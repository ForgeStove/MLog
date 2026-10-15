package io.github.forgestove.mlog.client.gui.logic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
/** 跳转连线；顶点提交到 {@code RenderType.gui()} 批次，调用方须在裁剪区关闭前 {@code gui.flush()}，否则线条越出裁剪范围。 */
public final class CurveRenderer {
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
	/** 逐段铺四边形，相邻两段共用折点处的对角点，故无接缝。 */
	private static void path(GuiGraphics gui, int color, double... points) {
		var count = points.length / 2;
		for (var i = 0; i < count - 1; i++) {
			var ax = points[i * 2];
			var ay = points[i * 2 + 1];
			var bx = points[(i + 1) * 2];
			var by = points[(i + 1) * 2 + 1];
			var len = Math.hypot(bx - ax, by - ay);
			if (len < 0.5) continue;
			// 本段两条边沿法向各让出半线宽；端头无邻段，端边垂直于本段
			var nx = (ay - by) / len * HALF;
			var ny = (bx - ax) / len * HALF;
			var head = i == 0 ? new double[]{ax + nx, ay + ny, ax - nx, ay - ny} : miter(points, i);
			var tail = i == count - 2 ? new double[]{bx + nx, by + ny, bx - nx, by - ny} : miter(points, i + 1);
			quad(gui, color, head[0], head[1], tail[0], tail[1], tail[2], tail[3], head[2], head[3]);
		}
	}
	/** 折点 {@code at} 处两段边界延长线的两个交点 {@code [x+,y+,x-,y-]}；交点沿法向偏移恒为半线宽，故每段保持自身线宽。 */
	private static double[] miter(double[] points, int at) {
		var px = points[at * 2];
		var py = points[at * 2 + 1];
		var inX = px - points[(at - 1) * 2];
		var inY = py - points[(at - 1) * 2 + 1];
		var outX = points[(at + 1) * 2] - px;
		var outY = points[(at + 1) * 2 + 1] - py;
		var inLen = Math.hypot(inX, inY);
		var outLen = Math.hypot(outX, outY);
		if (inLen < 1E-6 || outLen < 1E-6) return new double[]{px, py, px, py};
		// 进、出两段的方向，其法向即两条边的偏移方向
		var u1x = inX / inLen;
		var u1y = inY / inLen;
		var u2x = outX / outLen;
		var u2y = outY / outLen;
		var n1x = -u1y * HALF;
		var n1y = u1x * HALF;
		var n2x = -u2y * HALF;
		var n2y = u2x * HALF;
		var cross = u1x * u2y - u1y * u2x;
		var result = new double[4];
		for (var i = 0; i < 2; i++) {
			var sign = i == 0 ? 1 : -1;
			// 两条边均由折点加减偏移后沿本段伸出，解出其交点
			var ax = sign * (n1x - n2x);
			var ay = sign * (n1y - n2y);
			// 折角接近 180°（原路折回）时两段边界平行、交点位于无穷远，改用折点处的偏移点
			var t = Math.abs(cross) < 1E-9 ? 0 : -(ax * u2y - ay * u2x) / cross;
			result[i * 2] = px + sign * n1x + t * u1x;
			result[i * 2 + 1] = py + sign * n1y + t * u1y;
		}
		return result;
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
