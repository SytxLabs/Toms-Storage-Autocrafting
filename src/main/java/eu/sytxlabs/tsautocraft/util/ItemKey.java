package eu.sytxlabs.tsautocraft.util;

import net.minecraft.world.item.ItemStack;

public final class ItemKey {
	private final ItemStack stack;
	private final int hash;

	public ItemKey(ItemStack stack) {
		this.stack = stack.copyWithCount(1);
		this.hash = ItemStack.hashItemAndComponents(this.stack);
	}

	public ItemStack create(int count) {
		return stack.copyWithCount(count);
	}

	/** Read only, do not modify. */
	public ItemStack template() {
		return stack;
	}

	public boolean matches(ItemStack other) {
		return ItemStack.isSameItemSameComponents(stack, other);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ItemKey k && hash == k.hash && ItemStack.isSameItemSameComponents(stack, k.stack);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return stack.getItem().toString();
	}
}
