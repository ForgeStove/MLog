package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.RenderHighlightEvent.Block;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 方块轮廓，根据<b>模型的面</b>绘制。
 * <p>透明处理：把 BakedQuad 的 atlas UV 归一化到 sprite 局部 [0,1] 后，沿每条边按纹理 alpha 采样，只绘制可见区段。
 */
@OnlyIn(Dist.CLIENT)
public final class ModelOutline {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float RED = 0F, GREEN = 0F, BLUE = 0F, ALPHA = 0.4F;
	private static final RandomSource RANDOM = RandomSource.create(42L);
	/** 采样密度下限。按贴图像素自适应加密。 */
	private static final int MIN_EDGE_SAMPLES = 16;
	/** alpha 阈值（0~255）。低于它的像素视作不可见，用于跳过抗锯齿边缘。 */
	private static final int ALPHA_THRESHOLD = 0;
	private static final Map<TextureAtlasSprite, AlphaMap> ALPHA_CACHE = new WeakHashMap<>();
	private static final Map<BakedQuad, List<Segment>> SEGMENT_CACHE = new WeakHashMap<>();
	private static final Map<BlockState, List<BakedQuad>> QUAD_CACHE = new WeakHashMap<>();
	/**
	 * 资源重载会把图谱与模型整个换一批，缓存里那些精灵和四边形还指着上一批对象，它们底下的图已经释放。
	 * 不清的话下一次画描边就是拿旧对象去读野内存，透明与否全看运气。注册见 {@code MLogClient}。
	 */
	public static void reload(ResourceManager ignored) {
		QUAD_CACHE.clear();
		SEGMENT_CACHE.clear();
		ALPHA_CACHE.clear();
	}
	public static void onRenderHighlight(Block event) {
		var level = mc.level;
		if (level == null) return;
		var pos = event.getTarget().getBlockPos();
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MicroProcessorBlock)) return;
		var quads = quads(state);
		if (quads.isEmpty()) return;
		event.setCanceled(true);
		var pose = event.getPoseStack().last();
		var camera = event.getCamera().getPosition();
		var consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
		var ox = pos.getX() - (float) camera.x;
		var oy = pos.getY() - (float) camera.y;
		var oz = pos.getZ() - (float) camera.z;
		for (var quad : quads)
			for (var seg : segmentsOf(quad)) {
				vertex(consumer, pose, ox + seg.x0, oy + seg.y0, oz + seg.z0);
				vertex(consumer, pose, ox + seg.x1, oy + seg.y1, oz + seg.z1);
			}
	}
	// ---------- 缓存层 ----------
	private static List<BakedQuad> quads(BlockState state) {
		return QUAD_CACHE.computeIfAbsent(
			state, s -> {
				var model = mc.getBlockRenderer().getBlockModel(s);
				var list = new ArrayList<>(model.getQuads(s, null, RANDOM, ModelData.EMPTY, null));
				for (var side : Direction.values())
					list.addAll(model.getQuads(s, side, RANDOM, ModelData.EMPTY, null));
				return List.copyOf(list);
			}
		);
	}
	private static List<Segment> segmentsOf(BakedQuad quad) {
		return SEGMENT_CACHE.computeIfAbsent(quad, ModelOutline::buildSegments);
	}
	// ---------- 每帧廉价部分 ----------
	private static void vertex(VertexConsumer consumer, Pose pose, float x, float y, float z) {
		consumer.addVertex(pose, x, y, z).setColor(RED, GREEN, BLUE, ALPHA).setNormal(pose, 0F, 1F, 0F);
	}
	// ---------- 预计算 ----------
	private static List<Segment> buildSegments(BakedQuad quad) {
		var verts = quad.getVertices();
		var stride = verts.length / 4;
		var sprite = quad.getSprite();
		var alpha = alphaOf(sprite);
		if (!alpha.hasTransparency()) return fullEdges(verts, stride);
		return sampleEdges(sprite, verts, stride, alpha);
	}
	@SuppressWarnings("resource")
	private static AlphaMap alphaOf(TextureAtlasSprite sprite) {
		return ALPHA_CACHE.computeIfAbsent(
			sprite, s -> {
				// 尺寸取帧的，不取原始图的：动态贴图的原始图是把所有帧竖着拼起来的长条，
				// 拿它当尺寸的话 v 会被摊到所有帧上，采到的就不是画出来的那一帧
				var contents = s.contents();
				var w = contents.width();
				var h = contents.height();
				var data = new byte[w * h];
				var transparent = false;
				for (var y = 0; y < h; y++)
					for (var x = 0; x < w; x++) {
						// 取第 0 帧；这个重载会把帧号折成原始图上的偏移
						var a = s.getPixelRGBA(0, x, y) >>> 24;
						data[y * w + x] = (byte) a;
						if (a == ALPHA_THRESHOLD) transparent = true;
					}
				LOGGER.info(
					"Sprite {}x{} hasTransparency={} u0={} u1={} v0={} v1={}",
					w,
					h,
					transparent,
					s.getU0(),
					s.getU1(),
					s.getV0(),
					s.getV1()
				);
				return new AlphaMap(data, w, h, transparent);
			}
		);
	}
	private static List<Segment> fullEdges(int[] verts, int stride) {
		var out = new ArrayList<Segment>(4);
		for (var i = 0; i < 4; i++) {
			var v0 = i * stride;
			var v1 = (i + 1) % 4 * stride;
			out.add(new Segment(
				Float.intBitsToFloat(verts[v0]),
				Float.intBitsToFloat(verts[v0 + 1]),
				Float.intBitsToFloat(verts[v0 + 2]),
				Float.intBitsToFloat(verts[v1]),
				Float.intBitsToFloat(verts[v1 + 1]),
				Float.intBitsToFloat(verts[v1 + 2])
			));
		}
		return out;
	}
	/**
	 * 沿四条边采样。把 BakedQuad 的 atlas UV 归一化到 sprite 局部 [0,1]，再按 alpha 抽出可见区段。
	 * <p>不翻 v：MC 的 {@code NativeImage} 自上而下，和 UV 的 v 同向，没有需要翻的情形。
	 */
	private static List<Segment> sampleEdges(TextureAtlasSprite sprite, int[] verts, int stride, AlphaMap alpha) {
		var out = new ArrayList<Segment>(8);
		var uMin = sprite.getU0();
		var uSpan = sprite.getU1() - uMin;
		var vMin = sprite.getV0();
		var vSpan = sprite.getV1() - vMin;
		if (uSpan == 0F) uSpan = 1F;
		if (vSpan == 0F) vSpan = 1F;
		for (var i = 0; i < 4; i++) {
			var v0 = i * stride;
			var v1 = (i + 1) % 4 * stride;
			var px0 = Float.intBitsToFloat(verts[v0]);
			var py0 = Float.intBitsToFloat(verts[v0 + 1]);
			var pz0 = Float.intBitsToFloat(verts[v0 + 2]);
			var px1 = Float.intBitsToFloat(verts[v1]);
			var py1 = Float.intBitsToFloat(verts[v1 + 1]);
			var pz1 = Float.intBitsToFloat(verts[v1 + 2]);
			// atlas UV → sprite 局部 [0,1]
			var u0 = (Float.intBitsToFloat(verts[v0 + 4]) - uMin) / uSpan;
			var vv0 = (Float.intBitsToFloat(verts[v0 + 5]) - vMin) / vSpan;
			var u1 = (Float.intBitsToFloat(verts[v1 + 4]) - uMin) / uSpan;
			var vv1 = (Float.intBitsToFloat(verts[v1 + 5]) - vMin) / vSpan;
			var du = Math.abs((int) (u1 * alpha.width()) - (int) (u0 * alpha.width()));
			var dv = Math.abs((int) (vv1 * alpha.height()) - (int) (vv0 * alpha.height()));
			var samples = Math.max(MIN_EDGE_SAMPLES, Math.max(du, dv) * 2);
			var start = -1;
			for (var s = 0; s <= samples; s++) {
				var t = (float) s / samples;
				var visible = alpha.isOpaqueInset(lerp(u0, u1, t), lerp(vv0, vv1, t));
				if (visible && start < 0) start = s;
				else if (!visible && start >= 0) {
					addSegment(out, px0, py0, pz0, px1, py1, pz1, (float) start / samples, (float) (s - 1) / samples);
					start = -1;
				}
			}
			if (start >= 0) addSegment(out, px0, py0, pz0, px1, py1, pz1, (float) start / samples, 1F);
		}
		return out;
	}
	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
	private static void addSegment(List<Segment> out, float x0, float y0, float z0, float x1, float y1, float z1, float t0, float t1) {
		out.add(new Segment(lerp(x0, x1, t0), lerp(y0, y1, t0), lerp(z0, z1, t0), lerp(x0, x1, t1), lerp(y0, y1, t1), lerp(z0, z1, t1)));
	}
	private record AlphaMap(byte[] data, int width, int height, boolean hasTransparency) {
		boolean isOpaqueInset(float u, float v) {
			var x = (int) (u * width);
			var y = (int) (v * height);
			if (x < 0 || y < 0 || x >= width || y >= height) return false;
			var cx = u < 0.5F ? x + 1 : x - 1;
			var cy = v < 0.5F ? y + 1 : y - 1;
			if (cx < 0 || cy < 0 || cx >= width || cy >= height) return false;
			return (data[y * width + x] & 0xFF) > ALPHA_THRESHOLD && (data[cy * width + cx] & 0xFF) > ALPHA_THRESHOLD;
		}
	}
	private record Segment(float x0, float y0, float z0, float x1, float y1, float z1) {}
}