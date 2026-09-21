package io.github.forgestove.mlog.client.gui;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 鼠标光标。
 * <p>悬停在可拖的元素上给手型，悬停在输入框上给文本光标。
 * <p>光标句柄按需创建后缓存，{@code glfwSetCursor} 也只在状态真的变化时才调。
 * <p>一帧里元素是<b>边画边报</b>的，所以先只记下请求，等界面画完由 {@link #apply} 统一下发。
 * 边报边下发的话，一帧内会先下发箭头再下发手型，鼠标停着不动时看着就是来回闪。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicCursor {
	/** 已创建的 GLFW 标准光标，键是 {@code GLFW_*_CURSOR}。 */
	private static final Map<Integer, Long> CURSORS = new HashMap<>();
	/** 当前下发的光标，{@code 0} 表示默认箭头，{@code -1} 表示还没下发过。 */
	private static int current = -1;
	/** 本帧请求的光标，渲染时由元素往里写。 */
	private static int requested;
	/** 开始渲染一帧。这一帧没有元素改光标的话，{@link #apply} 会把它收回默认箭头。 */
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
	/** 下发本帧的光标。必须是界面把这一帧的控件都画完之后调。 */
	public static void apply(Post ignoredEvent) {
		if (requested == current) return;
		current = requested;
		var handle = requested == 0 ? 0L : CURSORS.computeIfAbsent(requested, GLFW::glfwCreateStandardCursor);
		GLFW.glfwSetCursor(mc.getWindow().getWindow(), handle);
	}
}
