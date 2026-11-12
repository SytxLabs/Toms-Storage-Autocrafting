package eu.sytxlabs.tsautocraft.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import com.tom.storagemod.block.IInventoryCable;
import com.tom.storagemod.inventory.InventoryCableNetwork;
import com.tom.storagemod.util.TickerUtil;

//? if <26.3 {

/*import com.mojang.serialization.MapCodec;
*///?}

/**
 * Base for all addon blocks: a functional node in Tom's cable network
 * ({@link IInventoryCable#isFunctionalNode()} = true), ticks on the server and opens its menu.
 * <p>
 * These blocks expose no item storage, so they (and the machines behind them)
 * are not picked up as storage by the Inventory Connector.
 */
public class NodeBlock extends BaseEntityBlock implements IInventoryCable {

	@FunctionalInterface
	public interface BEFactory {
		BlockEntity create(BlockPos pos, BlockState state);
	}

	private final BEFactory factory;

	public NodeBlock(Block.Properties properties, BEFactory factory) {
		super(properties);
		this.factory = factory;
	}

	//? if <26.3 {
	/*@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return MapCodec.unit(this);
	}
	*///?}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return factory.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return TickerUtil.createTicker(world, false, true);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack item, BlockState state, Level world, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (item.getItem() instanceof eu.sytxlabs.tsautocraft.MemoryCardItem) return InteractionResult.PASS;
		return super.useItemOn(item, state, world, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) return InteractionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof MenuProvider mp) player.openMenu(mp);
		return InteractionResult.SUCCESS;
	}

	// Keep Tom's cable network cache up to date (same approach as Tom's Level Emitter)

	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel l) InventoryCableNetwork.getNetwork(l).markNodeInvalid(pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
		super.neighborChanged(state, level, pos, block, orientation, moved);
		if (!level.isClientSide()) {
			if (level.getBlockEntity(pos) instanceof eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity p) p.invalidateTargets();
			InventoryCableNetwork n = InventoryCableNetwork.getNetwork(level);
			n.markNodeInvalid(pos);
			if (orientation != null) {
				for (var d : orientation.getDirections()) n.markNodeInvalid(pos.relative(d));
			} else {
				for (var d : Direction.values()) n.markNodeInvalid(pos.relative(d));
			}
		}
	}
}
