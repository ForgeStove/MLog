package io.github.forgestove.mlog.client.gui.logic;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 跳转连线的绘制。
 * <p>形状是 Mindustry 的梯形折线：从起点向右伸出，纵向移动到目标附近，再折回目标。
 * <p>MC 没有画粗线的 API，只能自己拼矩形。
 */
@OnlyIn(Dist.CLIENT)
public final class CurveRenderer {
	/** 线宽，以线段自身为轴居中。 */
	private static final int LINE_W = 2;
	/** 已画过的线段，一帧一组。跳向同一目标的线会共用一层的伸出距离，目标那段完全重合 */
	private static final Set<String> DRAWN = new HashSet<>();
	/** 开始一帧的绘制，清掉上一帧的去重记录。 */
	public static void begin() {
		DRAWN.clear();
	}
	/** 从 {@code (x1,y1)} 连到 {@code (x2,y2)}，向右伸出 {@code reach} 像素绕行。 */
	public static void curve(GuiGraphics gui, double x1, double y1, double x2, double y2, int color, float reach) {
		var dy = Double.compare(y2, y1) * reach * 0.5;
		// 两卡片挨得比伸出距离还近时梯形会自交，中间拧出一个回环，这时退化成折角折线
		var corner = intersection(x1, y1, x1 + reach, y1 + dy, x2, y2, x1 + reach, y2 - dy);
		if (corner != null) {
			line(gui, x1, y1, corner[0], corner[1], color);
			line(gui, corner[0], corner[1], x2, y2, color);
			return;
		}
		line(gui, x1, y1, x1 + reach, y1 + dy, color);
		line(gui, x1 + reach, y1 + dy, x1 + reach, y2 - dy, color);
		line(gui, x1 + reach, y2 - dy, x2, y2, color);
	}
	/** @return 两条线段的交点，平行、共线或不相交时返回 {@code null}。 */
	private static double @Nullable [] intersection(
		double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4
	) {
		var d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3);
		if (d == 0) return null;
		var t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d;
		var u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d;
		if (t < 0 || t > 1 || u < 0 || u > 1) return null;
		return new double[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t};
	}
	private static void line(GuiGraphics gui, double x1, double y1, double x2, double y2, int color) {
		var dx = x2 - x1;
		var dy = y2 - y1;
		var length = Math.hypot(dx, dy);
		if (length < 0.5) return;
		// 这段本帧已经用同样的颜色画过就跳过：共用一层伸出距离的两条线，目标附近那段是完全同一段。
		// 颜色要并进去：高亮的那条压在普通线上面，重合段得换个颜色再画一遍才有高亮效果
		if (!DRAWN.add(key(x1, y1, x2, y2, color))) return;
		var len = (int) Math.ceil(length);
		var pose = gui.pose();
		pose.pushPose();
		// 先把坐标系转到线段自身的方向上，再当成一长条矩形画。逐点盖小方块的画法在斜线上会留下一串阶梯
		pose.translate((float) x1, (float) y1, 0F);
		pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
		gui.fill(0, -LINE_W / 2, len, LINE_W - LINE_W / 2, color);
		pose.popPose();
	}
	/**
	 * @return 线段的去重键，含颜色。
	 * 	<p>坐标取两位小数：同一段线的端点由同一份数据算出，值完全一样；
	 * 	方向反着画的算同一段，所以两端排序后再拼
	 */
	private static String key(double x1, double y1, double x2, double y2, int color) {
		var a = "%.2f,%.2f".formatted(x1, y1);
		var b = "%.2f,%.2f".formatted(x2, y2);
		return (a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a) + "#" + color;
	}
}
