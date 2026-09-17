package io.github.forgestove.mlog.client.event;
import io.github.forgestove.mlog.core.net.LinkPayload;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 链接模式：左键点方块建立链接，右键或 ESC 退出。同一时间只能给一个处理器链接。 */
@OnlyIn(Dist.CLIENT)
public final class LinkMode {
	private static @Nullable BlockPos processor;
	public static void start(BlockPos pos) {
		processor = pos;
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.hint"), true);
	}
	public static void onMouseButton(Pre event) {
		if (processor == null || mc.screen != null) return;
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			exit();
			event.setCanceled(true);
			return;
		}
		if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		if (!(mc.hitResult instanceof BlockHitResult hit)) return;
		var target = hit.getBlockPos();
		if (target.equals(processor)) return;
		PacketDistributor.sendToServer(new LinkPayload(processor, target, false));
		event.setCanceled(true);
	}
	private static void exit() {
		processor = null;
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.done"), true);
	}
	/** ESC 会打开暂停菜单，这里把它拦下来改成退出链接模式。 */
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
}
