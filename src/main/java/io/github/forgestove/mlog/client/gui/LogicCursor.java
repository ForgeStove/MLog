package io.github.forgestove.mlog.client.gui;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 鼠标光标。
 * <p>悬停在可拖动元素上显示手型，悬停在输入框上显示文本光标。
 * <p>光标句柄按需创建并缓存，{@code glfwSetCursor} 仅在状态变化时调用。
 * <p>元素在渲染过程中请求光标，故此处仅记录请求，待界面绘制完成后由 {@link #apply} 统一下发。
 * 若随请求即时下发，一帧内会先设为箭头再设为手型，鼠标静止时表现为反复闪烁。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicCursor {
	/** 已创建的 GLFW 标准光标，键是 {@code GLFW_*_CURSOR}。 */
	private static final Map<Integer, Long> CURSORS = new HashMap<>();
	/** 当前已下发的光标，{@code 0} 为默认箭头，{@code -1} 表示尚未下发。 */
	private static int current = -1;
	/** 本帧请求的光标，渲染时由元素写入。 */
	private static int requested;
	/** 开始渲染一帧；该帧若无元素请求光标，{@link #apply} 将回收为默认箭头。 */
	public static void reset(Pre ignoredEvent) {
		requested = 0;
	}
	/** 可拖动的元素。 */
	public static void setHand() {
		requested = GLFW.GLFW_HAND_CURSOR;
	}
	/** 输入框。 */
	public static void setIBeam() {
		requested = GLFW.GLFW_IBEAM_CURSOR;
	}
	/** 下发本帧的光标，须在界面绘制完本帧控件后调用。 */
	public static void apply(Post ignoredEvent) {
		if (requested == current) return;
		current = requested;
		var handle = requested == 0 ? 0L : CURSORS.computeIfAbsent(requested, GLFW::glfwCreateStandardCursor);
		GLFW.glfwSetCursor(mc.getWindow().getWindow(), handle);
	}
}
