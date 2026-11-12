package eu.sytxlabs.tsautocraft.blockentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import eu.sytxlabs.tsautocraft.job.JobHistoryEntry;
import eu.sytxlabs.tsautocraft.job.JobRunner;
import eu.sytxlabs.tsautocraft.job.JobScheduler;
import eu.sytxlabs.tsautocraft.job.JobText;
import eu.sytxlabs.tsautocraft.job.Reservations;
import eu.sytxlabs.tsautocraft.menu.AutocraftingTerminalMenu;
import eu.sytxlabs.tsautocraft.menu.CraftRequestProtocol;
import eu.sytxlabs.tsautocraft.network.NetworkLink;
import eu.sytxlabs.tsautocraft.network.TerminalCrafting;
import eu.sytxlabs.tsautocraft.util.CustomName;
import eu.sytxlabs.tsautocraft.util.ItemKey;
import eu.sytxlabs.tsautocraft.util.StorageUtil;
import eu.sytxlabs.tsautocraft.util.TerminalAccess;
import eu.sytxlabs.tsautocraft.util.UpgradeContainer;

/**
 * Autocrafting Terminal: lists craftable items, accepts requests and runs the jobs.
 * Capacity Upgrades raise the number of parallel jobs (config {@code jobsPerCapacityUpgrade} each).
 */
public class AutocraftingTerminalBlockEntity extends PlatformBlockEntity implements TickableServer, MenuProvider, JobOwner, Upgrades.UpgradeHost, StatusInfo, CraftRequestProtocol.Target {
	private final CustomName customName = new CustomName();
	public static final int UPGRADE_SLOTS = 3;

	/** Largest amount per request (config {@code maxRequestAmount}). */
	public static long maxRequest() {
		return TsAutocraftConfig.get().maxRequestAmount;
	}

	private final NetworkLink network = new NetworkLink();
	private final JobRunner runner = new JobRunner();
	private final List<CraftingJob> jobs = new ArrayList<>();
	private final UpgradeContainer upgrades = new UpgradeContainer(UPGRADE_SLOTS, Upgrades.AUTOCRAFTING_TERMINAL, this::setChanged);
	private int nextId = 1;
	/** Finished jobs of the network, newest first (config {@code jobHistorySize}). */
	private final List<JobHistoryEntry> history = new ArrayList<>();
	/** Running jobs of other owners on the network, watched until they finish (uid -> job). */
	private final Map<Integer, Watched> watched = new HashMap<>();
	private static final int HISTORY_SCAN_TICKS = 20;

	private record Watched(CraftingJob job, boolean automatic) {}

	public AutocraftingTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.AUTOCRAFTING_TERMINAL_BE, pos, state);
	}

	public UpgradeContainer getUpgrades() {
		return upgrades;
	}

	@Override
	public Identifier upgradeHost() {
		return Upgrades.AUTOCRAFTING_TERMINAL;
	}

	@Override
	public int upgradeCount(net.minecraft.world.item.Item item) {
		return upgrades.count(item);
	}

	public int maxJobs() {
		TsAutocraftConfig cfg = TsAutocraftConfig.get();
		return cfg.maxJobsPerTerminal + upgrades.count(ModContent.CAPACITY_UPGRADE) * cfg.jobsPerCapacityUpgrade;
	}

	@Override
	public void updateServer() {
		if (level == null) return;
		for (CraftingJob j : jobs) if (j.isFinished()) addHistory(j, false);
		if (jobs.removeIf(CraftingJob::isFinished)) setChanged();
		if (!jobs.isEmpty() && JobRunner.due(level)) JobScheduler.submit(this);
		if (level.getGameTime() % HISTORY_SCAN_TICKS == 0) scanOtherJobs();
	}

	//region History

	/** Records finished jobs of the other job owners on the network (several terminals on one network each keep the network's history). */
	private void scanOtherJobs() {
		if (TsAutocraftConfig.get().jobHistorySize <= 0) {
			watched.clear();
			return;
		}
		watched.values().removeIf(w -> {
			if (!w.job().isFinished()) return false;
			addHistory(w.job(), w.automatic());
			return true;
		});
		Set<Integer> alive = new HashSet<>();
		for (JobOwner o : TerminalCrafting.jobOwners(level, worldPosition)) {
			if (o == this) continue;
			for (CraftingJob j : o.activeJobs()) {
				alive.add(j.uid);
				watched.putIfAbsent(j.uid, new Watched(j, o.automatic()));
			}
		}
		watched.keySet().retainAll(alive);
	}

	private void addHistory(CraftingJob job, boolean automatic) {
		int max = TsAutocraftConfig.get().jobHistorySize;
		if (max <= 0 || level == null) return;
		history.addFirst(JobHistoryEntry.of(job, level.getGameTime(), automatic));
		while (history.size() > max) history.removeLast();
		setChanged();
	}

	/** Finished jobs, newest first. */
	public List<JobHistoryEntry> history() {
		return history;
	}

	public void clearHistory() {
		history.clear();
		setChanged();
	}
	//endregion

	@Override
	public void tickJob(CraftingJob job) {
		runner.tick(this, network, job);
	}

	@Override
	public void jobsChanged() {
		setChanged();
	}

	@Override
	public RequestResult request(ItemStack item, long amount) {
		if (item.isEmpty() || amount <= 0) return RequestResult.error("msg.tsautocraft.request.invalid");
		if (jobs.stream().filter(j -> !j.isFinished()).count() >= maxJobs()) return RequestResult.error("msg.tsautocraft.request.too_many");
		IInventoryAccess net = network.access(level, worldPosition);
		if (!network.isConnected(level, worldPosition)) return RequestResult.error("msg.tsautocraft.no_network");

		var outcome = CraftingJob.plan(nextId, level, net, network.providers(level, worldPosition), item, Math.min(amount, maxRequest()), TerminalCrafting.reservations(level, worldPosition));
		if (!outcome.ok()) return new RequestResult(null, outcome.missing());
		nextId++;
		jobs.add(outcome.job());
		JobScheduler.started(this, outcome.job());
		setChanged();
		return new RequestResult(null, Map.of());
	}

	/** Plans without starting: material list for the request preview, plus the largest possible amount if it fails. */
	@Override
	public CraftingJob.PlanOutcome preview(ItemStack item, long amount) {
		IInventoryAccess net = network.access(level, worldPosition);
		var providers = network.providers(level, worldPosition);
		Reservations res = TerminalCrafting.reservations(level, worldPosition);
		long a = Math.min(amount, maxRequest());
		var outcome = CraftingJob.plan(0, level, net, providers, item, a, res);
		if (outcome.ok()) return outcome;
		return outcome.withPossible(CraftingJob.maxPossible(level, net, providers, item, a, res));
	}

	/** Cancels a job of this terminal or of any other job owner on the network (by {@link CraftingJob#uid}). */
	public void cancel(int uid) {
		for (JobOwner o : TerminalCrafting.jobOwners(level, worldPosition)) o.cancelJob(uid);
		cancelJob(uid);
		setChanged();
	}

	/** Pauses/resumes a job of any owner on the network. */
	public void togglePause(int uid) {
		for (JobOwner o : ownersWithSelf()) {
			CraftingJob j = o.findJob(uid);
			if (j != null) {
				j.setPaused(!j.paused());
				o.jobsChanged();
			}
		}
	}

	/** Cycles the priority (normal -> high -> low -> normal) of a job of any owner on the network. */
	public void cyclePriority(int uid) {
		for (JobOwner o : ownersWithSelf()) {
			CraftingJob j = o.findJob(uid);
			if (j != null) {
				j.setPriority(j.priority() == CraftingJob.PRIORITY_NORMAL ? CraftingJob.PRIORITY_HIGH : j.priority() == CraftingJob.PRIORITY_HIGH ? CraftingJob.PRIORITY_LOW : CraftingJob.PRIORITY_NORMAL);
				o.jobsChanged();
			}
		}
	}

	private List<JobOwner> ownersWithSelf() {
		List<JobOwner> l = new ArrayList<>(TerminalCrafting.jobOwners(level, worldPosition));
		if (!l.contains(this)) l.add(this);
		return l;
	}

	@Override
	public void cancelJob(int uid) {
		for (CraftingJob j : jobs) if (j.uid == uid) j.cancel();
	}

	@Override
	public List<CraftingJob> activeJobs() {
		List<CraftingJob> out = new ArrayList<>();
		for (CraftingJob j : jobs) if (!j.isFinished()) out.add(j);
		return out;
	}

	@Override
	public boolean automatic() {
		return false;
	}

	/** Own jobs followed by the running jobs of all other job owners (Requesters, Exporters, other terminals). */
	public List<CraftingJob.JobView> jobViews() {
		List<CraftingJob.JobView> views = new ArrayList<>();
		for (CraftingJob j : jobs) views.add(j.view(false));
		for (JobOwner o : TerminalCrafting.jobOwners(level, worldPosition)) {
			if (o == this) continue;
			for (CraftingJob j : o.activeJobs()) views.add(j.view(o.automatic()));
		}
		return views;
	}

	public List<ItemStack> craftables() {
		return TerminalCrafting.craftables(level, worldPosition);
	}

	public long stockOf(ItemStack st) {
		IInventoryAccess net = network.access(level, worldPosition);
		return StorageUtil.count(net, level, new ItemKey(st));
	}

	public record RequestResult(String error, Map<ItemKey, Long> missing) {
		public static RequestResult error(String key) {
			return new RequestResult(key, Map.of());
		}

		public boolean ok() {
			return error == null && missing.isEmpty();
		}
	}

	@Override
	public void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		customName.save(out);
		var list = out.list("Jobs", CraftingJob.CODEC);
		for (CraftingJob j : jobs) if (!j.isFinished()) list.add(j);
		out.putInt("NextId", nextId);
		upgrades.save(out, "Upgrades");
		var hist = out.list("History", JobHistoryEntry.CODEC);
		history.forEach(hist::add);
	}

	@Override
	public void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		customName.load(in);
		jobs.clear();
		in.listOrEmpty("Jobs", CraftingJob.CODEC).stream().forEach(jobs::add);
		nextId = in.getIntOr("NextId", 1);
		upgrades.load(in, "Upgrades");
		history.clear();
		in.listOrEmpty("History", JobHistoryEntry.CODEC).stream().forEach(history::add);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level == null) return;
		Containers.dropContents(level, pos, upgrades);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new AutocraftingTerminalMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return customName.orElse(Component.translatable("block.tsautocraft.autocrafting_terminal"));
	}

	public boolean stillValid(Player player) {
		return TerminalAccess.stillValid(this, player);
	}

	//region Anvil name

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
		List<CraftingJob> active = activeJobs();
		lines.accept(Component.translatable("jade.tsautocraft.jobs", active.size(), maxJobs()));
		for (int i = 0; i < active.size() && i < 3; i++) lines.accept(JobText.line(active.get(i).view(false)));
		if (active.size() > 3) lines.accept(Component.translatable("tooltip.tsautocraft.pattern.more", active.size() - 3));
		if (!network.isConnected(level, worldPosition)) lines.accept(Component.translatable("reason.tsautocraft.no_network"));
	}
}
