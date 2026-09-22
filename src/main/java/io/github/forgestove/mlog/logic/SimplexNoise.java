package io.github.forgestove.mlog.logic;
/**
 * 二维单形噪声。
 * <p>只搬 2D 那一份：{@code noise} 只吃两个参数。种子固定 0，同一个坐标
 * 必须给同一个值，否则逻辑处理器不可复现。
 */
public final class SimplexNoise {
	/** 12 个梯度，2D 只用到前两个分量。 */
	private static final int[][] GRAD3 = {
		{1, 1, 0},
		{-1, 1, 0},
		{1, -1, 0},
		{-1, -1, 0},
		{1, 0, 1},
		{-1, 0, 1},
		{1, 0, -1},
		{-1, 0, -1},
		{0, 1, 1},
		{0, -1, 1},
		{0, 1, -1},
		{0, -1, -1}
	};
	/** @return 坐标 {@code (x, y)} 上的噪声值，量级约在 [-1, 1]。 */
	public static double raw2d(double x, double y) {
		// 把输入空间斜切到网格上，找出所在的三角形格子
		var s = (x + y) * (0.5 * (Math.sqrt(3.0) - 1.0));
		var i = floor(x + s);
		var j = floor(y + s);
		var t = (i + j) * ((3.0 - Math.sqrt(3.0)) / 6.0);
		var x0 = x - (i - t);
		var y0 = y - (j - t);
		// 三角形往哪边倒：先迈 x 还是先迈 y
		var i1 = 0;
		var j1 = 1;
		if (x0 > y0) {
			i1 = 1;
			j1 = 0;
		}
		var g = (3.0 - Math.sqrt(3.0)) / 6.0;
		var ii = i & 255;
		var jj = j & 255;
		// 三个角点各自的梯度下标，三个分量加起来就是这一格的噪声
		return 70.0 * (
			corner(perm(ii + perm(jj)) % 12, x0, y0) + corner(perm(ii + i1 + perm(jj + j1)) % 12, x0 - i1 + g, y0 - j1 + g) + corner(
				perm(ii
					+ 1
					+ perm(jj + 1)) % 12, x0 - 1.0 + 2.0 * g, y0 - 1.0 + 2.0 * g
			)
		);
	}
	/** 负整数会偏下一格（{@code -1.0} 算成 -2），为数值一致不修。 */
	private static int floor(double x) {
		return x > 0 ? (int) x : (int) x - 1;
	}
	/** @return 一个角点的贡献，落在衰减半径之外就是 0。 */
	private static double corner(int gradient, double x, double y) {
		var t = 0.5 - x * x - y * y;
		if (t < 0) return 0;
		t *= t;
		return t * t * (GRAD3[gradient][0] * x + GRAD3[gradient][1] * y);
	}
	/**
	 * 哈希：坐标压到 0-255 再打散。
	 * <p>签名本来带一个 seed，这里固定成 0 并进常数里。
	 */
	private static int perm(int x) {
		x = (x & 255) * 0x45d9f3b;
		var hi = x >>> 16;
		x = (hi ^ x) * 0x45d9f3b;
		hi = x >>> 16;
		return (hi ^ x) & 0xff;
	}
}
