package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.*;

import java.util.*;
/**
 * 逻辑显示单元的渲染。
 * <p>每格各自绘制本格那一块画布，不设主方块：整组形状变化或仅加载部分时，各格仍可独立绘制。
 * <p>边框由模型绘制并压在画布之上：画布铺满整格，边框在拐角处的斜接会伸进格内，靠深度偏置定胜负。
 */
@OnlyIn(Dist.CLIENT)
public class DisplayRenderer implements BlockEntityRenderer<TileLogicDisplayBlockEntity> {
	/** 同时保留的画布上限，超出后淘汰最久未用的那份并归还显存。 */
	private static final int MAX_BUFFERS = 64;
	/** 方块中心到面中心的距离。 */
	private static final float HALF = 0.5F;
	private static final Map<BlockPos, DisplayBuffer> BUFFERS = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlockPos, DisplayBuffer> eldest) {
			if (size() <= MAX_BUFFERS) return false;
			eldest.getValue().close();
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
	 * @return 整组共用的画布，按外接框角落存放
	 * 	<p>命令按扫描顺序拼自各成员格，不集中于某一格，整组形状变化后命令仍留在原处。
	 */
	private static DisplayBuffer buffer(Level level, DisplayGroup group, Direction u, Direction v) {
		var revision = 1;
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y) && level.getBlockEntity(group.at(u, v, x, y)) instanceof TileLogicDisplayBlockEntity display)
					revision = revision * 31 + display.getRevision();
		var existing = BUFFERS.get(group.origin());
		if (existing == null) {
			existing = new DisplayBuffer(group.canvasWidth(), group.canvasHeight());
			BUFFERS.put(group.origin(), existing);
		}
		// 尺寸变化就地改建：替换缓冲会摘掉贴图名
		existing.resize(group.canvasWidth(), group.canvasHeight());
		if (existing.stale(revision)) existing.render(commands(level, group, u, v), revision);
		return existing;
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
	/** @return 组内各成员格的命令按扫描顺序拼接的结果。 */
	private static List<DrawCmd> commands(Level level, DisplayGroup group, Direction u, Direction v) {
		var commands = new ArrayList<DrawCmd>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y) && level.getBlockEntity(group.at(u, v, x, y)) instanceof TileLogicDisplayBlockEntity be)
					commands.addAll(be.getCommands());
		return commands;
	}
	/** 本格朝向面的坐标系：{@code u} 为面内的右、{@code v} 为面内的下、{@code normal} 为外法向。 */
	private record Face(Direction u, Direction v, Vec3i normal) {
		private void vertex(VertexConsumer consumer, PoseStack pose, float du, float dv, float textureU, float textureV) {
			var x = HALF + normal.getX() * HALF + u.getStepX() * du + v.getStepX() * dv;
			var y = HALF + normal.getY() * HALF + u.getStepY() * du + v.getStepY() * dv;
			var z = HALF + normal.getZ() * HALF + u.getStepZ() * du + v.getStepZ() * dv;
			consumer.addVertex(pose.last(), x, y, z).setUv(textureU, textureV);
		}
	}
}
