package eu.sytxlabs.tsautocraft.blockentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.tom.storagemod.inventory.IInventoryAccess;
import com.tom.storagemod.platform.PlatformBlockEntity;
import com.tom.storagemod.util.TickerUtil.TickableServer;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.TsAutocraftConfig;
import eu.sytxlabs.tsautocraft.api.JobOwner;
import eu.sytxlabs.tsautocraft.api.Upgrades;
import eu.sytxlabs.tsautocraft.job.CraftingJob;
import eu.sytxlabs.tsautocraft.job.JobRunner;
import eu.sytxlabs.tsautocraft.job.JobScheduler;
import eu.sytxlabs.tsautocraft.job.JobText;
import eu.sytxlabs.tsautocraft.job.Reservations;
import eu.sytxlabs.tsautocraft.menu.LevelMaintainerMenu;
import eu.sytxlabs.tsautocraft.network.NetworkLink;
import eu.sytxlabs.tsautocraft.network.TerminalCrafting;
import eu.sytxlabs.tsautocraft.util.CustomName;
import eu.sytxlabs.tsautocraft.util.GhostContainer;
import eu.sytxlabs.tsautocraft.util.ItemKey;
import eu.sytxlabs.tsautocraft.util.StorageUtil;
import eu.sytxlabs.tsautocraft.util.UpgradeContainer;

/**
 * Level Maintainer (like ExtendedAE's): 12 entries of item, threshold and batch. When the network stock (minus what other
 * jobs reserved) of an entry drops below its threshold, a crafting job for the batch amount is started. One job per entry;
 * the next one only starts after it finished, so nothing is crafted twice while results are still inside machines.
 * <p>
 * Upgrades: Speed (halves the check interval each), Redstone (only maintain with / without a signal, or toggle on pulses;
 * running jobs always continue).
 */
public class LevelMaintainerBlockEntity extends PlatformBlockEntity implements TickableServer, MenuProvider, JobOwner, StatusInfo, Upgrades.UpgradeHost, MemoryCardHost {
	private final CustomName customName = new CustomName();
	public static final int ENTRIES = 12, UPGRADE_SLOTS = 2;
	public static final int MAX_THRESHOLD = 1_000_000;

	/** Status per entry (for display). */
	public static final int ST_OFF = 0, ST_STOCKED = 1, ST_CRAFTING = 2, ST_MISSING = 3, ST_NO_PATTERN = 4, ST_NO_NETWORK = 5, ST_REDSTONE = 6;

	private final GhostContainer items = new GhostContainer(ENTRIES, this::setChanged);
	private final int[] thresholds = new int[ENTRIES];
	private final int[] batches = new int[ENTRIES];
	private final CraftingJob[] jobs = new CraftingJob[ENTRIES];
	private final int[] cooldown = new int[ENTRIES];
	/** Failed plans in a row per entry: the cooldown doubles each time (up to 8x), see {@link #failPlan}. */
	private final int[] planFails = new int[ENTRIES];
	private final int[] status = new int[ENTRIES];
	private final long[] stock = new long[ENTRIES];
	private final ItemStack[] statusItem = new ItemStack[ENTRIES];
	private final UpgradeContainer upgrades = new UpgradeContainer(UPGRADE_SLOTS, Upgrades.LEVEL_MAINTAINER, this::setChanged);
	private final NetworkLink network = new NetworkLink();
	private final JobRunner runner = new JobRunner();
	private int nextId = 1;

	private record SlotJob(int slot, CraftingJob job) {
		static final Codec<SlotJob> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("slot").forGetter(SlotJob::slot), CraftingJob.CODEC.fieldOf("job").forGetter(SlotJob::job)).apply(i, SlotJob::new));
	}

	public LevelMaintainerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.LEVEL_MAINTAINER_BE, pos, state);
		Arrays.fill(statusItem, ItemStack.EMPTY);
	}

	public GhostContainer getItems() {
		return items;
	}

	public UpgradeContainer getUpgrades() {
		return upgrades;
	}

	@Override
	public Identifier upgradeHost() {
		return Upgrades.LEVEL_MAINTAINER;
	}

	@Override
	public int upgradeCount(Item item) {
		return upgrades.count(item);
	}

	public int getThreshold(int i) {
		return thresholds[i];
	}

	public int getBatch(int i) {
		return batches[i];
	}

	public void setThreshold(int i, int amount) {
		if (i < 0 || i >= ENTRIES) return;
		thresholds[i] = Math.clamp(amount, 0, MAX_THRESHOLD);
		cooldown[i] = planFails[i] = 0;
		setChanged();
	}

	/** Batch amount, at most {@code maxRequestAmount} from the config. */
	public void setBatch(int i, int amount) {
		if (i < 0 || i >= ENTRIES) return;
		batches[i] = Math.clamp(amount, 0, TsAutocraftConfig.get().maxRequestAmount);
		cooldown[i] = planFails[i] = 0;
		setChanged();
	}

	public int getStatus(int i) {
		return status[i];
	}

	public long getStock(int i) {
		return stock[i];
	}

	public ItemStack getStatusItem(int i) {
		return statusItem[i];
	}

	private boolean active(int i) {
		return !items.getItem(i).isEmpty() && thresholds[i] > 0 && batches[i] > 0;
	}

	/** Ticks between stock checks (config {@code maintainerCheckTicks}, halved per Speed Upgrade). */
	private int interval() {
		return Math.max(1, TsAutocraftConfig.get().maintainerCheckTicks >> Math.min(8, upgrades.count(ModContent.SPEED_UPGRADE)));
	}

	@Override
	public void updateServer() {
		if (level == null) return;
		// Redstone Upgrade: checked every tick so short pulses are not missed
		boolean allowed = redstoneAllows();
		boolean runJobs = JobRunner.due(level);
		boolean check = (level.getGameTime() + Math.abs(worldPosition.hashCode())) % interval() == 0;
		if (!runJobs && !check) return;

		IInventoryAccess net = check ? network.access(level, worldPosition) : null;
		var providers = check ? network.providers(level, worldPosition) : null;
		boolean connected = check && network.isConnected(level, worldPosition);
		Reservations res = check ? TerminalCrafting.reservations(level, worldPosition) : null;

		for (int i = 0; i < ENTRIES; i++) {
			ItemStack item = items.getItem(i);
			CraftingJob job = jobs[i];

			// Entry emptied or another item: the job is no longer wanted
			if (job != null && (item.isEmpty() || !ItemStack.isSameItemSameComponents(item, job.target))) {
				job.cancel();
				jobs[i] = null;
				job = null;
				setChanged();
			}

			if (job != null && runJobs) {
				if (job.state() == CraftingJob.State.RUNNING && !inFlight(job) && job.stalledTicks() > TsAutocraftConfig.get().maintainerReplanSeconds * 20
						&& (job.reason() == CraftingJob.Reason.WAITING_ITEMS || job.reason() == CraftingJob.Reason.NO_PROVIDER)) {
					job.cancel();
				}
				if (job.isFinished()) {
					jobs[i] = null;
					cooldown[i] = 2;
					setChanged();
				}
			}
			if (!check) continue;

			if (jobs[i] != null) {
				setStatus(i, ST_CRAFTING, jobs[i].reasonItem());
				if (net != null) stock[i] = StorageUtil.count(net, level, new ItemKey(item));
				continue;
			}
			if (!active(i)) {
				setStatus(i, ST_OFF, ItemStack.EMPTY);
				stock[i] = 0;
				continue;
			}
			if (!connected) {
				setStatus(i, ST_NO_NETWORK, ItemStack.EMPTY);
				stock[i] = 0;
				continue;
			}
			ItemKey key = new ItemKey(item);
			if (net == null) continue;
			long have = Math.max(0, StorageUtil.count(net, level, key) - res.total(key));
			stock[i] = have;
			if (have >= thresholds[i]) {
				setStatus(i, ST_STOCKED, ItemStack.EMPTY);
				continue;
			}
			if (!allowed) {
				setStatus(i, ST_REDSTONE, ItemStack.EMPTY);
				continue;
			}
			if (cooldown[i] > 0) {
				cooldown[i]--;
				continue;
			}

			long amount = Math.min(batches[i], TsAutocraftConfig.get().maxRequestAmount);
			var outcome = CraftingJob.plan(nextId, level, net, providers, item, amount, res);
			if (outcome.ok()) {
				nextId++;
				jobs[i] = outcome.job();
				planFails[i] = 0;
				JobScheduler.started(this, jobs[i]);
				setStatus(i, ST_CRAFTING, ItemStack.EMPTY);
				setChanged();
			} else {
				if (outcome.missing().containsKey(key)) {
					setStatus(i, ST_NO_PATTERN, ItemStack.EMPTY);
				} else {
					ItemKey first = outcome.missing().keySet().iterator().next();
					setStatus(i, ST_MISSING, first.create(1));
				}
				failPlan(i);
			}
		}
		if (runJobs && !activeJobs().isEmpty()) JobScheduler.submit(this);
	}

	private void failPlan(int i) {
		cooldown[i] = 5 << Math.min(planFails[i], 3);
		if (planFails[i] < 3) planFails[i]++;
	}

	/** Crafts of the job are still inside machines (cancelling now would lose track of them). */
	private static boolean inFlight(CraftingJob job) {
		for (CraftingJob.Step s : job.steps()) if (s.inFlight > 0) return true;
		return false;
	}

	@Override
	public void tickJob(CraftingJob job) {
		runner.tick(this, network, job);
	}

	@Override
	public void jobsChanged() {
		setChanged();
	}

	@Override
	public List<CraftingJob> activeJobs() {
		List<CraftingJob> out = new ArrayList<>();
		for (CraftingJob j : jobs) if (j != null && !j.isFinished()) out.add(j);
		return out;
	}

	/** Cancel from the Autocrafting Terminal; the entry plans again after its cooldown if stock is still low. */
	@Override
	public void cancelJob(int uid) {
		for (int i = 0; i < ENTRIES; i++) {
			if (jobs[i] != null && jobs[i].uid == uid) {
				jobs[i].cancel();
				jobs[i] = null;
				cooldown[i] = 5;
				setChanged();
			}
		}
	}

	private void setStatus(int i, int st, ItemStack item) {
		status[i] = st;
		statusItem[i] = item;
	}

	//region Redstone Upgrade

	/** With a Redstone Upgrade: maintain only while powered, only while not powered, or toggle on/off with every pulse (same as the Importer/Exporter). */
	private int redstoneMode = AbstractIOBlockEntity.RS_HIGH;
	private boolean toggleOn, lastPowered;

	public boolean hasRedstoneUpgrade() {
		return upgrades.count(ModContent.REDSTONE_UPGRADE) > 0;
	}

	public int getRedstoneMode() {
		return redstoneMode;
	}

	public void cycleRedstoneMode() {
		redstoneMode = (redstoneMode + 1) % AbstractIOBlockEntity.RS_MODES;
		setChanged();
	}

	private boolean redstoneAllows() {
		if (!hasRedstoneUpgrade()) return true;
		if (level == null) return false;
		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered && !lastPowered) {
			toggleOn = !toggleOn;
			setChanged();
		}
		lastPowered = powered;
		return switch (redstoneMode) {
		case AbstractIOBlockEntity.RS_LOW -> !powered;
		case AbstractIOBlockEntity.RS_TOGGLE -> toggleOn;
		default -> powered;
		};
	}
	//endregion

	@Override
	public void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		customName.save(out);
		items.save(out, "Items");
		var th = out.list("Thresholds", Codec.INT);
		for (int a : thresholds) th.add(a);
		var ba = out.list("Batches", Codec.INT);
		for (int a : batches) ba.add(a);
		var js = out.list("Jobs", SlotJob.CODEC);
		for (int i = 0; i < ENTRIES; i++) if (jobs[i] != null && !jobs[i].isFinished()) js.add(new SlotJob(i, jobs[i]));
		out.putInt("NextId", nextId);
		out.putInt("RedstoneMode", redstoneMode);
		out.putBoolean("RedstoneOn", toggleOn);
		out.putBoolean("RedstoneLast", lastPowered);
		upgrades.save(out, "Upgrades");
	}

	@Override
	public void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		customName.load(in);
		items.load(in, "Items");
		List<Integer> th = in.listOrEmpty("Thresholds", Codec.INT).stream().toList();
		List<Integer> ba = in.listOrEmpty("Batches", Codec.INT).stream().toList();
		for (int i = 0; i < ENTRIES; i++) {
			thresholds[i] = i < th.size() ? th.get(i) : 0;
			batches[i] = i < ba.size() ? ba.get(i) : 0;
		}
		Arrays.fill(jobs, null);
		in.listOrEmpty("Jobs", SlotJob.CODEC).stream().forEach(sj -> {
			if (sj.slot() >= 0 && sj.slot() < ENTRIES) jobs[sj.slot()] = sj.job();
		});
		nextId = in.getIntOr("NextId", 1);
		redstoneMode = Math.clamp(in.getIntOr("RedstoneMode", AbstractIOBlockEntity.RS_HIGH), 0, AbstractIOBlockEntity.RS_MODES - 1);
		toggleOn = in.getBooleanOr("RedstoneOn", false);
		lastPowered = in.getBooleanOr("RedstoneLast", false);
		upgrades.load(in, "Upgrades");
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) Containers.dropContents(level, pos, upgrades);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new LevelMaintainerMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return customName.orElse(Component.translatable("block.tsautocraft.level_maintainer"));
	}

	public boolean stillValid(Player player) {
		if (level == null || level.getBlockEntity(worldPosition) != this) return false;
		return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
	}

	//region Anvil name

	/** Anvil name of the placed item, null without one. */
	public Component customName() {
		return customName.get();
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		customName.apply(components);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		customName.collect(builder);
	}
	//endregion

	@Override
	public void appendStatus(Consumer<Component> lines) {
		int used = 0;
		for (int i = 0; i < ENTRIES; i++) if (active(i)) used++;
		lines.accept(Component.translatable("jade.tsautocraft.entries", used, ENTRIES));
		if (hasRedstoneUpgrade() && level != null && !redstoneAllowsPeek()) lines.accept(Component.translatable("jade.tsautocraft.maintainer.redstone_off"));
		for (CraftingJob j : activeJobs()) lines.accept(JobText.line(j.view(true)));
	}

	/** Redstone state without the toggle side effect (for Jade). */
	private boolean redstoneAllowsPeek() {
		if (level == null) return false;
		boolean powered = level.hasNeighborSignal(worldPosition);
		return switch (redstoneMode) {
			case AbstractIOBlockEntity.RS_LOW -> !powered;
			case AbstractIOBlockEntity.RS_TOGGLE -> toggleOn;
			default -> powered;
		};
	}

	//region Memory Card (entries are ghosts)

	@Override
	public void saveCard(ValueOutput out) {
		items.save(out, "Items");
		var th = out.list("Thresholds", Codec.INT);
		for (int a : thresholds) th.add(a);
		var ba = out.list("Batches", Codec.INT);
		for (int a : batches) ba.add(a);
		out.putInt("RedstoneMode", redstoneMode);
	}

	@Override
	public void loadCard(ValueInput in) {
		items.load(in, "Items");
		List<Integer> th = in.listOrEmpty("Thresholds", Codec.INT).stream().toList();
		List<Integer> ba = in.listOrEmpty("Batches", Codec.INT).stream().toList();
		for (int i = 0; i < ENTRIES; i++) {
			setThreshold(i, i < th.size() ? th.get(i) : 0);
			setBatch(i, i < ba.size() ? ba.get(i) : 0);
		}
		redstoneMode = Math.clamp(in.getIntOr("RedstoneMode", redstoneMode), 0, AbstractIOBlockEntity.RS_MODES - 1);
		toggleOn = false;
		setChanged();
	}

	@Override
	public UpgradeContainer cardUpgrades() {
		return upgrades;
	}
	//endregion
}
