package eu.sytxlabs.tsautocraft.blockentity;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.tom.storagemod.block.entity.PaintedBlockEntity;
import com.tom.storagemod.inventory.IInventoryAccess;
import com.tom.storagemod.inventory.PlatformInventoryAccess;
import com.tom.storagemod.util.TickerUtil.TickableServer;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.api.CraftingProvider;
import eu.sytxlabs.tsautocraft.api.JobOwner;
import eu.sytxlabs.tsautocraft.block.CraftingEmitterBlock;
import eu.sytxlabs.tsautocraft.job.CraftingJob;
import eu.sytxlabs.tsautocraft.menu.CraftingEmitterMenu;
import eu.sytxlabs.tsautocraft.network.NetworkLink;
import eu.sytxlabs.tsautocraft.network.TerminalCrafting;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.util.CustomName;
import eu.sytxlabs.tsautocraft.util.GhostContainer;
import eu.sytxlabs.tsautocraft.util.ItemKey;
import eu.sytxlabs.tsautocraft.util.StorageUtil;

/**
 * Crafting Emitter: redstone signal depending on one item of the network.
 * <ul>
 *   <li>{@link #MODE_BELOW}: while the stock is below the amount.</li>
 *   <li>{@link #MODE_ABOVE}: while the stock is at least the amount.</li>
 *   <li>{@link #MODE_CRAFT}: the item counts as craftable (like an AE2 crafting card) and the signal is on while a job needs it, e.g. to switch a farm on. The items have to come back through an Importer (or storage).</li>
 * </ul>
 */
public class CraftingEmitterBlockEntity extends PaintedBlockEntity implements TickableServer, MenuProvider, CraftingProvider, StatusInfo, MemoryCardHost {
	private final CustomName customName = new CustomName();
	public static final int MODE_BELOW = 0, MODE_ABOVE = 1, MODE_CRAFT = 2, MODES = 3;
	public static final int MAX_AMOUNT = 1_000_000;
	private static final int INTERVAL = 10;

	private final GhostContainer item = new GhostContainer(1, this::onItemChanged);
	private int amount = 1;
	private int mode = MODE_BELOW;
	private PatternData pattern;
	private final NetworkLink network = new NetworkLink();

	public CraftingEmitterBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CRAFTING_EMITTER_BE, pos, state);
	}

	public GhostContainer getItem() {
		return item;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = Math.clamp(amount, 0, MAX_AMOUNT);
		setChanged();
	}

	public int getMode() {
		return mode;
	}

	public void cycleMode() {
		mode = (mode + 1) % MODES;
		pattern = null;
		eu.sytxlabs.tsautocraft.network.NetworkCache.patternsChanged();
		setChanged();
	}

	private void onItemChanged() {
		pattern = null;
		eu.sytxlabs.tsautocraft.network.NetworkCache.patternsChanged();
		setChanged();
	}

	/** Pattern without inputs: one craft = one item that the player's farm or machine produces on the signal. */
	private PatternData pattern() {
		ItemStack st = item.getItem(0);
		if (mode != MODE_CRAFT || st.isEmpty()) return null;
		if (pattern == null) pattern = new PatternData(PatternData.PROCESSING, List.of(), List.of(new PatternData.Entry(st, 1)));
		return pattern;
	}

	@Override
	public List<PatternData> getPatterns() {
		PatternData p = pattern();
		return p != null ? List.of(p) : List.of();
	}

	/** Every requested craft is "started" at once; the signal stays on until all of them came back. */
	@Override
	public Dispatch dispatch(PatternData p, IInventoryAccess net, ToLongFunction<ItemKey> available, long wanted) {
		if (!p.equals(pattern())) return Dispatch.fail(DispatchResult.NO_TARGET);
		return new Dispatch(DispatchResult.OK, wanted, Map.of());
	}

	@Override
	public void updateServer() {
		if (level == null || ((level.getGameTime() + Math.abs(worldPosition.hashCode())) % INTERVAL != 0)) return;
		setPowered(computePowered());
	}

	private boolean computePowered() {
		ItemStack st = item.getItem(0);
		if (st.isEmpty()) return false;
		if (mode == MODE_CRAFT) {
			PatternData p = pattern();
			for (CraftingJob j : eu.sytxlabs.tsautocraft.network.NetworkCache.of(level, worldPosition).jobs(level)) if (j.pendingFor(p) > 0) return true;
			return false;
		}
		IInventoryAccess net = network.access(level, worldPosition);
		if (net == PlatformInventoryAccess.EMPTY) return false;
		long count = StorageUtil.count(net, level, new ItemKey(st));
		return (mode == MODE_BELOW) == (count < amount);
	}

	private void setPowered(boolean powered) {
		BlockState st = getBlockState();
		if (st.getValue(CraftingEmitterBlock.POWERED) != powered && level != null) level.setBlock(worldPosition, st.setValue(CraftingEmitterBlock.POWERED, powered), 3);
	}

	@Override
	public void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		customName.save(out);
		item.save(out, "Item");
		out.putInt("Amount", amount);
		out.putInt("Mode", mode);
	}

	@Override
	public void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		customName.load(in);
		item.load(in, "Item");
		amount = in.getIntOr("Amount", 1);
		mode = Math.clamp(in.getIntOr("Mode", MODE_BELOW), 0, MODES - 1);
		pattern = null;
		eu.sytxlabs.tsautocraft.network.NetworkCache.patternsChanged();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new CraftingEmitterMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return customName.orElse(getBlockState().getBlock().getName());
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
		String m = mode == MODE_BELOW ? "below" : mode == MODE_ABOVE ? "above" : "craft";
		lines.accept(Component.translatable("button.tsautocraft.emitter." + m));
		ItemStack st = item.getItem(0);
		if (!st.isEmpty()) lines.accept(mode == MODE_CRAFT ? st.getHoverName() : Component.translatable("label.tsautocraft.item_count", amount, st.getHoverName()));
	}

	//region Memory Card (mode and amount; not the item)

	@Override
	public void saveCard(ValueOutput out) {
		out.putInt("Amount", amount);
		out.putInt("Mode", mode);
	}

	@Override
	public void loadCard(ValueInput in) {
		setAmount(in.getIntOr("Amount", amount));
		mode = Math.clamp(in.getIntOr("Mode", mode), 0, MODES - 1);
		onItemChanged();
	}
	//endregion
}
