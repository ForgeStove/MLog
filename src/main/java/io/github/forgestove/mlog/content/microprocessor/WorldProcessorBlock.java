package io.github.forgestove.mlog.content.microprocessor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
/**
 * 世界处理器方块，对应 Mindustry 的 {@code world-processor}。
 * <p>形状、朝向、界面、方块实体都和微型处理器一样，差别只有两点：它带特权（语句表里多出世界那一类，
 * 并且跑得更快），以及只挂在这个方块上时才算特权——判定见
 * {@link MicroProcessorBlockEntity#privileged()}。
 * <p>权限照命令方块那套：{@link GameMasterBlock} 让非 OP 挖不掉，物品是
 * {@code GameMasterBlockItem}（非 OP 放不下），界面也只对 {@code canUseGameMasterBlocks} 的人开。
 */
public class WorldProcessorBlock extends MicroProcessorBlock implements GameMasterBlock {
	public static final MapCodec<WorldProcessorBlock> CODEC = simpleCodec(WorldProcessorBlock::new);
	public WorldProcessorBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	/** 对齐 CommandBlock：只有能操作命令方块的人（OP / 权限等级 2）才打得开界面。 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return player.canUseGameMasterBlocks() ? super.useWithoutItem(state, level, pos, player, hit) : InteractionResult.PASS;
	}
}
