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

import java.util.ArrayList;
import java.util.List;

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
	 * 球面专用的渲染类型：和 {@link #OUTLINE} 同一套，但<b>只测深度、不写深度</b>。
	 * <p>测深度：球半径十来格，人常站在球里，靠它才被地面、山体、方块挡住，而不是整块糊在画面上。
	 * <p>不写深度：写的话球会挡自己——灰描边先画、主色后画，两者共面，写深度之后先画的那条
	 * 会把后画的整段盖掉，看着就是「描边把线条主体吃了」，而且共面来回比深度还会一片一片地闪。
	 * 只测不写之后这些都免了：球自己的先后只由绘制顺序定（灰的铺完再压主色），
	 * 对外仍和世界正常比深度。
	 */
	private static final RenderType SPHERE = RenderTypes.SPHERE;
	/**
	 * 平面矩形用的渲染类型。
	 * <p>不能用 {@link #OUTLINE}：那是实体着色器，顶点里带了方向光，颜色会被压暗一截，
	 * 画在文字底下就看得出深浅不一。这个只吃位置和颜色，和文字那边的着色器口径一致。
	 */
	private static final RenderType RECT = RenderTypes.RECT;
	/**
	 * 球面线框的密度：经线数、纬线数、每圈的段数。
	 * <p>和 Mindustry 一样用折线逼近圆（那边的 {@code Lines.circle} 也是按固定段数拼出来的），
	 * 段数越大越圆，顶点数也成正比涨。
	 * <p>纬线取的是奇数条，正好让中间那条落在赤道上（{@code i/(PARALLELS+1) - 0.5} 在奇数条时能取到 0），
	 * 那条最显眼，少了球看着就是缺了一圈。
	 */
	private static final int MERIDIANS = 5, PARALLELS = 5, SEGMENTS = 48;
	/**
	 * 球面那圈描边的宽度：外面粗灰、里面细主色。
	 * <p>比例照 Mindustry 的 {@code Drawf.circles} 取：它是 {@code stroke(3f, Pal.gray)} 垫在
	 * {@code stroke(1f, color)} 底下，也就是 3 : 1。
	 */
	private static final float SPHERE_EDGE_W = 3 / 16F, SPHERE_LINE_W = 1 / 16F;
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
	 * 画一个带描边的线框球面，用来表示连接范围之类的球形作用域。
	 * <p>和 Mindustry 用折线逼近圆是一个意思：一圈圈经纬线，每圈按 {@link #SEGMENTS} 段摊开。
	 * 外层是粗的描边色、里面压一条细的主色，对齐那边的 {@code Drawf.circles}。
	 * <p>整球只取一次缓冲：先把所有粗描边铺完，再压上所有细主色，中间不提交。
	 * <p>每一圈都是平行于屏幕的带子（见 {@link #polyline}）：从任何角度看都是同样的粗细，
	 * 不会像「躺在自己平面里的圆」那样一斜就侧过去变薄。
	 *
	 * @param center       球心，世界坐标
	 * @param radius       半径，单位是格
	 * @param outlineColor 外层粗描边的颜色，对齐 {@code Drawf.circles} 里的 {@code Pal.gray}
	 */
	public static void renderSphere(PoseStack pose, Vec3 camera, Vec3 center, double radius, int outlineColor, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(SPHERE);
		var rings = sphereRings(center, radius);
		// 2D 那边是「先粗灰后细彩」，两层共面，后画的盖住先画的，外面正好露出一圈灰边
		var look = mc.gameRenderer.getMainCamera().getLookVector();
		var view = new Vec3(look.x, look.y, look.z);
		stroke(pose.last(), consumer, view, camera, rings, SPHERE_EDGE_W, outlineColor);
		stroke(pose.last(), consumer, view, camera, rings, SPHERE_LINE_W, color);
		buffers.endBatch(SPHERE);
	}
	/** 把所有圈按同一个线宽和颜色画一遍，顶点直接写进 {@code consumer}。 */
	private static void stroke(
		Pose pose,
		VertexConsumer consumer,
		Vec3 view,
		Vec3 camera,
		List<List<Vec3>> rings,
		float width,
		int color
	) {
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		for (var ring : rings) polyline(pose, consumer, view, camera, ring, width, red, green, blue, alpha);
	}
	/** @return 球面上的所有圈：先纬线后经线，每条都是首尾相连的点列（世界坐标）。 */
	private static List<List<Vec3>> sphereRings(Vec3 center, double radius) {
		var rings = new ArrayList<List<Vec3>>();
		// 纬线：越靠近两极越小，两极本身退化成一个点，那边不画
		for (var i = 1; i <= PARALLELS; i++) {
			var latitude = Math.PI * (i / (double) (PARALLELS + 1) - 0.5);
			var ringRadius = Math.cos(latitude) * radius;
			rings.add(circle(center.add(0, Math.sin(latitude) * radius, 0), new Vec3(ringRadius, 0, 0), new Vec3(0, 0, ringRadius)));
		}
		// 经线：绕 Y 轴均匀铺开，每条都是从一极绕到另一极的大圆
		for (var i = 0; i < MERIDIANS; i++) {
			var angle = Math.PI * i / MERIDIANS;
			rings.add(
				circle(
					center,
					new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
					new Vec3(0, radius, 0)
				)
			);
		}
		return rings;
	}
	/** @return 一圈的点：圆心 {@code center}，所在平面由两个半轴向量 {@code u}、{@code v} 张成。 */
	private static List<Vec3> circle(Vec3 center, Vec3 u, Vec3 v) {
		var points = new ArrayList<Vec3>(SEGMENTS);
		for (var i = 0; i < SEGMENTS; i++) {
			var t = Math.PI * 2 * i / SEGMENTS;
			points.add(center.add(u.scale(Math.cos(t))).add(v.scale(Math.sin(t))));
		}
		return points;
	}
	/**
	 * 画一圈平行于屏幕的粗折线（首尾相连），顶点直接写进 {@code consumer}（缓冲由调用方管）。
	 * <p>每个折点的偏移量取「切向 × 视线方向」，视线用相机这一帧的朝向（一个全局向量，不是每个点各自
	 * 指向相机的那个向量）。这样带子铺在<b>过这一点、平行于屏幕</b>的平面里——和 2D 里画粗线一样，
	 * 从任何角度看都是同样的粗细；也不会像「躺在自己平面里的带子」那样一斜就薄得看不见。
	 * <p>视线必须是全局的那一个。用逐点的「指向相机」时，每条圈上都有两个点切向正对相机，叉积在那里归零，
	 * 而两侧的点算出来的方向还会各自翻面、且翻面点和零点错开，相邻那条四边形就扭成蝴蝶结、
	 * 粗描边整个盖到主色线上（只有站在球心看时两者恰好重合，所以那时候是好的）。
	 * 全局视线的零点与翻面点重合，那一处只退化成一条细缝，不会翻。
	 * <p>摊成四边形而不是用 {@code RenderType.lines()}：后者的线宽在核心渲染管线下恒为 1 像素、
	 * 改不了，也就铺不出外面那层粗描边。
	 *
	 * @param view 相机这一帧的视线方向（世界坐标）
	 */
	private static void polyline(
		Pose pose,
		VertexConsumer consumer,
		Vec3 view,
		Vec3 camera,
		List<Vec3> points,
		float width,
		float red,
		float green,
		float blue,
		float alpha
	) {
		var count = points.size();
		if (count < 2) return;
		var half = width / 2;
		var offsets = new Vec3[count];
		// 切向正对视线时叉积归零，那一段投影下来本来就是一个点，沿用上一处的偏移量兜底，别让它翻面
		var fallback = new Vec3(0, half, 0);
		for (var i = 0; i < count; i++) {
			var next = points.get((i + 1) % count);
			var previous = points.get((i - 1 + count) % count);
			var normal = next.subtract(previous).cross(view);
			fallback = normal.lengthSqr() < 1.0E-8 ? fallback : normal.normalize().scale(half);
			offsets[i] = fallback;
		}
		for (var i = 0; i < count; i++) {
			var next = (i + 1) % count;
			var from = points.get(i);
			var to = points.get(next);
			quad(
				pose,
				consumer,
				from.subtract(offsets[i]).subtract(camera),
				from.add(offsets[i]).subtract(camera),
				to.add(offsets[next]).subtract(camera),
				to.subtract(offsets[next]).subtract(camera),
				red,
				green,
				blue,
				alpha
			);
		}
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
	/** 四边形：四个角点是 {@link Vec3}，都已经是相机相对坐标，按顺序绕一圈。 */
	private static void quad(
		Pose pose,
		VertexConsumer consumer,
		Vec3 a,
		Vec3 b,
		Vec3 c,
		Vec3 d,
		float red,
		float green,
		float blue,
		float alpha
	) {
		quad(
			pose,
			consumer,
			(float) a.x,
			(float) a.y,
			(float) a.z,
			(float) b.x,
			(float) b.y,
			(float) b.z,
			(float) c.x,
			(float) c.y,
			(float) c.z,
			(float) d.x,
			(float) d.y,
			(float) d.z,
			red,
			green,
			blue,
			alpha
		);
	}
	/** 同上，角点是拆开的浮点坐标。 */
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
		/** 同 {@link #OUTLINE}，写掩码只留颜色（只测深度、不写深度）：线框球靠这个既不挡自己、又会被世界挡住。 */
		private static final RenderType SPHERE = create(
			"mlog_sphere",
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
