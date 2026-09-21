package io.github.forgestove.mlog.content.microprocessor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public class WorldProcessorBlock extends MicroProcessorBlock implements GameMasterBlock {
	public static final MapCodec<WorldProcessorBlock> CODEC = simpleCodec(WorldProcessorBlock::new);
	public WorldProcessorBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return player.canUseGameMasterBlocks() ? super.useWithoutItem(state, level, pos, player, hit) : InteractionResult.PASS;
	}
}
