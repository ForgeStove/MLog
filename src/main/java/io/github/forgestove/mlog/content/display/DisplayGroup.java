package io.github.forgestove.mlog.content.display;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.*;
/**
 * 整组逻辑显示单元的范围。
 * <p>同朝向相邻的单元自动拼成一组，形状任意：由某一格漫开收出全部成员，再取外接框；
 * {@code cells} 记下框内哪些格是成员，{@code origin} 是外接框的角落，未必落在成员格上。
 * <p>漫开只能按外接框尺寸截断：按已找到的格数截断时结果取决于起点，各格会算出尺寸不同的组。
 * <p>{@code complete} 为假表示这一组不完整：超出格数上限，或邻格所在区块尚未加载而无法判定其是否同类。
 * 此时不绘制，也不据此丢弃已建好的画布。
 */
public record DisplayGroup(BlockPos origin, int width, int height, BitSet cells, boolean complete) {
	/** 单格组的格表；无世界、或方块不是显示单元时用。 */
	public static BitSet single() {
		var cells = new BitSet();
		cells.set(0);
		return cells;
	}
	/**
	 * 单格的分辨率，单位是画布像素。
	 * <p>须与瓷砖贴图同值：屏幕内容区在面上仅有 20 个贴图像素，画布更密亦会被采样丢弃。
	 */
	public static final int RESOLUTION = 32;
	/** 单轴的格数上限，超出时不绘制。 */
	public static final int MAX_TILES = 16;
	/** 边框在贴图上的像素宽；画布内容铺的是外接框去掉这一圈的那片。 */
	public static final int FRAME = Math.round(TileLogicDisplayBlock.INSET * RESOLUTION);
	/** 八向掩码的位序：0 右、1 右上、2 上、3 左上、4 左、5 左下、6 下、7 右下。 */
	private static final int[][] D8 = {{1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}};
	/** 漫开的四个正交方向，按面内「右」「下」两轴给出。 */
	private static final int[][] ORTHOGONAL = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
	/**
	 * 八向掩码到瓷砖号的查表，掩码位序见 {@link #connections}。
	 * <p>每行 16 项：行首即掩码的高半字节（位 4~7：左、左下、下、右下），
	 * 行内第 N 项即低半字节（位 0~3：右、右上、上、左上）为 N。
	 * 行尾 {@code 0xN0} 标注该行首项的掩码；内容相同的行标「同」，不再重复展开。
	 */
	private static final int[] TILES = {
		39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24, // 0x00
		38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25, // 0x10
		39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24, // 0x20 同 0x00
		38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25, // 0x30 同 0x10
		3, 4, 3, 4, 15, 40, 15, 20, 3, 4, 3, 4, 15, 40, 15, 20, // 0x40
		5, 28, 5, 28, 29, 10, 29, 23, 5, 28, 5, 28, 31, 11, 31, 32, // 0x50
		3, 4, 3, 4, 15, 40, 15, 20, 3, 4, 3, 4, 15, 40, 15, 20, // 0x60 同 0x40
		2, 30, 2, 30, 9, 46, 9, 22, 2, 30, 2, 30, 14, 44, 14, 6, // 0x70
		39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24, // 0x80 同 0x00
		38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25, // 0x90 同 0x10
		39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24, // 0xA0 同 0x00
		38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25, // 0xB0 同 0x10
		3, 0, 3, 0, 15, 42, 15, 12, 3, 0, 3, 0, 15, 42, 15, 12, // 0xC0
		5, 8, 5, 8, 29, 35, 29, 33, 5, 8, 5, 8, 31, 34, 31, 7, // 0xD0
		3, 0, 3, 0, 15, 42, 15, 12, 3, 0, 3, 0, 15, 42, 15, 12, // 0xE0 同 0xC0
		2, 1, 2, 1, 9, 45, 9, 19, 2, 1, 2, 1, 14, 18, 14, 13, // 0xF0
	};
	/**
	 * 瓷砖按 6/20/6 三等分的九块里哪些是边框，下标即瓷砖号。
	 * <p>位索引为 {@code j * 3 + i}（i 为列、j 为行，自上而下），1 表示该块是边框；
	 * 写成 {@code 0bXXX_YYY_ZZZ} 时 {@code ZZZ} 是顶行、{@code YYY} 是中行、{@code XXX} 是底行，
	 * 每行内自低位向高位对应自左向右的三列。
	 * <p>九块非全框即全透明，画布按这些块铺开即可与边框零重叠。
	 */
	private static final int[] REGIONS = {
		0b001_001_111, 0b000_000_111, 0b100_100_111, 0b101_101_111, //  0~ 3
		0b101_001_111, 0b101_100_111, 0b100_000_000, 0b001_000_000, //  4~ 7
		0b001_000_111, 0b100_100_101, 0b101_000_101, 0b101_000_100, //  8~11
		0b001_001_001, 0b000_000_000, 0b100_100_100, 0b101_101_101, // 12~15
		0b111_001_101, 0b111_100_101, 0b000_000_100, 0b000_000_001, // 16~19
		0b101_001_001, 0b111_000_100, 0b100_000_001, 0b101_000_001, // 20~23
		0b111_001_001, 0b111_000_000, 0b111_100_100, 0b111_101_101, // 24~27
		0b101_000_111, 0b101_100_101, 0b100_000_111, 0b101_100_100, // 28~31
		0b101_000_000, 0b001_000_001, 0b001_000_100, 0b001_000_101, // 32~35
		0b111_001_111, 0b111_000_111, 0b111_100_111, 0b111_101_111, // 36~39
		0b101_001_101, 0b111_000_101, 0b001_001_101, 0b111_000_001, // 40~43
		0b100_000_100, 0b000_000_101, 0b100_000_101,                // 44~46
	};
	/** @return 掩码对应的瓷砖号。 */
	public static int tile(int connections) {
		return TILES[connections & 0xFF];
	}
	/** @return 瓷砖的九块里第 (i, j) 块是否为边框。 */
	public static boolean framed(int tile, int i, int j) {
		return (REGIONS[tile] & 1 << j * 3 + i) != 0;
	}
	/** @return {@code pos} 所在的那一组；方块不是逻辑显示单元时按单格处理。 */
	public static DisplayGroup of(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TileLogicDisplayBlock)) return new DisplayGroup(pos, 1, 1, single(), true);
		var orientation = state.getValue(TileLogicDisplayBlock.ORIENTATION);
		var right = right(orientation);
		var down = down(orientation);
		var found = new ArrayList<BlockPos>();
		var pending = new ArrayDeque<BlockPos>();
		var minU = 0;
		var maxU = 0;
		var minV = 0;
		var maxV = 0;
		var complete = true;
		found.add(pos);
		pending.add(pos);
		while (!pending.isEmpty()) {
			var at = pending.poll();
			var du = offset(pos, at, right);
			var dv = offset(pos, at, down);
			for (var step : ORTHOGONAL) {
				var next = step[0] == 0 ? at.relative(down, step[1]) : at.relative(right, step[0]);
				if (found.contains(next)) continue;
				// 区块未加载时无法判定邻格是否同类，按可能相连处理，整组不完整；世界上下限之外则必然不是显示屏
				if (!level.isOutsideBuildHeight(next) && !level.isLoaded(next)) {
					complete = false;
					continue;
				}
				var nextState = level.getBlockState(next);
				if (!(nextState.getBlock() instanceof TileLogicDisplayBlock)
					|| nextState.getValue(TileLogicDisplayBlock.ORIENTATION) != orientation) continue;
				// 先确认同类再判外接框，否则相邻空格会被误判为超限
				var nextU = du + step[0];
				var nextV = dv + step[1];
				if (Math.max(maxU, nextU) - Math.min(minU, nextU) >= MAX_TILES
					|| Math.max(maxV, nextV) - Math.min(minV, nextV) >= MAX_TILES) {
					complete = false;
					continue;
				}
				found.add(next);
				pending.add(next);
				minU = Math.min(minU, nextU);
				maxU = Math.max(maxU, nextU);
				minV = Math.min(minV, nextV);
				maxV = Math.max(maxV, nextV);
			}
		}
		var width = maxU - minU + 1;
		var cells = new BitSet();
		for (var member : found) {
			var x = offset(pos, member, right) - minU;
			var y = offset(pos, member, down) - minV;
			cells.set(y * width + x);
		}
		return new DisplayGroup(pos.relative(right, minU).relative(down, minV), width, maxV - minV + 1, cells, complete);
	}
	/** @return 面内向右的方向：屏幕「上」与法向的叉积。 */
	public static Direction right(FrontAndTop orientation) {
		var normal = orientation.top().getNormal().cross(orientation.front().getNormal());
		return Direction.getNearest(normal.getX(), normal.getY(), normal.getZ());
	}
	/** @return 面内向下的方向，即屏幕「上」的反向。 */
	public static Direction down(FrontAndTop orientation) {
		return orientation.top().getOpposite();
	}
	/** @return 面内摆向的级数（0~3），用于瓷砖在格内的旋转。 */
	public static int rotation(FrontAndTop orientation) {
		var front = orientation.front();
		var down = down(orientation);
		for (var rotation = 0; rotation < 4; rotation++)
			if (rotate(baseDown(front), front, rotation) == down) return rotation;
		return 0;
	}
	/** @return 该法向下摆向为 0 时屏幕「下」的指向。 */
	private static Direction baseDown(Direction front) {
		return switch (front) {
			case UP -> Direction.SOUTH;
			case DOWN -> Direction.NORTH;
			default -> Direction.DOWN;
		};
	}
	/** @return {@code to} 相对 {@code from} 在 {@code axis} 上偏移的格数。 */
	public static int offset(BlockPos from, BlockPos to, Direction axis) {
		return (to.getX() - from.getX()) * axis.getStepX()
			+ (to.getY() - from.getY()) * axis.getStepY()
			+ (to.getZ() - from.getZ()) * axis.getStepZ();
	}
	/** @return {@code d} 绕 {@code axis} 按右手方向转 {@code rotation} 级得到的轴向。 */
	private static Direction rotate(Direction d, Direction axis, int rotation) {
		var result = d;
		for (var i = 0; i < rotation; i++) {
			var v = axis.getNormal().cross(result.getNormal());
			result = Direction.getNearest(v.getX(), v.getY(), v.getZ());
		}
		return result;
	}
	/** @return 屏幕周围八向各有无同朝向、同摆向的同类，按 {@link #D8} 的位序打包。 */
	public static int connections(BlockGetter level, BlockPos pos, FrontAndTop orientation) {
		var right = right(orientation);
		var down = down(orientation);
		var bits = 0;
		for (var i = 0; i < D8.length; i++) {
			var offset = pos.relative(right, D8[i][0]).relative(down, D8[i][1]);
			var state = level.getBlockState(offset);
			if (state.getBlock() instanceof TileLogicDisplayBlock
				&& state.getValue(TileLogicDisplayBlock.ORIENTATION) == orientation) bits |= 1 << i;
		}
		return bits;
	}
	/** @return 外接框内 (x, y) 一格（自 origin 数起）是否为成员。 */
	public boolean contains(int x, int y) {
		return x >= 0 && y >= 0 && x < width && y < height && cells.get(y * width + x);
	}
	/**
	 * @param x 自 origin 算起的列号
	 * @param y 自 origin 算起的行号
	 * @return 该格的坐标
	 */
	public BlockPos at(Direction u, Direction v, int x, int y) {
		return origin.relative(u, x).relative(v, y);
	}
	/** @return 整块画布的像素宽。 */
	public int canvasWidth() {
		return width * RESOLUTION - FRAME * 2;
	}
	/** @return 整块画布的像素高。 */
	public int canvasHeight() {
		return height * RESOLUTION - FRAME * 2;
	}
}