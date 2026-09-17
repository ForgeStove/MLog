package io.github.forgestove.mlog.client.gui.logic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
/**
 * 跳转连线的绘制。
 * <p>形状是 Mindustry 的梯形折线：从起点向右伸出，纵向移动到目标附近，再折回目标。
 * <p>MC 没有画粗线的 API，直接按点位拼四边形。折点处两段的边界各自延长求交、共用同一对交点，
 * 接缝因此是个尖角：每段沿自身法向都恰好是线宽，不会有一段被公共端边撑成宽口的梯形。
 * <p>顶点是塞进 {@code RenderType.gui()} 那个批次的，不会立刻绘制：调用方必须在
 * 裁剪区关闭之前 {@code gui.flush()} 一次，否则整条线会漏到裁剪范围之外。
 */
@OnlyIn(Dist.CLIENT)
public final class CurveRenderer {
	/**
	 * 线宽，以线段自身为轴居中。
	 * <p>MDT 是 {@code Lines.stroke(Scl.scl(4f))}，按两边 UI 的换算比例（行高 40 → 16，即 0.4）
	 * 折过来是 1.6。这里是手给顶点，带得动小数，不用取整。
	 */
	private static final double LINE_W = 1.6;
	/** 半线宽：两条边各沿法向让出这么多。 */
	private static final double HALF = LINE_W / 2.0;
	/** 从 {@code (x1,y1)} 连到 {@code (x2,y2)}，向右伸出 {@code reach} 像素绕行。 */
	public static void curve(GuiGraphics gui, double x1, double y1, double x2, double y2, int color, float reach) {
		var dy = Double.compare(y2, y1) * reach * 0.5;
		// 两卡片挨得比伸出距离还近时梯形会自交，中间拧出一个回环，这时退化成折角折线
		var corner = intersection(x1, y1, x1 + reach, y1 + dy, x2, y2, x1 + reach, y2 - dy);
		if (corner != null) path(gui, color, x1, y1, corner[0], corner[1], x2, y2);
		else path(gui, color, x1, y1, x1 + reach, y1 + dy, x1 + reach, y2 - dy, x2, y2);
	}
	/** @return 两条线段的交点，平行、共线或不相交时返回 {@code null}。 */
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
	/** 顺着折线逐段铺四边形，相邻两段在折点处的两个角点由 {@link #miter} 算出，因此严格重合。 */
	private static void path(GuiGraphics gui, int color, double... points) {
		var count = points.length / 2;
		for (var i = 0; i < count - 1; i++) {
			var ax = points[i * 2];
			var ay = points[i * 2 + 1];
			var bx = points[(i + 1) * 2];
			var by = points[(i + 1) * 2 + 1];
			var len = Math.hypot(bx - ax, by - ay);
			if (len < 0.5) continue;
			// 本段两条边沿法向各让出半线宽；端头没有邻段，端边就垂直于本段
			var nx = (ay - by) / len * HALF;
			var ny = (bx - ax) / len * HALF;
			var head = i == 0 ? new double[]{ax + nx, ay + ny, ax - nx, ay - ny} : miter(points, i);
			var tail = i == count - 2 ? new double[]{bx + nx, by + ny, bx - nx, by - ny} : miter(points, i + 1);
			quad(gui, color, head[0], head[1], tail[0], tail[1], tail[2], tail[3], head[2], head[3]);
		}
	}
	/**
	 * @return 折点 {@code at} 处两段边界延长线的两个交点 {@code [x+,y+,x-,y-]}，两侧邻段共用这一对点。
	 * 	<p>交点沿本段法向的偏移恒是半线宽，只是顺着端边方向又多走出去了一截——折角越尖走得越远。
	 * 	每段因此都保持自己的线宽，不会像「端边取两段公共的那条直线」那样把斜段截成宽口的梯形。
	 */
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
		// 进、出两段的方向，各自的法向就是它的两条边的偏移方向
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
			// 两条边都是「折点加减偏移」再沿本段伸出，解出它们相遇的位置
			var ax = sign * (n1x - n2x);
			var ay = sign * (n1y - n2y);
			// 折角接近 180°（原路折回）时两段边界平行，交点跑到无穷远，退回折点处的偏移点
			var t = Math.abs(cross) < 1E-9 ? 0 : -(ax * u2y - ay * u2x) / cross;
			result[i * 2] = px + sign * n1x + t * u1x;
			result[i * 2 + 1] = py + sign * n1y + t * u1y;
		}
		return result;
	}
	/**
	 * 铺一个实心四边形，四个顶点按顺序给出。
	 * <p>走 {@code RenderType.gui()} 那个批次，和 {@code gui.fill} 是同一套渲染状态，
	 * 但顶点可以带小数，斜边的边界因此落在像素中间而不是被取整。
	 */
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
