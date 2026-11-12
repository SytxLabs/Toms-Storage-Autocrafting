package eu.sytxlabs.tsautocraft.block;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.tom.storagemod.block.AbstractStorageTerminalBlock;

import eu.sytxlabs.tsautocraft.blockentity.PatternAccessTerminalBlockEntity;

//? if <26.3 {

/*import com.mojang.serialization.MapCodec;
*///?}

/**
 * Pattern Access Terminal: shape, placement and cable connection of a Tom's terminal (connects only on its back); the GUI shows the pattern slots of every Pattern Provider on the network.
 */
public class PatternAccessTerminalBlock extends AbstractStorageTerminalBlock {

	public PatternAccessTerminalBlock(Block.Properties properties) {
		super(properties);
	}

	//? if <26.3 {
	/*@Override
	protected MapCodec<? extends AbstractStorageTerminalBlock> codec() {
		return MapCodec.unit(this);
	}
	*///?}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PatternAccessTerminalBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) return InteractionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof PatternAccessTerminalBlockEntity be && be.stillValid(player)) player.openMenu(be);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
	}
}
