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

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 方块的粗描边：{@code RenderType.lines()} 线宽恒为 1 像素，加粗须把十二条棱各展开成 {@code 长 × 线宽 × 线宽} 的长方体。 */
public class OutlineRenderer {
	private static final RenderType OUTLINE = RenderTypes.OUTLINE;
	/** 深度照测但不写深度，层次由 {@link #LAYER_BIAS} 处理。 */
	private static final RenderType RANGE = RenderTypes.RANGE;
	/** 平面矩形专用：{@link #OUTLINE} 是实体着色器，方向光会压暗颜色。 */
	private static final RenderType RECT = RenderTypes.RECT;
	/** 穿透方块的平面矩形（链接名下划线）：不测也不写深度，否则线被方块深度遮挡。 */
	private static final RenderType SEE_THROUGH = RenderTypes.SEE_THROUGH;
	/** 范围线框那圈描边的宽度，外面粗灰、里面细主色，宽度比 3 : 1。 */
	private static final float OUTLINE_W = 3 / 16F, LINE_W = 1 / 16F;
	/** 主色层沿视线朝相机偏移的距离，取 1/8 格以盖住灰色描边。 */
	private static final float LAYER_BIAS = 1 / 8F;
	/** {@code pose} 只含相机旋转，顶点坐标须减去 {@code camera} 后才落入该坐标系。 */
	public static void renderBox(PoseStack pose, Vec3 camera, AABB box, float width, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		boxEdges(buffers.getBuffer(OUTLINE), pose.last(), camera, box, width, color);
		buffers.endBatch(OUTLINE);
	}
	private static void boxEdges(VertexConsumer consumer, Pose pose, Vec3 camera, AABB box, float width, int color) {
		boxEdges(consumer, pose, camera, box, width, color, 0F);
	}
	/** {@code bias} 为每个顶点沿视线朝相机偏移的距离，使后画的层盖住先画的层。 */
	private static void boxEdges(VertexConsumer consumer, Pose pose, Vec3 camera, AABB box, float width, int color, float bias) {
		// 减去相机后才落入 pose 所属坐标系
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
		// 相机在该坐标系原点，朝原点偏移即沿视线前移
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
	/** 带描边的方块体积线框，供连接范围使用：外层粗描边、内层细主色。 */
	public static void renderOutlinedBox(PoseStack pose, Vec3 camera, AABB box, int outlineColor, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RANGE);
		var pose1 = pose.last();
		boxEdges(consumer, pose1, camera, box, OUTLINE_W, outlineColor);
		boxEdges(consumer, pose1, camera, box, LINE_W, color, LAYER_BIAS);
		buffers.endBatch(RANGE);
	}
	/** 任意朝向的实心四边形，顶点按世界坐标给出；此 pose 属世界空间，不可当作 XY 平面使用。 */
	public static void renderQuad(Pose pose, Vec3 camera, int color, Vec3... points) {
		if (points.length < 3) return;
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RECT);
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		// 减去相机才落入 pose 所属坐标系
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
	/** 在 pose 的 XY 平面上画一个实心矩形，供文字下划线使用。 */
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
	/** 四条边分别绘制、互不重叠：垫大矩形会与其深度相同，遮挡只取决于绘制顺序而闪烁。 */
	public static void renderFrame(Pose pose, float minX, float minY, float maxX, float maxY, float thickness, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(SEE_THROUGH);
		rect(consumer, pose, minX - thickness, minY - thickness, maxX + thickness, minY, color);
		rect(consumer, pose, minX - thickness, maxY, maxX + thickness, maxY + thickness, color);
		rect(consumer, pose, minX - thickness, minY, minX, maxY, color);
		rect(consumer, pose, maxX, minY, maxX + thickness, maxY, color);
		buffers.endBatch(SEE_THROUGH);
	}
	private static class RenderTypes extends RenderType {
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
				// 棱是空心的，正反面都要可见
				.setCullState(NO_CULL)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false)
		);
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
