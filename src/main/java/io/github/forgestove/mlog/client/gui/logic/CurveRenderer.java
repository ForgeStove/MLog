package io.github.forgestove.mlog.client.gui.logic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
/** 跳转连线；顶点提交到 {@code RenderType.gui()} 批次，调用方须在裁剪区关闭前 {@code gui.flush()}，否则线条会越出裁剪范围。 */
public class CurveRenderer {
	private static final double LINE_W = 1.6;
	private static final double HALF = LINE_W / 2.0;
	public static void curve(GuiGraphics gui, double x1, double y1, double x2, double y2, int color, float reach) {
		var dy = Double.compare(y2, y1) * reach * 0.5;
		// 两卡片间距小于伸出距离时梯形自交并形成回环，故退化为折线
		var corner = intersection(x1, y1, x1 + reach, y1 + dy, x2, y2, x1 + reach, y2 - dy);
		if (corner != null) path(gui, color, x1, y1, corner[0], corner[1], x2, y2);
		else path(gui, color, x1, y1, x1 + reach, y1 + dy, x1 + reach, y2 - dy, x2, y2);
	}
	/** 平行、共线或不相交时返回 {@code null}。 */
	private static double @Nullable [] intersection(
		double x1,
		double y1,
		double x2,
		double y2,
		double x3,
		double y3,
		double x4,
		double y4
	) {
		var d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3);
		if (d == 0) return null;
		var t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d;
		var u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d;
		if (t < 0 || t > 1 || u < 0 || u > 1) return null;
		return new double[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t};
	}
	/**
	 * 每段独立铺矩形，折点处用三角形补外侧缺角。逐段求法向，反向或短段都不会出现顶点交叉；
	 * 斜接求交点在反向时两侧偏移会互换，四顶点会连成蝴蝶结。
	 */
	private static void path(GuiGraphics gui, int color, double... points) {
		var n = points.length / 2;
		var prevNx = 0.0;
		var prevNy = 0.0;
		var prevDx = 0.0;
		var prevDy = 0.0;
		for (var i = 0; i < n - 1; i++) {
			var ax = points[i * 2];
			var ay = points[i * 2 + 1];
			var bx = points[(i + 1) * 2];
			var by = points[(i + 1) * 2 + 1];
			var dx = bx - ax;
			var dy = by - ay;
			var len = Math.hypot(dx, dy);
			if (len < 1E-9) continue;
			var nx = dy / len * HALF;
			var ny = -dx / len * HALF;
			quad(gui, color, ax + nx, ay + ny, ax - nx, ay - ny, bx - nx, by - ny, bx + nx, by + ny);
			// 上一段与本段在 A 处相接，外侧缺角用三角形补齐；顶点序须与矩形一致，否则被背面剔除
			if (i > 0) {
				var cross = prevDx * dy - prevDy * dx;
				if (cross > 1E-9) quad(gui, color, ax + prevNx, ay + prevNy, ax, ay, ax, ay, ax + nx, ay + ny);
				else if (cross < -1E-9) quad(gui, color, ax - nx, ay - ny, ax, ay, ax, ay, ax - prevNx, ay - prevNy);
			}
			prevNx = nx;
			prevNy = ny;
			prevDx = dx;
			prevDy = dy;
		}
	}
	/** 顶点取小数，斜边边界落在像素中间而不会被取整。 */
	private static void quad(
		GuiGraphics gui,
		int color,
		double x0,
		double y0,
		double x1,
		double y1,
		double x2,
		double y2,
		double x3,
		double y3
	) {
		var pose = gui.pose().last().pose();
		var consumer = gui.bufferSource().getBuffer(RenderType.gui());
		consumer.addVertex(pose, (float) x0, (float) y0, 0F).setColor(color);
		consumer.addVertex(pose, (float) x1, (float) y1, 0F).setColor(color);
		consumer.addVertex(pose, (float) x2, (float) y2, 0F).setColor(color);
		consumer.addVertex(pose, (float) x3, (float) y3, 0F).setColor(color);
	}
}
