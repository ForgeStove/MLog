package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 方块的粗描边。
 * <p>原版 {@code RenderType.lines()} 在核心渲染管线下线宽恒为 1 像素，加粗只能以面拼出：
 * 把框的十二条棱各展开成一个 {@code 长 × 线宽 × 线宽} 的长方体，
 * 线宽即成为可任意给定的参数。此处省去缓冲池与法线开关，只保留画框所需的部分。
 */
@OnlyIn(Dist.CLIENT)
public final class OutlineRenderer {
	/** 描边用的渲染类型：无纹理的实心面，正反面都画。 */
	private static final RenderType OUTLINE = RenderTypes.OUTLINE;
	/**
	 * 范围线框专用的渲染类型：与 {@link #OUTLINE} 同一套，深度照测，但不写深度。
	 * <p>测深度：范围框会正常被地形、方块遮挡，不会整体盖在画面上。
	 * <p>不写深度：两层贴在同一条棱上，写深度会挡住其中的细层。层次由 {@link #LAYER_BIAS} 处理。
	 */
	private static final RenderType RANGE = RenderTypes.RANGE;
	/**
	 * 平面矩形用的渲染类型。
	 * <p>不能用 {@link #OUTLINE}：那是实体着色器，顶点含方向光，颜色会被压暗，
	 * 画在文字下方时深浅可见。该类型只取位置与颜色，与文字的着色器口径一致。
	 */
	private static final RenderType RECT = RenderTypes.RECT;
	/**
	 * 世界中需要穿透方块的平面矩形（链接名下方的下划线）。
	 * <p>不测深度、不写深度：名字本身走 {@code DisplayMode.SEE_THROUGH}，该线须与之同口径，
	 * 否则文字浮在方块上、线却被方块深度遮挡。
	 */
	private static final RenderType SEE_THROUGH = RenderTypes.SEE_THROUGH;
	/**
	 * 范围线框那圈描边的宽度：外面粗灰、里面细主色。
	 * <p>宽度比取 3 : 1。
	 */
	private static final float OUTLINE_W = 3 / 16F, LINE_W = 1 / 16F;
	/**
	 * 主色层沿视线朝相机偏移的距离，用于压住灰色描边。
	 * <p>两层互相嵌套：粗描边 3/16、主色 1/16，主色完全嵌于粗描边内，深度上始终相差一截，
	 * 层次原本只由「不写深度」这条 GL 状态决定。该状态会被光影模组接管：Iris 的
	 * {@code DepthColorStorage} 在锁定期间会延后 {@code depthMask} 调用，甚至直接丢弃。
	 * 沿视线偏移则屏幕位置不变、深度上前移，因此不依赖任何 GL 状态。
	 * <p>取 1/8 格：两层表面沿任意视线的最大间距为半宽之差乘 √3（斜视时），1/16 × √3 ≈ 0.108，留有少量余量。
	 */
	private static final float LAYER_BIAS = 1 / 8F;
	/**
	 * 给一个方块体积描边。
	 *
	 * @param camera 相机位置，顶点坐标需减去它——此处的 pose 只含相机的旋转
	 * @param width  线宽，单位是格（十六像素一格）
	 */
	public static void renderBox(PoseStack pose, Vec3 camera, AABB box, float width, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		boxEdges(buffers.getBuffer(OUTLINE), pose.last(), camera, box, width, color);
		buffers.endBatch(OUTLINE);
	}
	/** 把一个方块体积的十二条棱写进 {@code consumer}，每条棱都是一根有截面的长方体。 */
	private static void boxEdges(VertexConsumer consumer, Pose pose, Vec3 camera, AABB box, float width, int color) {
		boxEdges(consumer, pose, camera, box, width, color, 0F);
	}
	/** @param bias 每个顶点沿视线朝相机挪的距离，用来让后画的那层压在先画的上面，见 {@link #LAYER_BIAS} */
	private static void boxEdges(VertexConsumer consumer, Pose pose, Vec3 camera, AABB box, float width, int color, float bias) {
		// 顶点按世界坐标给出，减去相机后才落入 pose 所在的坐标系
		var minX = (float) (box.minX - camera.x);
		var minY = (float) (box.minY - camera.y);
		var minZ = (float) (box.minZ - camera.z);
		var maxX = (float) (box.maxX - camera.x);
		var maxY = (float) (box.maxY - camera.y);
		var maxZ = (float) (box.maxZ - camera.z);
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		// 十二条棱，每条由两个端点确定
		edge(pose, consumer, minX, minY, minZ, maxX, minY, minZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, maxY, minZ, maxX, maxY, minZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, maxY, maxZ, maxX, maxY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, minY, minZ, minX, maxY, minZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, minY, maxZ, minX, maxY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, minY, minZ, minX, minY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, width, red, green, blue, alpha, bias);
		edge(pose, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, width, red, green, blue, alpha, bias);
	}
	/** 把一条棱画成有截面的长方体。 */
	private static void edge(
		Pose pose,
		VertexConsumer consumer,
		float x0,
		float y0,
		float z0,
		float x1,
		float y1,
		float z1,
		float width,
		float red,
		float green,
		float blue,
		float alpha,
		float bias
	) {
		var half = width / 2;
		var minX = Math.min(x0, x1) - half;
		var minY = Math.min(y0, y1) - half;
		var minZ = Math.min(z0, z1) - half;
		var maxX = Math.max(x0, x1) + half;
		var maxY = Math.max(y0, y1) + half;
		var maxZ = Math.max(z0, z1) + half;
		box(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, bias);
	}
	/** 画一个实心长方体，六个面。法线统一朝上，各面亮度才一致。 */
	private static void box(
		Pose pose,
		VertexConsumer consumer,
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ,
		float red,
		float green,
		float blue,
		float alpha,
		float bias
	) {
		quad(pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha, bias);
		quad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha, bias);
		quad(pose, consumer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, red, green, blue, alpha, bias);
		quad(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha, bias);
		quad(pose, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha, bias);
		quad(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, red, green, blue, alpha, bias);
	}
	/** 同上，角点以分立的浮点坐标给出。 */
	private static void quad(
		Pose pose,
		VertexConsumer consumer,
		float x0,
		float y0,
		float z0,
		float x1,
		float y1,
		float z1,
		float x2,
		float y2,
		float z2,
		float x3,
		float y3,
		float z3,
		float red,
		float green,
		float blue,
		float alpha,
		float bias
	) {
		vertex(pose, consumer, x0, y0, z0, red, green, blue, alpha, bias);
		vertex(pose, consumer, x1, y1, z1, red, green, blue, alpha, bias);
		vertex(pose, consumer, x2, y2, z2, red, green, blue, alpha, bias);
		vertex(pose, consumer, x3, y3, z3, red, green, blue, alpha, bias);
	}
	/** 白纹理只取一个点，颜色由 {@code setColor} 决定。 */
	private static void vertex(
		Pose pose,
		VertexConsumer consumer,
		float x,
		float y,
		float z,
		float red,
		float green,
		float blue,
		float alpha,
		float bias
	) {
		// 相机位于该坐标系原点，朝原点偏移即沿视线前移
		if (bias != 0F) {
			var distance = Mth.sqrt(x * x + y * y + z * z);
			if (distance > 1E-4F) {
				var scale = bias / distance;
				x -= x * scale;
				y -= y * scale;
				z -= z * scale;
			}
		}
		consumer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(0F, 0F)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LightTexture.FULL_BRIGHT)
			.setNormal(pose, 0F, 1F, 0F);
	}
	/**
	 * 画一个带描边的方块体积线框，给连接范围这种「立方体作用域」用。
	 * <p>外面一层粗描边、里面一条细主色，两层贴在同一条棱上。
	 * <p>走 {@link #RANGE} 那套「测深度、不写深度」；主色那层再朝相机挪 {@link #LAYER_BIAS}，见它的说明。
	 */
	public static void renderOutlinedBox(PoseStack pose, Vec3 camera, AABB box, int outlineColor, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RANGE);
		var pose1 = pose.last();
		boxEdges(consumer, pose1, camera, box, OUTLINE_W, outlineColor);
		boxEdges(consumer, pose1, camera, box, LINE_W, color, LAYER_BIAS);
		buffers.endBatch(RANGE);
	}
	/**
	 * 画一个任意朝向的实心四边形，顶点按世界坐标给出、按顺序绕行。
	 * <p>不能像 {@link #renderRect} 那样在 pose 的 XY 平面上绘制：此 pose 属于世界空间，
	 * 当作平面使用会随视角偏移。
	 */
	public static void renderQuad(Pose pose, Vec3 camera, int color, Vec3... points) {
		if (points.length < 3) return;
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RECT);
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		// 顶点减去相机，才落入 pose 所在的坐标系
		for (var point : points)
			planeVertex(
				pose,
				consumer,
				(float) (point.x - camera.x),
				(float) (point.y - camera.y),
				(float) (point.z - camera.z),
				red,
				green,
				blue,
				alpha
			);
		buffers.endBatch(RECT);
	}
	private static void planeVertex(
		Pose pose,
		VertexConsumer consumer,
		float x,
		float y,
		float z,
		float red,
		float green,
		float blue,
		float alpha
	) {
		consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
	}
	/** 在 pose 的 XY 平面上画一个实心矩形，给世界里的文字补下划线之类用。 */
	public static void renderRect(Pose pose, float minX, float minY, float maxX, float maxY, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		rect(buffers.getBuffer(SEE_THROUGH), pose, minX, minY, maxX, maxY, color);
		buffers.endBatch(SEE_THROUGH);
	}
	private static void rect(VertexConsumer consumer, Pose pose, float minX, float minY, float maxX, float maxY, int color) {
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		rectVertex(pose, consumer, minX, minY, red, green, blue, alpha);
		rectVertex(pose, consumer, maxX, minY, red, green, blue, alpha);
		rectVertex(pose, consumer, maxX, maxY, red, green, blue, alpha);
		rectVertex(pose, consumer, minX, maxY, red, green, blue, alpha);
	}
	private static void rectVertex(Pose pose, VertexConsumer consumer, float x, float y, float red, float green, float blue, float alpha) {
		consumer.addVertex(pose, x, y, 0F).setColor(red, green, blue, alpha);
	}
	/**
	 * 沿矩形四边绘制一圈描边。
	 * <p>四条边分别绘制、互不重叠：若在下方垫一个更大的实心矩形，两者深度相同，
	 * 遮挡关系只取决于绘制顺序，转动视角时会出现闪烁。
	 *
	 * @param thickness 描边厚度，画在 {@code minX..maxX} 之外
	 */
	public static void renderFrame(Pose pose, float minX, float minY, float maxX, float maxY, float thickness, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(SEE_THROUGH);
		rect(consumer, pose, minX - thickness, minY - thickness, maxX + thickness, minY, color);
		rect(consumer, pose, minX - thickness, maxY, maxX + thickness, maxY + thickness, color);
		rect(consumer, pose, minX - thickness, minY, minX, maxY, color);
		rect(consumer, pose, maxX, minY, maxX + thickness, maxY, color);
		buffers.endBatch(SEE_THROUGH);
	}
	/** 必须继承 {@link RenderType} 才能访问其 protected 的 {@code create}。 */
	private static final class RenderTypes extends RenderType {
		private static final RenderType OUTLINE = create(
			"mlog_outline",
			DefaultVertexFormat.NEW_ENTITY,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
				.setTextureState(new TextureStateShard(ResourceLocation.withDefaultNamespace("textures/misc/white.png"), false, false))
				// 棱是空心的，正反面都要看得见
				.setCullState(NO_CULL)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false)
		);
		/**
		 * 范围线框：同 {@link #OUTLINE}，只是写掩码只留颜色、深度测试写成 {@code LEQUAL}——
		 * 被地形遮挡无碍，但不写深度，以免先绘制的粗框覆盖后绘制的细框。
		 */
		private static final RenderType RANGE = create(
			"mlog_range",
			DefaultVertexFormat.NEW_ENTITY,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
				.setTextureState(new TextureStateShard(ResourceLocation.withDefaultNamespace("textures/misc/white.png"), false, false))
				.setCullState(NO_CULL)
				.setDepthTestState(LEQUAL_DEPTH_TEST)
				.setWriteMaskState(COLOR_WRITE)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false)
		);
		private static final RenderType RECT = create(
			"mlog_rect",
			DefaultVertexFormat.POSITION_COLOR,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				// 文字所用 pose 的 y 为负缩放，绕序相反，默认剔除会剔除整个矩形
				.setCullState(NO_CULL).createCompositeState(false)
		);
		/** 同 {@link #RECT}，另外照原版 {@code RenderType.textSeeThrough} 的做法关掉深度测试与深度写入。 */
		private static final RenderType SEE_THROUGH = create(
			"mlog_rect_see_through",
			DefaultVertexFormat.POSITION_COLOR,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder()
				.setShaderState(POSITION_COLOR_SHADER)
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setDepthTestState(NO_DEPTH_TEST)
				.setWriteMaskState(COLOR_WRITE)
				.setCullState(NO_CULL)
				.createCompositeState(false)
		);
		private RenderTypes(
			String name,
			VertexFormat format,
			Mode mode,
			int bufferSize,
			boolean affectsCrumbling,
			boolean sortOnUpload,
			Runnable setupState,
			Runnable clearState
		) {
			super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
		}
	}
}
