package io.github.forgestove.mlog.content.processor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import org.jetbrains.annotations.Nullable;
public final class ProcessorRightClick {
	public static void onRightClickBlock(RightClickBlock event) {
		if (event.getSide() != LogicalSide.SERVER || !canOverride(event)) return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}
	/** 潜行、编辑按钮、无权限的世界处理器，以及声明跳过方块交互的事件都交回方块自身。 */
	public static boolean canOverride(RightClickBlock event) {
		var player = event.getEntity();
		var level = event.getLevel();
		var pos = event.getPos();
		if (player.isShiftKeyDown()) return false;
		if (!(level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock)) return false;
		if (!accessible(level, pos, player)) return false;
		if (AbstractProcessorBlock.isEditButton(level, pos, event.getHitVec())) return false;
		return !event.getUseBlock().isFalse();
	}
	/** 世界处理器需权限才由链接模式接管。 */
	public static boolean accessible(BlockGetter level, BlockPos pos, @Nullable Player player) {
		if (!(level.getBlockState(pos).getBlock() instanceof WorldProcessorBlock)) return true;
		return player != null && player.canUseGameMasterBlocks();
	}
}
