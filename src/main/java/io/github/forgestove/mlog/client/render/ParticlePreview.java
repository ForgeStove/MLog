package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 在界面中预览粒子：直接驱动粒子顶点，不经过世界粒子引擎。 */
public class ParticlePreview {
	/** 预览粒子统一生成的高度；发射型粒子产生的子粒子会因离视点过远而不显示。 */
	private static final double ORIGIN_Y = 1000;
	private static final ResourceLocation PARTICLE_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
	private static final Camera CAMERA = new Camera();
	/** 已生成过但取不到提供者的类型，后续直接跳过。 */
	private static final Set<ResourceLocation> UNAVAILABLE = new HashSet<>();
	/** 每类粒子测得的完整活动范围，作为固定缩放与取景依据。 */
	private static final Map<ResourceLocation, Bounds> MEASURED = new HashMap<>();
	/** 测量上限 5 秒，覆盖绝大多数粒子的整个生命周期。 */
	private static final int MEASURE_TICKS = 100;
	private static @Nullable Map<ResourceLocation, ParticleProvider<?>> providers;
	public static @Nullable ResourceLocation idOf(String option) {
		return ResourceLocation.tryParse(option.startsWith("@") ? option.substring(1) : option);
	}
	public static @Nullable Particle create(String option) {
		var id = idOf(option);
		if (id == null || UNAVAILABLE.contains(id) || mc.level == null) return null;
		if (!(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType type)) return null;
		var provider = providers().get(id);
		if (provider == null) {
			UNAVAILABLE.add(id);
			return null;
		}
		try {
			@SuppressWarnings("unchecked") var typed = (ParticleProvider<SimpleParticleType>) provider;
			var particle = typed.createParticle(type, mc.level, 0, ORIGIN_Y, 0, 0, 0, 0);
			if (particle == null) UNAVAILABLE.add(id);
			return particle;
		} catch (RuntimeException ignored) {
			UNAVAILABLE.add(id);
			return null;
		}
	}
	/** 把一次渲染写入的顶点收集起来；自定义渲染类型直接跳过。 */
	public static Frame capture(Particle particle, float partialTick) {
		var type = particle.getRenderType();
		if (type == ParticleRenderType.NO_RENDER || type == ParticleRenderType.CUSTOM) return Frame.EMPTY;
		var capture = new Capture();
		particle.render(capture, CAMERA, partialTick);
		capture.finish();
		var atlas = type == ParticleRenderType.TERRAIN_SHEET ? InventoryMenu.BLOCK_ATLAS : PARTICLE_ATLAS;
		return new Frame(List.copyOf(capture.vertices), atlas);
	}
	/** 完整播放一个样本，测出包含尺寸与移动的包围盒；按类型缓存。 */
	public static @Nullable Bounds measure(String option) {
		var id = idOf(option);
		if (id == null) return null;
		var cached = MEASURED.get(id);
		if (cached != null) return cached;
		var particle = create(option);
		if (particle == null) return null;
		var type = particle.getRenderType();
		if (type == ParticleRenderType.NO_RENDER || type == ParticleRenderType.CUSTOM) return null;
		var capture = new ExtentCapture();
		for (var i = 0; i < MEASURE_TICKS && particle.isAlive(); i++) {
			particle.render(capture, CAMERA, 0F);
			try {
				particle.tick();
			} catch (RuntimeException ignored) {
				break;
			}
		}
		var bounds = capture.bounds();
		if (bounds != null) MEASURED.put(id, bounds);
		return bounds;
	}
	public static @Nullable Bounds bounds(Frame frame) {
		if (frame.vertices.size() < 4) return null;
		var minX = Float.MAX_VALUE;
		var maxX = -Float.MAX_VALUE;
		var minY = Float.MAX_VALUE;
		var maxY = -Float.MAX_VALUE;
		for (var vertex : frame.vertices) {
			minX = Math.min(minX, vertex.x);
			maxX = Math.max(maxX, vertex.x);
			minY = Math.min(minY, vertex.y);
			maxY = Math.max(maxY, vertex.y);
		}
		return new Bounds(minX, maxX, minY, maxY);
	}
	/** 按给定的界面原点与世界坐标比例绘制顶点。 */
	public static void draw(GuiGraphics gui, Frame frame, float originX, float originY, float scale) {
		var count = frame.vertices.size() / 4 * 4;
		if (count == 0) return;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, frame.atlas);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		var buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		var matrix = gui.pose().last().pose();
		for (var i = 0; i < count; i++) {
			var vertex = frame.vertices.get(i);
			buffer.addVertex(matrix, originX + vertex.x * scale, originY - vertex.y * scale, 0F)
				.setUv(vertex.u, vertex.v)
				.setColor(vertex.argb);
		}
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}
	private static Map<ResourceLocation, ParticleProvider<?>> providers() {
		if (providers != null) return providers;
		try {
			var field = ParticleEngine.class.getDeclaredField("providers");
			field.setAccessible(true);
			@SuppressWarnings("unchecked") var map = (Map<ResourceLocation, ParticleProvider<?>>) field.get(mc.particleEngine);
			return providers = map;
		} catch (ReflectiveOperationException ignored) {
			return providers = Map.of();
		}
	}
	/** 当前帧的包围盒，坐标为世界单位。 */
	public record Bounds(float minX, float maxX, float minY, float maxY) {
		public float centerX() {
			return (minX + maxX) / 2F;
		}
		public float centerY() {
			return (minY + maxY) / 2F;
		}
		public float spanX() {
			return maxX - minX;
		}
		public float spanY() {
			return maxY - minY;
		}
	}
	public record Vertex(float x, float y, float u, float v, int argb) {}
	public record Frame(List<Vertex> vertices, ResourceLocation atlas) {
		public static final Frame EMPTY = new Frame(List.of(), PARTICLE_ATLAS);
	}
	/** 收集粒子写入的顶点，颜色与 UV 原样保留。 */
	private static final class Capture implements VertexConsumer {
		private final List<Vertex> vertices = new ArrayList<>();
		private float x, y, u, v;
		private int argb = -1;
		private boolean active;
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			flush();
			this.x = x;
			this.y = y;
			active = true;
			return this;
		}
		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			argb = alpha << 24 | red << 16 | green << 8 | blue;
			return this;
		}
		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}
		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}
		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}
		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}
		private void flush() {
			if (!active) return;
			vertices.add(new Vertex(x, y, u, v, argb));
			u = v = 0F;
			argb = -1;
			active = false;
		}
		private void finish() {
			flush();
		}
	}
	/** 只记录顶点范围的消费者，用于测量整体活动范围。 */
	private static final class ExtentCapture implements VertexConsumer {
		private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			return this;
		}
		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}
		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}
		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}
		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}
		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}
		private @Nullable Bounds bounds() {
			return minX > maxX ? null : new Bounds(minX, maxX, minY, maxY);
		}
	}
}
