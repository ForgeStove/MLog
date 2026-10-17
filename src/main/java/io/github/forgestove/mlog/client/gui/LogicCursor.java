package io.github.forgestove.mlog.client.gui;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 光标句柄按需创建并缓存，{@code glfwSetCursor} 仅在状态变化时调用。
 * 元素在渲染过程中请求光标，{@link #apply} 须待界面绘制完本帧控件后下发；若随请求即时下发，一帧内会先设为箭头再设为手型，鼠标静止时表现为反复闪烁。
 */
public class LogicCursor {
	/** 已创建的 GLFW 标准光标，键是 {@code GLFW_*_CURSOR}。 */
	private static final Map<Integer, Long> CURSORS = new HashMap<>();
	/** 当前已下发的光标，{@code 0} 为默认箭头，{@code -1} 表示尚未下发。 */
	private static int current = -1;
	/** 本帧请求的光标，渲染时由元素写入。 */
	private static int requested;
	public static void reset(Pre ignoredEvent) {
		requested = 0;
	}
	public static void setHand() {
		requested = GLFW.GLFW_HAND_CURSOR;
	}
	public static void setIBeam() {
		requested = GLFW.GLFW_IBEAM_CURSOR;
	}
	public static void apply(Post ignoredEvent) {
		if (requested == current) return;
		current = requested;
		var handle = requested == 0 ? 0L : CURSORS.computeIfAbsent(requested, GLFW::glfwCreateStandardCursor);
		GLFW.glfwSetCursor(mc.getWindow().getWindow(), handle);
	}
}
