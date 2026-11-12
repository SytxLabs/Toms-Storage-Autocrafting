package eu.sytxlabs.tsautocraft.blockentity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.tom.storagemod.block.entity.PaintedBlockEntity;
import com.tom.storagemod.inventory.StoredItemStack;
import com.tom.storagemod.inventory.filter.ItemPredicate;
import com.tom.storagemod.item.IItemFilter;
import com.tom.storagemod.util.BlockFaceReference;
import com.tom.storagemod.util.TickerUtil.TickableServer;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.TsAutocraftConfig;
import eu.sytxlabs.tsautocraft.api.IOMode;
import eu.sytxlabs.tsautocraft.api.IOModes;
import eu.sytxlabs.tsautocraft.api.Upgrades;
import eu.sytxlabs.tsautocraft.block.DirectionalNodeBlock;
import eu.sytxlabs.tsautocraft.network.NetworkLink;
import eu.sytxlabs.tsautocraft.util.CustomName;
import eu.sytxlabs.tsautocraft.util.UpgradeContainer;

/**
 * Shared base for Exporter and Importer: 9 filter slots (ghost = exact item, nothing used up, or a real Tom's filter
 * item). What fits nowhere waits in {@link #buffer} and is retried, never dropped.
 */
public abstract class AbstractIOBlockEntity extends PaintedBlockEntity implements TickableServer, MenuProvider, Upgrades.UpgradeHost, MemoryCardHost {
	protected final CustomName customName = new CustomName();
	public static final int SLOTS = 9, UPGRADE_SLOTS = 3;

	protected final UpgradeContainer upgrades = new UpgradeContainer(UPGRADE_SLOTS, upgradeHost(), this::setChanged);

	@Override
	public int upgradeCount(net.minecraft.world.item.Item item) {
		return upgrades.count(item);
	}

	public UpgradeContainer getUpgrades() {
		return upgrades;
	}

	/** Maximum items moved per operation (config {@code ioItemsPerOperation}, times 4 per Stack Upgrade). */
	protected int maxPerOp() {
		return (int) Math.min(64, (long) TsAutocraftConfig.get().ioItemsPerOperation << (2 * Math.min(3, upgrades.count(ModContent.STACK_UPGRADE))));
	}

	/** Ticks between operations (config {@code ioIntervalTicks}, halved per Speed Upgrade). */
	protected int interval() {
		return Math.max(1, TsAutocraftConfig.get().ioIntervalTicks >> Math.min(8, upgrades.count(ModContent.SPEED_UPGRADE)));
	}

	/** One per slot: ghosts of plain items or real Tom's filter items. */
	protected final SimpleContainer filters = new SimpleContainer(SLOTS) {
		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			predicatesDirty = true;
			wake();
			AbstractIOBlockEntity.this.setChanged();
		}
	};
	private final ItemPredicate[] predicates = new ItemPredicate[SLOTS];
	private boolean predicatesDirty = true;

	protected final NetworkLink network = new NetworkLink();
	protected ItemStack buffer = ItemStack.EMPTY;
	protected final eu.sytxlabs.tsautocraft.util.Throughput throughput = new eu.sytxlabs.tsautocraft.util.Throughput();
	/** Number the {@link IOMode} keeps (round robin position). */
	protected int cursor;
	private IOMode mode;
	/** What the active mode sees of this block. */
	protected final IOMode.Context modeContext = new IOMode.Context() {
		@Override
		public net.minecraft.world.level.Level level() {
			return level;
		}

		@Override
		public BlockPos pos() {
			return worldPosition;
		}

		@Override
		public net.minecraft.util.RandomSource random() {
			return level != null ? level.getRandom() : net.minecraft.util.RandomSource.create();
		}

		@Override
		public int cursor() {
			return cursor;
		}

		@Override
		public void setCursor(int c) {
			cursor = c;
		}
	};
	protected int backoff;

	protected AbstractIOBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public SimpleContainer getFilters() {
		return filters;
	}

	protected abstract IOMode defaultMode();

	public IOMode getMode() {
		return mode != null ? mode : defaultMode();
	}

	/** Next mode this block supports. */
	public void cycleMode() {
		mode = IOModes.next(getMode(), upgradeHost());
		cursor = 0;
		wake();
		setChanged();
	}

	protected Direction facing() {
		return getBlockState().getValue(DirectionalNodeBlock.FACING);
	}

	@Override
	public void updateServer() {
		if (level == null) return;
		// Redstone Upgrade: checked every tick so short pulses are not missed
		if (!redstoneAllows()) return;
		if ((level.getGameTime() + Math.abs(worldPosition.hashCode())) % interval() != 0) return;
		if (backoff > 0) {
			backoff--;
			return;
		}
		if (predicatesDirty) rebuildPredicates();
		if (work()) {
			idle = 0;
		} else {
			idle = Math.min(idle + 1, TsAutocraftConfig.get().ioIdleMaxSkip);
			backoff = Math.max(backoff, idle);
		}
	}

	/** One operation; true if something was moved or is waiting in the buffer (no idle skipping then). */
	protected abstract boolean work();

	/** Idle operations in a row, see {@link #updateServer()}. */
	private int idle;

	/** Something changed (filters, menu, redstone): work again at the next operation. */
	protected void wake() {
		idle = 0;
		backoff = 0;
	}

	//region Redstone Upgrade

	/** With a Redstone Upgrade: work only while powered, only while not powered, or toggle on/off with every pulse. */
	public static final int RS_HIGH = 0, RS_LOW = 1, RS_TOGGLE = 2, RS_MODES = 3;
	private int redstoneMode = RS_HIGH;
	private boolean toggleOn, lastPowered;

	public boolean hasRedstoneUpgrade() {
		return upgrades.count(ModContent.REDSTONE_UPGRADE) > 0;
	}

	public int getRedstoneMode() {
		return redstoneMode;
	}

	public void cycleRedstoneMode() {
		redstoneMode = (redstoneMode + 1) % RS_MODES;
		setChanged();
	}

	private boolean redstoneAllows() {
		if (!hasRedstoneUpgrade()) return true;
		if (level == null) return false;
		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered != lastPowered) wake();
		if (powered && !lastPowered) {
			toggleOn = !toggleOn;
			setChanged();
		}
		lastPowered = powered;
		return switch (redstoneMode) {
		case RS_LOW -> !powered;
		case RS_TOGGLE -> toggleOn;
		default -> powered;
		};
	}
	//endregion

	private void rebuildPredicates() {
		predicatesDirty = false;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack st = filters.getItem(i);
			if (st.isEmpty()) {
				predicates[i] = null;
			} else if (st.getItem() instanceof IItemFilter f) {
				predicates[i] = f.createFilter(BlockFaceReference.touching(level, worldPosition, facing()), st);
			} else {
				ItemStack copy = st.copyWithCount(1);
				predicates[i] = s -> ItemStack.isSameItemSameComponents(s.getStack(), copy);
			}
		}
	}

	protected ItemPredicate predicate(int i) {
		return predicates[i];
	}

	protected boolean hasAnyFilter() {
		for (ItemPredicate p : predicates) if (p != null) return true;
		return false;
	}

	protected boolean matchesAny(ItemStack stack) {
		StoredItemStack s = new StoredItemStack(stack);
		for (ItemPredicate p : predicates) {
			if (p == null) continue;
			p.updateState();
			if (p.test(s)) return true;
		}
		return false;
	}

	@Override
	public void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		customName.save(out);
		var list = out.list("Filters", ItemStack.OPTIONAL_CODEC);
		for (int i = 0; i < SLOTS; i++) list.add(filters.getItem(i));
		if (!buffer.isEmpty()) out.store("Buffer", ItemStack.CODEC, buffer);
		out.putString("Mode", getMode().id().toString());
		out.putInt("Cursor", cursor);
		out.putInt("RedstoneMode", redstoneMode);
		out.putBoolean("RedstoneOn", toggleOn);
		out.putBoolean("RedstoneLast", lastPowered);
		upgrades.save(out, "Upgrades");
	}

	@Override
	public void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		customName.load(in);
		List<ItemStack> list = in.listOrEmpty("Filters", ItemStack.OPTIONAL_CODEC).stream().toList();
		for (int i = 0; i < SLOTS; i++) filters.setItem(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
		buffer = in.read("Buffer", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		mode = in.getString("Mode").map(net.minecraft.resources.Identifier::tryParse).map(id -> IOModes.get(id, upgradeHost(), defaultMode())).orElse(null);
		cursor = in.getIntOr("Cursor", 0);
		redstoneMode = Math.clamp(in.getIntOr("RedstoneMode", RS_HIGH), 0, RS_MODES - 1);
		toggleOn = in.getBooleanOr("RedstoneOn", false);
		lastPowered = in.getBooleanOr("RedstoneLast", false);
		upgrades.load(in, "Upgrades");
		predicatesDirty = true;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level == null) return;
		// Only Tom's filter items are real, plain items are ghosts
		for (int i = 0; i < SLOTS; i++) {
			ItemStack st = filters.getItem(i);
			if (st.getItem() instanceof IItemFilter) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), st);
		}
		if (!buffer.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), buffer);
		Containers.dropContents(level, pos, upgrades);
		buffer = ItemStack.EMPTY;
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

	//region Memory Card

	@Override
	public void saveCard(ValueOutput out) {
		out.putString("Mode", getMode().id().toString());
		out.putInt("RedstoneMode", redstoneMode);
	}

	@Override
	public void loadCard(ValueInput in) {
		mode = in.getString("Mode").map(net.minecraft.resources.Identifier::tryParse).map(id -> IOModes.get(id, upgradeHost(), defaultMode())).orElse(mode);
		cursor = 0;
		redstoneMode = Math.clamp(in.getIntOr("RedstoneMode", redstoneMode), 0, RS_MODES - 1);
		toggleOn = false;
		predicatesDirty = true;
		wake();
		setChanged();
	}

	@Override
	public net.minecraft.world.Container cardFilters() {
		return filters;
	}

	@Override
	public UpgradeContainer cardUpgrades() {
		return upgrades;
	}
	//endregion
}
