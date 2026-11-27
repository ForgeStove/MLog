package io.github.forgestove.mlog.content.processor.behaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
/**
 * 可在方块表面显示小面板并响应点击的行为。
 * 与 Create 的 {@code ValueSettingsBehaviour} 同构；面板内容由客户端按具体行为类型绘制。
 */
@SuppressWarnings("unused")
public interface ValueSettingsBehaviour {
	ValueBoxTransform getSlotPositioning();
	boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit);
	Component getLabel();
	default boolean isActive() {
		return true;
	}
	default boolean mayInteract(Player player) {
		return true;
	}
	default boolean onlyVisibleWithWrench() {
		return false;
	}
}
