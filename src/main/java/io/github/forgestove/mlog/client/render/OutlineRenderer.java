package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 方块的粗描边，抄自 Create 的 catnip（{@code net.createmod.catnip.outliner.AABBOutline}）。
 * <p>原版 {@code RenderType.lines()} 在核心渲染管线下线宽恒为 1 像素，想要更粗只能自己拿面拼：
 * catnip 把框的十二条棱各展开成一个 {@code 长 × 线宽 × 线宽} 的长方体，
 * 于是线宽就是一个可以随便给的参数。这里省掉了它的缓冲池与法线开关，只留画框要用的部分。
 */
@OnlyIn(Dist.CLIENT)
public final class OutlineRenderer {
	/** 描边用的渲染类型：无纹理的实心面，正反面都画。 */
	private static final RenderType OUTLINE = RenderTypes.OUTLINE;
	/**
	 * 平面矩形用的渲染类型。
	 * <p>不能用 {@link #OUTLINE}：那是实体着色器，顶点里带了方向光，颜色会被压暗一截，
	 * 画在文字底下就看得出深浅不一。这个只吃位置和颜色，和文字那边的着色器口径一致。
	 */
	private static final RenderType RECT = RenderTypes.RECT;
	/**
	 * 给一个方块体积描边。
	 *
	 * @param camera 相机位置，顶点坐标要减掉它——这里的 pose 只带了相机的旋转
	 * @param width  线宽，单位是格（十六像素一格）
	 */
	public static void renderBox(PoseStack pose, Vec3 camera, AABB box, float width, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(OUTLINE);
		var pose1 = pose.last();
		// 顶点按世界坐标给，减掉相机后才落在 pose 所在的坐标系里
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
		// 十二条棱，每条都由两个端点决定
		edge(pose1, consumer, minX, minY, minZ, maxX, minY, minZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, minY, maxZ, maxX, minY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, maxY, minZ, maxX, maxY, minZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, maxY, maxZ, maxX, maxY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, minY, minZ, minX, maxY, minZ, width, red, green, blue, alpha);
		edge(pose1, consumer, maxX, minY, minZ, maxX, maxY, minZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, minY, maxZ, minX, maxY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, minY, minZ, minX, minY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, maxX, minY, minZ, maxX, minY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, minX, maxY, minZ, minX, maxY, maxZ, width, red, green, blue, alpha);
		edge(pose1, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, width, red, green, blue, alpha);
		buffers.endBatch(OUTLINE);
	}
	/**
	 * 画一个任意朝向的实心四边形，顶点按<b>世界坐标</b>给、绕一圈按顺序。
	 * <p>不能像 {@link #renderRect} 那样在 pose 的 XY 平面上画：这个 pose 是世界空间的，
	 * 拿它当平面用会跟着视角跑偏。
	 */
	public static void renderQuad(Pose pose, Vec3 camera, int color, Vec3... points) {
		if (points.length < 3) return;
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RECT);
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		// 顶点减掉相机，才落进 pose 所在的坐标系
		for (var point : points)
			planeVertex(pose, consumer, (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z), red, green, blue, alpha);
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
		float alpha
	) {
		var half = width / 2;
		var minX = Math.min(x0, x1) - half;
		var minY = Math.min(y0, y1) - half;
		var minZ = Math.min(z0, z1) - half;
		var maxX = Math.max(x0, x1) + half;
		var maxY = Math.max(y0, y1) + half;
		var maxZ = Math.max(z0, z1) + half;
		box(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
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
		float alpha
	) {
		quad(pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
		quad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
		quad(pose, consumer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, red, green, blue, alpha);
		quad(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
		quad(pose, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
		quad(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, red, green, blue, alpha);
	}
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
		float alpha
	) {
		vertex(pose, consumer, x0, y0, z0, red, green, blue, alpha);
		vertex(pose, consumer, x1, y1, z1, red, green, blue, alpha);
		vertex(pose, consumer, x2, y2, z2, red, green, blue, alpha);
		vertex(pose, consumer, x3, y3, z3, red, green, blue, alpha);
	}
	/** 白纹理只取一个点，颜色就由 {@code setColor} 决定。 */
	private static void vertex(
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
		consumer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(0F, 0F)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LightTexture.FULL_BRIGHT)
			.setNormal(pose, 0F, 1F, 0F);
	}
	/** 在 pose 的 XY 平面上画一个实心矩形，给世界里的文字补下划线之类用。 */
	public static void renderRect(Pose pose, float minX, float minY, float maxX, float maxY, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		rect(buffers.getBuffer(RECT), pose, minX, minY, maxX, maxY, color);
		buffers.endBatch(RECT);
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
	 * 沿矩形四边画一圈描边。
	 * <p>四条边各画各的、互不重叠：要是垫一个更大的实心矩形在下面，两者同深度，
	 * 谁盖谁只看绘制顺序，转视角时能看出闪。
	 *
	 * @param thickness 描边厚度，画在 {@code minX..maxX} 之外
	 */
	public static void renderFrame(Pose pose, float minX, float minY, float maxX, float maxY, float thickness, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RECT);
		rect(consumer, pose, minX - thickness, minY - thickness, maxX + thickness, minY, color);
		rect(consumer, pose, minX - thickness, maxY, maxX + thickness, maxY + thickness, color);
		rect(consumer, pose, minX - thickness, minY, minX, maxY, color);
		rect(consumer, pose, maxX, minY, maxX + thickness, maxY, color);
		buffers.endBatch(RECT);
	}
	/** 必须继承 {@link RenderType} 才够得着它 protected 的 {@code create}，catnip 也是这么做的。 */
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
		private static final RenderType RECT = create(
			"mlog_rect",
			DefaultVertexFormat.POSITION_COLOR,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				// 文字那个 pose 的 y 是负缩放，绕序是反的，默认剔除会把整个矩形吃掉
				.setCullState(NO_CULL).createCompositeState(false)
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
