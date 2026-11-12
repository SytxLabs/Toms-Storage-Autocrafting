package eu.sytxlabs.tsautocraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import eu.sytxlabs.tsautocraft.api.FluidUnits;

/** Virtual item for exact fluid amounts in patterns ({@link FluidUnits}): one item = 1 mB of the fluid in its component. */
public class FluidUnitItem extends Item {
	public FluidUnitItem(Item.Properties p) {
		super(p);
	}

	@Override
	public Component getName(ItemStack stack) {
		Fluid f = FluidUnits.fluid(stack);
		if (f == null) return super.getName(stack);
		Block block = f.defaultFluidState().createLegacyBlock().getBlock();
		// Mod fluids without a fluid block would read "Air": use the fluid id instead
		Component name = block == Blocks.AIR ? Component.literal(BuiltInRegistries.FLUID.getKey(f).toString()) : block.getName();
		return Component.translatable("item.tsautocraft.fluid_unit.named", name);
	}
}
