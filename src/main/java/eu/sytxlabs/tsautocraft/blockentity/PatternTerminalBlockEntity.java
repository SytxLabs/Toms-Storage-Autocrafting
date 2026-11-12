package eu.sytxlabs.tsautocraft.blockentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.api.PatternMode;
import eu.sytxlabs.tsautocraft.api.PatternModes;
import eu.sytxlabs.tsautocraft.menu.PatternTerminalMenu;
import eu.sytxlabs.tsautocraft.pattern.BuiltinPatternModes;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternEncoder;
import eu.sytxlabs.tsautocraft.pattern.PatternItem;
import eu.sytxlabs.tsautocraft.util.CustomName;
import eu.sytxlabs.tsautocraft.util.GhostContainer;

/**
 * Pattern Terminal: a Tom's terminal that also encodes patterns ({@link PatternModes}). In crafting mode each grid
 * position is the crafter slot the provider fills later. The "Blank" slot takes a blank or an old pattern to overwrite.
 */
public class PatternTerminalBlockEntity extends StorageTerminalBlockEntity {
	private final CustomName customName = new CustomName();
	public static final int SLOT_BLANK = 0, SLOT_RESULT = 1;

	private final GhostContainer grid = new GhostContainer(9, this::onGridChanged);
	private final GhostContainer outputs = new GhostContainer(3, this::setChanged);
	private final SimpleContainer patternSlots = new SimpleContainer(2) {
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot == SLOT_BLANK && (stack.is(ModContent.BLANK_PATTERN) || stack.getItem() instanceof PatternItem);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			blankChanged();
			PatternTerminalBlockEntity.this.setChanged();
		}
	};
	/** Stack last seen in the top slot, to load an encoded pattern once when it is put in. */
	private ItemStack lastBlank = ItemStack.EMPTY;
	private PatternMode mode = PatternModes.CRAFTING;
	private boolean substitutes;
	private boolean loading;

	public PatternTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.PATTERN_TERMINAL_BE, pos, state);
	}

	public GhostContainer getGrid() {
		return grid;
	}

	public GhostContainer getOutputs() {
		return outputs;
	}

	public SimpleContainer getPatternSlots() {
		return patternSlots;
	}

	public PatternMode getMode() {
		return mode;
	}

	public boolean isFixedMode() {
		return mode.fixedInputs();
	}

	public boolean isSubstitutes() {
		return substitutes;
	}

	public void setSubstitutes(boolean substitutes) {
		this.substitutes = substitutes;
		setChanged();
	}

	public void cycleMode() {
		setMode(PatternModes.next(mode));
	}

	public void setMode(PatternMode mode) {
		this.mode = mode;
		for (int i = 0; i < 9; i++) {
			ItemStack st = grid.getItem(i);
			if (!mode.gridSlotEnabled(i)) grid.setItem(i, ItemStack.EMPTY); // position not used by this mode
			else if (isFixedMode() && st.getCount() > 1) grid.setItem(i, st.copyWithCount(1));
		}
		onGridChanged();
	}

	private void onGridChanged() {
		if (!loading && isFixedMode() && level != null && !level.isClientSide()) {
			List<ItemStack> result = mode.computeOutputs(level, gridItems());
			outputs.clearContent();
			for (int i = 0; i < 3 && i < result.size(); i++) outputs.setItem(i, result.get(i));
		}
		setChanged();
	}

	private List<ItemStack> gridItems() {
		List<ItemStack> l = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) l.add(grid.getItem(i));
		return l;
	}

	/** Encodes the current grid. Returns a translation key for an error, or null on success. */
	public String encode() {
		ItemStack blank = patternSlots.getItem(SLOT_BLANK);
		if (blank.isEmpty()) return "msg.tsautocraft.encode.no_blank";
		if (!patternSlots.getItem(SLOT_RESULT).isEmpty()) return "msg.tsautocraft.encode.result_full";

		List<ItemStack> outs = new ArrayList<>(3);
		for (int i = 0; i < 3; i++) outs.add(outputs.getItem(i));
		PatternEncoder.Result r = PatternEncoder.encode(level, mode, gridItems(), outs, substitutes);
		if (r.error() != null) return r.error();

		ItemStack pattern = r.toItem();
		if (blank.getItem() instanceof PatternItem) pattern.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, blank.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
		blank.shrink(1);
		patternSlots.setItem(SLOT_BLANK, blank.isEmpty() ? ItemStack.EMPTY : blank);
		patternSlots.setItem(SLOT_RESULT, pattern);
		return null;
	}

	/** An encoded pattern put into the top slot is loaded into the grid right away, so it can be edited and encoded again. */
	private void blankChanged() {
		ItemStack now = patternSlots.getItem(SLOT_BLANK);
		boolean added = !ItemStack.isSameItemSameComponents(now, lastBlank);
		lastBlank = now.copy();
		if (added && !loading && level != null && !level.isClientSide() && PatternItem.get(now) != null) loadFromPattern();
	}

	public boolean loadFromPattern() {
		PatternData d = PatternItem.get(patternSlots.getItem(SLOT_BLANK));
		if (d == null) d = PatternItem.get(patternSlots.getItem(SLOT_RESULT));
		if (d == null) return false;
		loading = true;
		try {
			mode = d.modeType();
			substitutes = d.hasSubstitutes();
			grid.clearContent();
			outputs.clearContent();
			if (mode.keepsGridPositions()) {
				for (int i = 0; i < 9 && i < d.inputs().size(); i++) grid.setItem(i, d.inputs().get(i).stack());
			} else {
				int i = 0;
				for (PatternData.Entry e : d.inputs()) if (i < 9 && !e.isEmpty()) grid.setItem(i++, e.stack());
			}
			int o = 0;
			for (PatternData.Entry e : d.outputs()) if (o < 3) outputs.setItem(o++, e.stack());
		} finally {
			loading = false;
		}
		onGridChanged();
		return true;
	}

	/** Recipe transfer from JEI/REI: crafting = 9 grid positions, other fixed modes = one per input, processing = up to 9 inputs and 3 outputs with amounts. */
	public void applyRecipe(PatternMode mode, List<ItemStack> in, List<ItemStack> out) {
		boolean fixed = mode.fixedInputs();
		if (!mode.keepsGridPositions()) in = BuiltinPatternModes.compact(in);
		loading = true;
		try {
			this.mode = mode;
			grid.clearContent();
			outputs.clearContent();
			for (int i = 0; i < 9 && i < in.size(); i++) {
				ItemStack s = in.get(i);
				if (s.isEmpty() || !mode.gridSlotEnabled(i)) continue;
				grid.setItem(i, s.copyWithCount(fixed ? 1 : Math.clamp(s.getCount(), 1, PatternTerminalMenu.MAX_PROCESSING_COUNT)));
			}
			if (!fixed) {
				for (int i = 0; i < 3 && i < out.size(); i++) {
					ItemStack s = out.get(i);
					if (!s.isEmpty()) outputs.setItem(i, s.copyWithCount(Math.clamp(s.getCount(), 1, PatternTerminalMenu.MAX_PROCESSING_COUNT)));
				}
			}
		} finally {
			loading = false;
		}
		onGridChanged();
	}

	/**
	 * Multiplies all grid and output amounts by {@code mul / div} (modes with free amounts only). Does nothing and returns
	 * false if a new amount would not be a whole number in 1..{@link PatternTerminalMenu#MAX_PROCESSING_COUNT}.
	 */
	public boolean scaleAmounts(int mul, int div) {
		if (isFixedMode()) return false;
		List<ItemStack> all = gridItems();
		for (int i = 0; i < 3; i++) all.add(outputs.getItem(i));
		if (!PatternTerminalMenu.canScale(all, mul, div)) return false;
		loading = true;
		try {
			for (int i = 0; i < 9; i++) scaleSlot(grid, i, mul, div);
			for (int i = 0; i < 3; i++) scaleSlot(outputs, i, mul, div);
		} finally {
			loading = false;
		}
		onGridChanged();
		return true;
	}

	private static void scaleSlot(GhostContainer c, int slot, int mul, int div) {
		ItemStack st = c.getItem(slot);
		if (!st.isEmpty()) c.setItem(slot, st.copyWithCount((int) ((long) st.getCount() * mul / div)));
	}

	public void clearGrid() {
		grid.clearContent();
		outputs.clearContent();
		onGridChanged();
	}

	@Override
	public void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		customName.save(out);
		grid.save(out, "Grid");
		outputs.save(out, "Outputs");
		var list = out.list("Slots", ItemStack.OPTIONAL_CODEC);
		list.add(patternSlots.getItem(0));
		list.add(patternSlots.getItem(1));
		out.putString("ModeId", mode.id().toString());
		out.putBoolean("Substitutes", substitutes);
	}

	@Override
	public void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		customName.load(in);
		loading = true;
		try {
			grid.load(in, "Grid");
			outputs.load(in, "Outputs");
			List<ItemStack> l = in.listOrEmpty("Slots", ItemStack.OPTIONAL_CODEC).stream().toList();
			patternSlots.setItem(0, !l.isEmpty() ? l.get(0) : ItemStack.EMPTY);
			patternSlots.setItem(1, l.size() > 1 ? l.get(1) : ItemStack.EMPTY);
			mode = in.getString("ModeId").map(Identifier::tryParse).map(PatternModes::get).orElse(in.getBooleanOr("Crafting", true) ? PatternModes.CRAFTING : PatternModes.PROCESSING);
			substitutes = in.getBooleanOr("Substitutes", false);
		} finally {
			loading = false;
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		Containers.dropContents(level, pos, patternSlots);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new PatternTerminalMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return customName.orElse(Component.translatable("menu.tsautocraft.pattern_terminal"));
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
}
