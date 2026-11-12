package eu.sytxlabs.tsautocraft.menu;

//? if >=26.3 {
import net.minecraft.util.Prediction;
//?}
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.tom.storagemod.item.IItemFilter;
import com.tom.storagemod.menu.AbstractFilteredMenu;
import com.tom.storagemod.menu.slot.FilterSlot;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.api.IOMode;
import eu.sytxlabs.tsautocraft.api.IOModes;
import eu.sytxlabs.tsautocraft.api.Upgrades;
import eu.sytxlabs.tsautocraft.blockentity.AbstractIOBlockEntity;
import eu.sytxlabs.tsautocraft.util.UpgradeContainer;

/**
 * Menu for Exporter and Importer: 3x3 filter slots in dispenser layout, upgrade slots on the right (slot order: 0-8 filters, 9-44 player inventory, 45-47 upgrades).
 * Filter slots: plain items are ghosts (a copy, nothing is taken), Tom's filter items (item/tag/poly filter) are real items that
 * go in and come out again. JEI/REI drag and drop goes through Tom's {@link AbstractFilteredMenu#setPhantom} (ghosts only).
 */
public class IOMenu extends AbstractFilteredMenu {
	public static final int WIDTH = 176, UPGRADES = AbstractIOBlockEntity.SLOTS + 36;

	private final AbstractIOBlockEntity be;
	private final Container filters;
	private final Container upgrades;
	/** Client: index of the active {@link IOMode} in {@link IOModes#all()}. */
	public int mode;
	/** Client: redstone mode ({@code AbstractIOBlockEntity.RS_*}), used with a Redstone Upgrade. */
	public int redstoneMode;

	public IOMenu(MenuType<IOMenu> type, int id, Inventory inv, AbstractIOBlockEntity be) {
		super(type, id, inv);
		this.be = be;
		this.filters = be != null ? be.getFilters() : new SimpleContainer(AbstractIOBlockEntity.SLOTS);

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				addSlot(new FilterSlot(filters, col + row * 3, 62 + col * 18, 17 + row * 18));
			}
		}
		MenuUtil.addPlayerSlots(this::addSlot, inv, 8, 84);
		boolean exporter = type == ModContent.EXPORTER_MENU;
		this.upgrades = be != null ? be.getUpgrades() : new UpgradeContainer(AbstractIOBlockEntity.UPGRADE_SLOTS, exporter ? Upgrades.EXPORTER : Upgrades.IMPORTER, null);
		for (int i = 0; i < AbstractIOBlockEntity.UPGRADE_SLOTS; i++) addSlot(new UpgradeSlot(upgrades, i, WIDTH));
		addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return be != null ? IOModes.indexOf(be.getMode()) : 0;
			}

			@Override
			public void set(int value) {
				mode = value;
			}
		});
		addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return be != null ? be.getRedstoneMode() : 0;
			}

			@Override
			public void set(int value) {
				redstoneMode = value;
			}
		});
	}

	/** Client and server: is a Redstone Upgrade installed (the upgrade slots are synced)? */
	public boolean hasRedstoneUpgrade() {
		return UpgradeContainer.count(upgrades, ModContent.REDSTONE_UPGRADE) > 0;
	}

	public IOMode modeType() {
		return IOModes.byIndex(mode);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (be == null) return false;
		if (id == 0) be.cycleMode();
		else if (id == 1 && be.hasRedstoneUpgrade()) be.cycleRedstoneMode();
		else return false;
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return be == null || be.stillValid(player);
	}

	/** Tom's filter items are real items in the slot, everything else is a ghost. */
	public static boolean isRealFilter(ItemStack stack) {
		return stack.getItem() instanceof IItemFilter;
	}

	/**
	 * Filter slots: click with a plain item = ghost copy; with a Tom's filter = put it in (a filter already there goes to the
	 * cursor or the inventory); empty hand = take a real filter out or clear a ghost.
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId < 0 || slotId >= AbstractIOBlockEntity.SLOTS) {
			super.clicked(slotId, button, input, player);
			return;
		}
		if (input == ContainerInput.QUICK_MOVE) {
			quickMoveStack(player, slotId);
			return;
		}
		if (input != ContainerInput.PICKUP) return;
		Slot slot = slots.get(slotId);
		ItemStack cur = slot.getItem();
		ItemStack carried = getCarried();
		if (carried.isEmpty()) {
			if (isRealFilter(cur)) setCarried(cur);
			slot.set(ItemStack.EMPTY);
		} else if (isRealFilter(carried)) {
			slot.set(carried.split(1));
			if (isRealFilter(cur)) {
				if (carried.isEmpty()) setCarried(cur);
				else giveBack(player, cur);
			}
		} else {
			if (isRealFilter(cur)) giveBack(player, cur);
			slot.set(carried.copyWithCount(1));
		}
	}

	/** Real filter back to the player: inventory, or dropped if it is full (runs on client and server, like vanilla clicks). */
	private static void giveBack(Player player, ItemStack stack) {
		//? if >=26.3 {
		player.getInventory().placeItemBackInInventory(stack, Prediction.PREDICTED);
		//?}
		//? if <26.3 {
		/*player.getInventory().placeItemBackInInventory(stack);
		*///?}
	}

	/** JEI/REI drag: ghosts only, a Tom's filter item would be duplicated. */
	@Override
	public void setPhantom(Slot slot, ItemStack stack) {
		if (!isRealFilter(stack)) super.setPhantom(slot, stack);
	}

	/**
	 * Shift click: a real filter goes back into the inventory, a ghost is cleared; from the player inventory upgrades go into the
	 * panel, a Tom's filter (one item) or a ghost copy of anything else into the first free filter slot.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot from = slots.get(index);
		if (!from.hasItem()) return ItemStack.EMPTY;
		if (index < AbstractIOBlockEntity.SLOTS) {
			if (isRealFilter(from.getItem())) giveBack(player, from.getItem().copy());
			from.set(ItemStack.EMPTY);
			return ItemStack.EMPTY;
		}
		ItemStack stack = from.getItem();
		if (index >= UPGRADES || upgrades.canPlaceItem(0, stack)) {
			ItemStack copy = stack.copy();
			boolean moved = index >= UPGRADES ? moveItemStackTo(stack, AbstractIOBlockEntity.SLOTS, UPGRADES, true) : moveItemStackTo(stack, UPGRADES, slots.size(), false);
			if (!moved) return ItemStack.EMPTY;
			if (stack.isEmpty()) from.setByPlayer(ItemStack.EMPTY);
			else from.setChanged();
			return copy;
		}
		for (int i = 0; i < AbstractIOBlockEntity.SLOTS; i++) {
			if (ItemStack.isSameItemSameComponents(filters.getItem(i), stack)) return ItemStack.EMPTY;
		}
		for (int i = 0; i < AbstractIOBlockEntity.SLOTS; i++) {
			if (!filters.getItem(i).isEmpty()) continue;
			slots.get(i).set(isRealFilter(stack) ? stack.split(1) : stack.copyWithCount(1));
			from.setChanged();
			break;
		}
		return ItemStack.EMPTY;
	}
}
