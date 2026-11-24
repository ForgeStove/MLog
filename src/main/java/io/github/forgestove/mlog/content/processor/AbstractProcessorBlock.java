package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.content.processor.behaviour.*;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.Arrays;
public abstract class AbstractProcessorBlock extends BaseEntityBlock {
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	private static final float MIDDLE = 8F;
	private static final float[][] PARTS = {
		{2, 0.02F, 2, 14, 7.02F, 14},
		{3.83F, 6.4F, 3.83F, 12.17F, 7.4F, 12.17F},
		{4, 0.02F, 14, 12, 2.02F, 16},
		{0, 0.02F, 4, 2, 2.02F, 12},
		{14, 0.02F, 4, 16, 2.02F, 12},
		{4, 0.02F, 0, 12, 2.02F, 2},
		{2.41F, 0, 11.58F, 6.83F, 8, 16},
		{2.99F, 1, 10.75F, 5.24F, 7.5F, 12.99F},
		{0, 0, 9.17F, 4.42F, 8, 13.59F},
		{2.41F, 0, 0, 6.83F, 8, 4.42F},
		{2.99F, 1, 3.01F, 5.24F, 7.5F, 5.25F},
		{0, 0, 2.41F, 4.42F, 8, 6.83F},
		{9.17F, 0, 11.58F, 13.59F, 8, 16},
		{10.76F, 1, 10.75F, 13.01F, 7.5F, 12.99F},
		{11.58F, 0, 9.17F, 16, 8, 13.59F},
		{9.17F, 0, 0, 13.59F, 8, 4.42F},
		{10.76F, 1, 3.01F, 13.01F, 7.5F, 5.25F},
		{11.58F, 0, 2.41F, 16, 8, 6.83F},
	};
	private static final VoxelShape[] SHAPES = Arrays.stream(Direction.values())
		.map(AbstractProcessorBlock::turn)
		.toArray(VoxelShape[]::new);
	public AbstractProcessorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}
	private static VoxelShape turn(Direction facing) {
		Quaternionf rotation = switch (facing) {
			case DOWN -> new Quaternionf().rotationX((float) Math.PI);
			case UP -> new Quaternionf();
			case NORTH -> new Quaternionf().rotationX((float) (-Math.PI / 2));
			case SOUTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
			case WEST -> new Quaternionf().rotationZ((float) (Math.PI / 2));
			case EAST -> new Quaternionf().rotationZ((float) (-Math.PI / 2));
		};
		return Arrays.stream(PARTS).map(part -> turn(part, rotation)).reduce(Shapes.empty(), Shapes::or);
	}
	private static VoxelShape turn(float[] part, Quaternionf rotation) {
		var min = turn(new Vector3f(part[0], part[1], part[2]), rotation);
		var max = turn(new Vector3f(part[3], part[4], part[5]), rotation);
		return Shapes.box(
			Math.min(min.x, max.x) / 16.0,
			Math.min(min.y, max.y) / 16.0,
			Math.min(min.z, max.z) / 16.0,
			Math.max(min.x, max.x) / 16.0,
			Math.max(min.y, max.y) / 16.0,
			Math.max(min.z, max.z) / 16.0
		);
	}
	private static Vector3f turn(Vector3f v, Quaternionf rotation) {
		return v.sub(MIDDLE, MIDDLE, MIDDLE).rotate(rotation).add(MIDDLE, MIDDLE, MIDDLE);
	}
	public static boolean isEditButton(BlockGetter level, BlockPos pos, BlockHitResult hit) {
		var behaviour = ProcessorBehaviour.get(level, pos, EditButtonBehaviour.TYPE);
		return behaviour != null && behaviour.testHit(level, pos, level.getBlockState(pos), hit);
	}
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide
			&& player.isCreative()
			&& (!(this instanceof GameMasterBlock) || player.canUseGameMasterBlocks())
			&& level.getBlockEntity(pos) instanceof AbstractProcessorBlockEntity processor) {
			var stack = new ItemStack(this);
			processor.saveToItem(stack, level.registryAccess());
			popResource(level, pos, stack);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, pos);
		super.onRemove(state, level, pos, newState, isMoving);
	}
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !isEditButton(level, pos, hit)) return InteractionResult.PASS;
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
			level.getBlockEntity(pos, processorType()).ifPresent(be -> serverPlayer.openMenu(be, pos));
		return InteractionResult.SUCCESS;
	}
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}
	public abstract BlockEntityType<? extends AbstractProcessorBlockEntity> processorType();
	public abstract @Nullable Integer linkRange();
}
