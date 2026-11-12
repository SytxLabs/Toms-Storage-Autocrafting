package eu.sytxlabs.tsautocraft.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import com.tom.storagemod.util.BlockFace;

/**
 * Node with a front side ({@link #FACING} points at the target inventory or machine).
 * Placement: click on the target, the front then faces it. The front side does not connect to the cable network.
 */
public class DirectionalNodeBlock extends NodeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public DirectionalNodeBlock(Block.Properties properties, BEFactory factory) {
		super(properties, factory);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public boolean canConnectFrom(BlockState state, Direction dir) {
		return state.getValue(FACING) != dir;
	}

	@Override
	public List<BlockFace> nextScan(Level world, BlockState state, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		List<BlockFace> next = new ArrayList<>(5);
		for (Direction d : Direction.values()) {
			if (d != facing) next.add(new BlockFace(pos.relative(d), d.getOpposite()));
		}
		return next;
	}
}
