package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 逻辑显示单元的渲染。
 * <p>每格各自绘制本格那一块画布，不设主方块：整组形状变化或仅加载部分时，各格仍可独立绘制。
 * <p>边框由模型绘制并压在画布之上：画布铺满整格，边框在拐角处的斜接会伸进格内，靠深度偏置定胜负。
 */
@OnlyIn(Dist.CLIENT)
public class DisplayRenderer implements BlockEntityRenderer<TileLogicDisplayBlockEntity> {
	/**
	 * 同时保留的画布上限，超出后淘汰最久未用的一份并归还显存；淘汰即内容丢失。
	 */
	private static final int MAX_BUFFERS = 1024;
	/** 方块中心到面中心的距离。 */
	private static final float HALF = 0.5F;
	/** 画布的存放位置；含维度，因本表为静态、不同维度的同一坐标会重合。 */
	private record Key(ResourceKey<Level> dimension, BlockPos origin) {}
	/** 一份画布及其服务的格：坐标用于辨认前身与判断是否已全被拆除，高度用于折算内容位移。 */
	private record Canvas(DisplayBuffer buffer, BlockPos origin, int height, List<BlockPos> cells) {}
	/** 旧画布的内容在新画布上要平移的像素数。 */
	private record Shift(int x, int y) {}
	private static final Map<Key, Canvas> BUFFERS = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Canvas> eldest) {
			if (size() <= MAX_BUFFERS) return false;
			eldest.getValue().buffer().close();
			return true;
		}
	};
	@Override
	public void render(TileLogicDisplayBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		var level = be.getLevel();
		if (level == null) return;
		var pos = be.getBlockPos();
		var facing = be.getBlockState().getValue(TileLogicDisplayBlock.FACING);
		int rotation = be.getBlockState().getValue(TileLogicDisplayBlock.ROTATION);
		var u = DisplayGroup.right(facing, rotation);
		var v = DisplayGroup.down(facing, rotation);
		var group = DisplayGroup.of(level, pos);
		// 超出外接框上限时不绘制：各格算出的组已不一致
		if (!group.complete()) return;
		var x = DisplayGroup.offset(group.origin(), pos, u);
		var y = DisplayGroup.offset(group.origin(), pos, v);
		// 本格的瓷砖决定哪些位置让给边框
		var tile = DisplayGroup.tile(DisplayGroup.connections(level, pos, facing, rotation));
		// 取画布会切换离屏目标，须在绘制之前完成
		var canvas = buffer(level, group, u, v);
		var renderType = canvas.renderType();
		screen(buffers.getBuffer(renderType), pose, new Face(u, v, facing.getNormal()), group, x, y, tile);
		// 该批次不在固定缓冲表内，须立即提交，否则会绘于半透明方块之上
		if (buffers instanceof BufferSource source) source.endBatch(renderType);
	}
	/**
	 * @return 整组共用的画布，按外接框角落存放；同帧仅首个渲染的成员格取得到积压
	 */
	private static DisplayBuffer buffer(Level level, DisplayGroup group, Direction u, Direction v) {
		var key = new Key(level.dimension(), group.origin());
		var canvas = BUFFERS.get(key);
		var cells = cells(group, u, v);
		if (canvas == null) {
			// 换了外接框：按位移将旧画布内容搬入
			var previous = previous(level, group, u, v);
			var created = new DisplayBuffer(group.canvasWidth(), group.canvasHeight());
			if (previous != null) {
				var shift = shift(group, previous, u, v);
				created.copyFrom(previous.buffer(), shift.x(), shift.y());
			}
			canvas = new Canvas(created, group.origin(), group.height(), cells);
		} else {
			// 角落未变而尺寸变化：就地改建，替换缓冲会摘除贴图名
			if (!canvas.buffer().matches(group.canvasWidth(), group.canvasHeight())) {
				var shift = shift(group, canvas, u, v);
				canvas.buffer().resize(group.canvasWidth(), group.canvasHeight(), shift.x(), shift.y());
			}
			canvas = new Canvas(canvas.buffer(), group.origin(), group.height(), cells);
		}
		BUFFERS.put(key, canvas);
		var commands = drain(level, group, u, v);
		if (!commands.isEmpty()) canvas.buffer().apply(commands);
		return canvas.buffer();
	}
	/**
	 * @return 旧画布内容落入新画布所需的像素位移；画布的 y 与 {@code v} 反向，故须计入高度差
	 */
	private static Shift shift(DisplayGroup group, Canvas previous, Direction u, Direction v) {
		return new Shift(
			DisplayGroup.offset(group.origin(), previous.origin(), u) * DisplayGroup.RESOLUTION,
			(group.height() - previous.height() - DisplayGroup.offset(group.origin(), previous.origin(), v)) * DisplayGroup.RESOLUTION
		);
	}
	/** @return 本组各成员格的坐标。 */
	private static List<BlockPos> cells(DisplayGroup group, Direction u, Direction v) {
		var cells = new ArrayList<BlockPos>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y)) cells.add(group.at(u, v, x, y));
		return cells;
	}
	/**
	 * @return 可能为本组前身的画布；迭代顺序由最久未用到最近用过，故取最后一个相符者
	 */
	private static @Nullable Canvas previous(Level level, DisplayGroup group, Direction u, Direction v) {
		Canvas found = null;
		for (var entry : BUFFERS.entrySet()) {
			if (!entry.getKey().dimension().equals(level.dimension())) continue;
			if (overlaps(entry.getValue(), group, u, v)) found = entry.getValue();
		}
		return found;
	}
	/**
	 * @return 本画布服务的格与本组是否存在重合的成员格；仅比较外接框会与相邻的异朝向组相混
	 */
	private static boolean overlaps(Canvas canvas, DisplayGroup group, Direction u, Direction v) {
		for (var cell : canvas.cells())
			if (group.contains(DisplayGroup.offset(group.origin(), cell, u), DisplayGroup.offset(group.origin(), cell, v))) return true;
		return false;
	}
	/**
	 * 某一格被真正拆除时丢弃其所属画布；组内尚存成员格则不丢，区块卸载不调
	 */
	public static void invalidate(Level level, BlockPos pos) {
		var iterator = BUFFERS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (!entry.getKey().dimension().equals(level.dimension())) continue;
			var canvas = entry.getValue();
			if (!canvas.cells().contains(pos)) continue;
			if (canvas.cells().stream().anyMatch(cell -> alive(level, cell))) continue;
			iterator.remove();
			canvas.buffer().close();
		}
	}
	/** @return 这一格是否仍有屏；未加载的格按存在计，区块卸载不得视为拆除。 */
	private static boolean alive(Level level, BlockPos pos) {
		return !level.isLoaded(pos) || level.getBlockEntity(pos) instanceof TileLogicDisplayBlockEntity;
	}
	/**
	 * 本格那一块画布。
	 * <p>局部坐标以本格面心为原点，一格为一单位。画布只铺瓷砖上透明的那几块，边框占着的让开，
	 * 两者零重叠，不必与深度打交道。
	 * <p>画布的 y 朝上、贴图的 v 也朝上，两者同向；但这里的 {@code y} 是自内容上沿往下数的行号，
	 * 故 UV 按「离上沿多远」折算。
	 */
	private static void screen(VertexConsumer consumer, PoseStack pose, Face face, DisplayGroup group, int x, int y, int tile) {
		var resolution = DisplayGroup.RESOLUTION;
		var frame = DisplayGroup.FRAME;
		var canvasWidth = group.canvasWidth();
		var canvasHeight = group.canvasHeight();
		float[] cut = {0F, TileLogicDisplayBlock.INSET, 1F - TileLogicDisplayBlock.INSET, 1F};
		for (var i = 0; i < 3; i++)
			for (var j = 0; j < 3; j++) {
				if (DisplayGroup.framed(tile, i, j)) continue;
				var minU = -HALF + cut[i];
				var minV = -HALF + cut[j];
				var maxU = -HALF + cut[i + 1];
				var maxV = -HALF + cut[j + 1];
				// 画布内容按外接框去掉边框那一圈铺开，采到的正是瓷砖上透明那几块
				var u0 = (x * resolution + cut[i] * resolution - frame) / (float) canvasWidth;
				var u1 = (x * resolution + cut[i + 1] * resolution - frame) / (float) canvasWidth;
				var v1 = 1F - (y * resolution + cut[j] * resolution - frame) / (float) canvasHeight;
				var v0 = 1F - (y * resolution + cut[j + 1] * resolution - frame) / (float) canvasHeight;
				face.vertex(consumer, pose, minU, minV, u0, v1);
				face.vertex(consumer, pose, maxU, minV, u1, v1);
				face.vertex(consumer, pose, maxU, maxV, u1, v0);
				face.vertex(consumer, pose, minU, maxV, u0, v0);
			}
	}
	/** @return 取走组内各成员格积压的命令，按扫描顺序拼接。 */
	private static List<DrawCmd> drain(Level level, DisplayGroup group, Direction u, Direction v) {
		var commands = new ArrayList<DrawCmd>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y) && level.getBlockEntity(group.at(u, v, x, y)) instanceof TileLogicDisplayBlockEntity be)
					commands.addAll(be.drain());
		return commands;
	}
	/** 本格朝向面的坐标系：{@code u} 为面内的右、{@code v} 为面内的下、{@code normal} 为外法向。 */
	private record Face(Direction u, Direction v, Vec3i normal) {
		/** 顶点色与亮度均须给出，供光影包的兜底 program 读取。 */
		private void vertex(VertexConsumer consumer, PoseStack pose, float du, float dv, float textureU, float textureV) {
			var x = HALF + normal.getX() * HALF + u.getStepX() * du + v.getStepX() * dv;
			var y = HALF + normal.getY() * HALF + u.getStepY() * du + v.getStepY() * dv;
			var z = HALF + normal.getZ() * HALF + u.getStepZ() * du + v.getStepZ() * dv;
			consumer.addVertex(pose.last(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(textureU, textureV)
				.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
		}
	}
}
