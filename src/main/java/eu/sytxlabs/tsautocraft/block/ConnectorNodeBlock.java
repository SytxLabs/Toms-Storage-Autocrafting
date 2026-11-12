package eu.sytxlabs.tsautocraft.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.tom.storagemod.block.IInventoryCable;
import com.tom.storagemod.inventory.InventoryCableNetwork;
import com.tom.storagemod.util.BlockFace;
import com.tom.storagemod.util.TickerUtil;

//? if <26.3 {

/*import com.mojang.serialization.MapCodec;
*///?}

import org.jetbrains.annotations.Nullable;

import eu.sytxlabs.tsautocraft.blockentity.CraftingEmitterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;

/**
 * Connector-shaped network node, modelled after Tom's Inventory Cable Connector: a plate on the target side, a small stub towards the network and cable arms to every connected cable.
 * <p>
 * {@link #FACING} points at the target inventory/machine/crafter. The six direction properties tell the model
 * which cable arms to draw (for the facing side: whether a block is there at all).
 * {@link #POWERED} is only used when {@code emitsPulse} is true (Pattern Provider connector, directional Crafting Emitter).
 */
public class ConnectorNodeBlock extends BaseEntityBlock implements IInventoryCable {
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
	public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
	public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
	public static final BooleanProperty EAST = BlockStateProperties.EAST;
	public static final BooleanProperty WEST = BlockStateProperties.WEST;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final Direction[] DIRS = Direction.values();

	private final NodeBlock.BEFactory factory;
	private final boolean emitsPulse;
	private final VoxelShape[][] shapes;

	public ConnectorNodeBlock(Block.Properties properties, NodeBlock.BEFactory factory, boolean emitsPulse) {
		super(properties);
		this.factory = factory;
		this.emitsPulse = emitsPulse;
		this.shapes = makeShapes(0.125f);
		registerDefaultState(defaultBlockState().setValue(DOWN, false).setValue(UP, false).setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false).setValue(FACING, Direction.DOWN).setValue(POWERED, false));
	}

	//? if <26.3 {
	/*@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return MapCodec.unit(this);
	}
	*///?}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST, FACING, POWERED);
	}

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

	@Override
	public boolean isSignalSource(BlockState state) {
		return emitsPulse;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		if (!emitsPulse || !state.getValue(POWERED) || state.getValue(FACING).getOpposite() != side) return 0;
		BlockEntity be = level.getBlockEntity(pos);
		// Pattern Provider: only during its pulse; Crafting Emitter: while powered
		return be instanceof PatternProviderBlockEntity p && p.emitsSignal(state.getValue(FACING)) || be instanceof CraftingEmitterBlockEntity ? 15 : 0;
	}

	@Override
	public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		return 0;
	}
	//endregion

	//region Placement and connections

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connects(state, neighbor, dir));
	}

	private BlockState withConnections(BlockState state, LevelAccessor level, BlockPos pos) {
		for (Direction d : DIRS) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), connects(state, level.getBlockState(pos.relative(d)), d));
		}
		return state;
	}

	private static boolean connects(BlockState state, BlockState neighbor, Direction dir) {
		return dir == state.getValue(FACING) ? !neighbor.isAir() : IInventoryCable.canConnect(neighbor, dir);
	}

	@Override
	public boolean canConnectFrom(BlockState state, Direction dir) {
		return state.getValue(FACING) != dir;
	}

	@Override
	public List<BlockFace> nextScan(Level world, BlockState state, BlockPos pos) {
		Direction f = state.getValue(FACING);
		List<BlockFace> next = new ArrayList<>(5);
		for (Direction d : DIRS) {
			if (d != f && state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) next.add(new BlockFace(pos.relative(d), d.getOpposite()));
		}
		return next;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		BlockState s = state.setValue(FACING, rot.rotate(state.getValue(FACING)));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(rot.rotate(d)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d)));
		}
		return s;
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		BlockState s = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(d)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d)));
		}
		return s;
	}
	//endregion

	//region Shape (same as Tom's cable connector)

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return connectorShape(state);
	}

	public VoxelShape connectorShape(BlockState state) {
		int idx = 0;
		for (int j = 0; j < DIRS.length; ++j) {
			if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(DIRS[j]))) idx |= 1 << j;
		}
		return shapes[state.getValue(FACING).ordinal()][idx];
	}

	private static VoxelShape[][] makeShapes(float apothem) {
		float lo = 0.5F - apothem, hi = 0.5F + apothem;
		VoxelShape core = Block.box(lo * 16, lo * 16, lo * 16, hi * 16, hi * 16, hi * 16);
		VoxelShape[] arms = new VoxelShape[DIRS.length];
		for (int i = 0; i < DIRS.length; ++i) {
			Direction d = DIRS[i];
			arms[i] = Shapes.box(0.5D + Math.min(-apothem, d.getStepX() * 0.5D), 0.5D + Math.min(-apothem, d.getStepY() * 0.5D), 0.5D + Math.min(-apothem, d.getStepZ() * 0.5D), 0.5D + Math.max(apothem, d.getStepX() * 0.5D), 0.5D + Math.max(apothem, d.getStepY() * 0.5D), 0.5D + Math.max(apothem, d.getStepZ() * 0.5D));
		}
		VoxelShape[] combos = new VoxelShape[64];
		for (int k = 0; k < 64; ++k) {
			VoxelShape s = core;
			for (int j = 0; j < DIRS.length; ++j) if ((k & 1 << j) != 0) s = Shapes.or(s, arms[j]);
			combos[k] = s;
		}
		VoxelShape[][] ret = new VoxelShape[6][64];
		for (int i = 0; i < DIRS.length; ++i) {
			Direction d = DIRS[i];
			VoxelShape plate = Shapes.or(part(d, 16, 0, 16, 0, 2, 0), part(d, 10, 3, 10, 3, 2, 2), part(d, 6, 5, 6, 5, 2, 4));
			for (int j = 0; j < 64; j++) ret[i][j] = Shapes.or(combos[j], plate);
		}
		return ret;
	}

	private static VoxelShape part(Direction dir, float w, float wo, float h, float ho, float depth, float depthOff) {
		return switch (dir) {
			case DOWN -> shapeBox(ho, depthOff, wo, h + ho, depth + depthOff, w + wo);
			case UP -> shapeBox(ho, 16f - depth, wo, h + ho, 16f - depthOff, w + wo);
			case NORTH -> shapeBox(wo, ho, depthOff, w + wo, h + ho, depth + depthOff);
			case SOUTH -> shapeBox(wo, ho, 16f - depth, w + wo, h + ho, 16f - depthOff);
			case WEST -> shapeBox(depthOff, ho, wo, depth + depthOff, h + ho, w + wo);
			case EAST -> shapeBox(16f - depth, ho, wo, 16f - depthOff, h + ho, w + wo);
		};
	}

	private static VoxelShape shapeBox(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Block.box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}
	//endregion

	//region Keep Tom's cable network cache up to date

	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel l) InventoryCableNetwork.getNetwork(l).markNodeInvalid(pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
		super.neighborChanged(state, level, pos, block, orientation, moved);
		if (!level.isClientSide()) {
			InventoryCableNetwork n = InventoryCableNetwork.getNetwork(level);
			n.markNodeInvalid(pos);
			if (orientation != null) {
				for (var d : orientation.getDirections()) n.markNodeInvalid(pos.relative(d));
			} else {
				for (var d : Direction.values()) n.markNodeInvalid(pos.relative(d));
			}
		}
	}
	//endregion
}
