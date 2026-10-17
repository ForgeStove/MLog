package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.RenderStateShard.*;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.util.*;
import java.util.function.Supplier;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 画布的离屏缓冲：命令按到达顺序增量绘制且内容留存，绘制状态跨帧保留。 */
public class DisplayBuffer {
	private static final float CHANNEL = 1F / 255F;
	/** 画布底色，取模型背板那一色。 */
	private static final int BACKGROUND = 0x565666;
	/** 已废弃、等待归还显存的画布，延后一帧释放以避开绘制中的批次。 */
	private static final List<DisplayBuffer> RETIRED = new ArrayList<>();
	private final RenderType renderType;
	private final PoseStack pose = new PoseStack();
	private int color = 0xFFFFFFFF;
	private float stroke = 1F;
	private RenderTarget target;
	private int width, height;
	public DisplayBuffer(int width, int height) {
		this.width = width;
		this.height = height;
		target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		// 顶点色与亮度均须给出：光影包兜底 program 属性缺失时会渲染成透明材质
		renderType = RenderType.create(
			"mlog_tile_logic_display_canvas",
			DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			Mode.QUADS,
			1536,
			false,
			false,
			CompositeState.builder()
				// 屏幕色不随世界光照，暗处仍可辨识
				.setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader))
				// 直接绑离屏色纹理，不经贴图管理器，否则名字被注销后采样会落到缺省贴图
				.setTextureState(new EmptyTextureStateShard(this::bind, () -> {}))
				// 缓冲的 alpha 会被内部混合破坏，按不透明铺开，否则会透出后方方块
				.setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
				.setCullState(RenderStateShard.NO_CULL)
				.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
				.createCompositeState(false)
		);
		filter();
		// 画布自挂上批次即被采样，须先清底，避免暴露未初始化显存
		reset();
	}
	/** 裁剪框会裁去离屏绘制的一角，且清底与拷贝均受其影响，故返回进入前的裁剪开关供绘制完成后还原。 */
	private static boolean offscreen() {
		var scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		RenderSystem.disableScissor();
		return scissor;
	}
	private static void restoreScissor(boolean scissor) {
		if (scissor) GlStateManager._enableScissorTest();
	}
	private static void clear(int red, int green, int blue) {
		RenderSystem.clearColor(red * CHANNEL, green * CHANNEL, blue * CHANNEL, 1F);
		RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
	}
	/** 离屏绘制的混合式：alpha 须按 {@code ONE/ONE_MINUS_SRC_ALPHA} 累积，否则全透明图形会破坏缓冲 alpha。 */
	private static void blend() {
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
			GlConst.GL_SRC_ALPHA,
			GlConst.GL_ONE_MINUS_SRC_ALPHA,
			GlConst.GL_ONE,
			GlConst.GL_ONE_MINUS_SRC_ALPHA
		);
	}
	/** 将画布 alpha 置为不透明：字体批次以字形覆盖度覆盖 alpha，铺面批次会丢弃 alpha 低于 0.1 的像素；alpha 无法靠折算回升，须整体覆盖。 */
	private static void makeOpaque(int width, int height) {
		var builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		var matrix = new Matrix4f();
		builder.addVertex(matrix, 0F, 0F, 0F).setColor(0xFFFFFFFF);
		builder.addVertex(matrix, width, 0F, 0F).setColor(0xFFFFFFFF);
		builder.addVertex(matrix, width, height, 0F).setColor(0xFFFFFFFF);
		builder.addVertex(matrix, 0F, height, 0F).setColor(0xFFFFFFFF);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlConst.GL_ZERO, GlConst.GL_ONE, GlConst.GL_ONE, GlConst.GL_ZERO);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferUploader.drawWithShader(builder.buildOrThrow());
		// 还原为离屏使用的混合式，供后续帧沿用
		blend();
	}
	private static void line(Batch batch, PoseStack pose, int color, double x0, double y0, double x1, double y1, float width) {
		var dx = x1 - x0;
		var dy = y1 - y0;
		var length = Math.sqrt(dx * dx + dy * dy);
		if (length < 1E-6) return;
		// 沿向与垂向各取半个线宽，垂向即沿向转 90°
		var half = width / 2;
		var ux = dx / length * half;
		var uy = dy / length * half;
		quad(
			batch,
			pose,
			color,
			x0 - ux - uy,
			y0 - uy + ux,
			x0 - ux + uy,
			y0 - uy - ux,
			x1 + ux + uy,
			y1 + uy - ux,
			x1 + ux - uy,
			y1 + uy + ux
		);
	}
	private static void regular(DrawCmd command, PoseStack pose, Batch batch, int color, float stroke, boolean outline) {
		var sides = Mth.clamp((int) command.p1(), 3, GraphicsType.MAX_SIDES);
		var radius = command.p2();
		var rotation = Math.toRadians(command.p3());
		var x = command.x();
		var y = command.y();
		var step = Math.PI * 2 / sides;
		// 轮廓内外半径各让出「半个线宽 / cos(半夹角)」，相邻两边的四边形恰接于径向线上
		var miter = stroke / 2 / Math.cos(step / 2);
		var inner = radius - miter;
		var outer = radius + miter;
		for (var i = 0; i < sides; i++) {
			var from = rotation + i * step;
			var to = from + step;
			if (outline) {
				quad(
					batch,
					pose,
					color,
					x + Math.cos(from) * inner,
					y + Math.sin(from) * inner,
					x + Math.cos(to) * inner,
					y + Math.sin(to) * inner,
					x + Math.cos(to) * outer,
					y + Math.sin(to) * outer,
					x + Math.cos(from) * outer,
					y + Math.sin(from) * outer
				);
				continue;
			}
			var consumer = batch.builder();
			vertex(consumer, pose, color, x, y);
			vertex(consumer, pose, color, x + Math.cos(from) * radius, y + Math.sin(from) * radius);
			vertex(consumer, pose, color, x + Math.cos(to) * radius, y + Math.sin(to) * radius);
		}
	}
	private static void image(Batch batch, PoseStack pose, int color, DrawCmd command) {
		var packed = (int) command.p1() & 0x3FF | (int) command.p4() << 10;
		if (packed < 0) return;
		// 类型 0 是物品、1 是方块，编号按各自的注册表算
		var type = packed & 0x1F;
		var id = packed >> 5;
		var item = type == 1 ? BuiltInRegistries.BLOCK.byId(id).asItem() : BuiltInRegistries.ITEM.byId(id);
		if (item == Items.AIR) return;
		// 取代表图标：物品与方块均有该贴图
		var sprite = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0).getParticleIcon(ModelData.EMPTY);
		RenderSystem.setShaderTexture(0, sprite.atlasLocation());
		var rotation = Math.toRadians(command.p3());
		var cos = (float) Math.cos(rotation);
		var sin = (float) Math.sin(rotation);
		var half = (float) command.p2() / 2;
		var x = (float) command.x();
		var y = (float) command.y();
		// 图标的 v 朝下、画布 y 朝上，故上沿取 v1、下沿取 v0
		batch.vertex(pose, color, x + half * -cos + half * sin, y + half * -sin - half * cos, sprite.getU0(), sprite.getV1());
		batch.vertex(pose, color, x + half * cos + half * sin, y + half * sin - half * cos, sprite.getU1(), sprite.getV1());
		batch.vertex(pose, color, x + half * cos - half * sin, y + half * sin + half * cos, sprite.getU1(), sprite.getV0());
		batch.vertex(pose, color, x + half * -cos - half * sin, y + half * -sin + half * cos, sprite.getU0(), sprite.getV0());
	}
	private static void print(PoseStack pose, DrawCmd command, int color) {
		var text = command.text();
		if (text.isEmpty()) return;
		var font = mc.font;
		var align = DrawAlign.values()[Mth.clamp((int) command.p1(), 0, DrawAlign.values().length - 1)];
		var lines = text.split("\n", -1);
		var lineHeight = font.lineHeight;
		// 画布 y 朝上，对齐量往上加：整块文本的上沿即首行的上沿
		var top = (float) (command.y() + align.vertical() * lines.length * lineHeight);
		var buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
		for (var i = 0; i < lines.length; i++) {
			var line = lines[i];
			var x = (float) (command.x() - align.horizontal() * font.width(line));
			// 字体的 y 朝下，该行局部翻转一次再画回 y 朝上的画布
			pose.pushPose();
			pose.translate(0F, top - i * lineHeight, 0F);
			pose.scale(1F, -1F, 1F);
			font.drawInBatch(
				Component.literal(line).getVisualOrderText(),
				x,
				0F,
				color,
				false,
				pose.last().pose(),
				buffers,
				DisplayMode.NORMAL,
				0,
				LightTexture.FULL_BRIGHT
			);
			pose.popPose();
		}
		buffers.endBatch();
	}
	/** 把命令里 0~255 的(红, 绿, 蓝, 透明)折成一个颜色值；{@link ARGB32#color} 的参数序是 alpha 在前。 */
	private static int channel(double r, double g, double b, double a) {
		return ARGB32.color(
			(int) Mth.clamp(a, 0D, 255D),
			(int) Mth.clamp(r, 0D, 255D),
			(int) Mth.clamp(g, 0D, 255D),
			(int) Mth.clamp(b, 0D, 255D)
		);
	}
	private static void quad(
		Batch batch,
		PoseStack pose,
		int color,
		double x0,
		double y0,
		double x1,
		double y1,
		double x2,
		double y2,
		double x3,
		double y3
	) {
		var consumer = batch.builder();
		vertex(consumer, pose, color, x0, y0);
		vertex(consumer, pose, color, x1, y1);
		vertex(consumer, pose, color, x2, y2);
		vertex(consumer, pose, color, x0, y0);
		vertex(consumer, pose, color, x2, y2);
		vertex(consumer, pose, color, x3, y3);
	}
	private static void quad(Batch batch, PoseStack pose, int color, double minX, double minY, double maxX, double maxY) {
		quad(batch, pose, color, minX, minY, maxX, minY, maxX, maxY, minX, maxY);
	}
	private static void vertex(VertexConsumer consumer, PoseStack pose, int color, double x, double y) {
		consumer.addVertex(pose.last(), (float) x, (float) y, 0F).setColor(color);
	}
	public static void releaseRetired() {
		if (RETIRED.isEmpty()) return;
		for (var buffer : RETIRED) buffer.close();
		RETIRED.clear();
	}
	private void bind() {
		RenderSystem.setShaderTexture(0, target.getColorTextureId());
	}
	/** 按最近邻采样：画布与贴图像素本是一比一，插值只会模糊边缘。 */
	private void filter() {
		RenderSystem.activeTexture(GlConst.GL_TEXTURE0);
		GlStateManager._bindTexture(target.getColorTextureId());
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
	}
	private void reset() {
		fillBackground();
		color = 0xFFFFFFFF;
		stroke = 1F;
		pose.setIdentity();
	}
	private void fillBackground() {
		var scissor = offscreen();
		target.bindWrite(true);
		clear(BACKGROUND >> 16 & 0xFF, BACKGROUND >> 8 & 0xFF, BACKGROUND & 0xFF);
		restoreScissor(scissor);
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** 越界部分由 GL 裁剪，此处不做边界检查。 */
	public void copyFrom(DisplayBuffer source, int dx, int dy) {
		blitFrom(source.target, source.width, source.height, dx, dy);
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** 调用方负责还原帧缓冲绑定。 */
	private void blitFrom(RenderTarget source, int sourceWidth, int sourceHeight, int dx, int dy) {
		var scissor = offscreen();
		target.bindWrite(true);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
		GlStateManager._glBlitFrameBuffer(
			0,
			0,
			sourceWidth,
			sourceHeight,
			dx,
			dy,
			dx + sourceWidth,
			dy + sourceHeight,
			GL11.GL_COLOR_BUFFER_BIT,
			GL11.GL_NEAREST
		);
		restoreScissor(scissor);
	}
	private void replay(List<DrawCmd> commands, Batch shapes, Batch images) {
		for (var command : commands)
			switch (command.type()) {
				case clear -> {
					// 清屏会丢弃已积累图形，须先按顺序提交
					shapes.flush();
					images.flush();
					clear((int) command.x(), (int) command.y(), (int) command.p1());
				}
				case color -> color = channel(command.x(), command.y(), command.p1(), command.p2());
				case stroke -> stroke = (float) command.x();
				case line -> {
					images.flush();
					line(shapes, pose, color, command.x(), command.y(), command.p1(), command.p2(), stroke);
				}
				case rect -> {
					images.flush();
					var bounds = new Bounds(command);
					quad(shapes, pose, color, bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY());
				}
				case lineRect -> {
					images.flush();
					// 四条边沿矩形内侧铺设，四角由相邻两边各覆盖一次
					var bounds = new Bounds(command);
					quad(shapes, pose, color, bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY() + stroke);
					quad(shapes, pose, color, bounds.minX(), bounds.maxY() - stroke, bounds.maxX(), bounds.maxY());
					quad(shapes, pose, color, bounds.maxX() - stroke, bounds.minY(), bounds.maxX(), bounds.maxY());
					quad(shapes, pose, color, bounds.minX(), bounds.minY(), bounds.minX() + stroke, bounds.maxY());
				}
				case poly, linePoly -> {
					images.flush();
					regular(command, pose, shapes, color, stroke, command.type() == GraphicsType.linePoly);
				}
				case triangle -> {
					images.flush();
					var consumer = shapes.builder();
					vertex(consumer, pose, color, command.x(), command.y());
					vertex(consumer, pose, color, command.p1(), command.p2());
					vertex(consumer, pose, color, command.p3(), command.p4());
				}
				case image -> {
					shapes.flush();
					// 贴图在批次提交时才绑定，图标分属不同图集，须先提交上一批
					images.flush();
					image(images, pose, color, command);
				}
				case print -> {
					shapes.flush();
					images.flush();
					print(pose, command, color);
				}
				case translate -> pose.translate((float) command.x(), (float) command.y(), 0F);
				case scale ->
					pose.scale((float) (command.x() * GraphicsType.SCALE_STEP), (float) (command.y() * GraphicsType.SCALE_STEP), 1F);
				case rotate -> pose.mulPose(Axis.ZP.rotationDegrees((float) command.p1()));
				case reset -> pose.setIdentity();
			}
	}
	/** 尺寸变化时就地改建离屏目标；换新缓冲会使已提交的批次引用到已销毁的纹理。 */
	public void resize(int width, int height, int dx, int dy) {
		if (matches(width, height)) return;
		var previous = target;
		var previousWidth = this.width;
		var previousHeight = this.height;
		this.width = width;
		this.height = height;
		target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		filter();
		fillBackground();
		blitFrom(previous, previousWidth, previousHeight, dx, dy);
		// destroyBuffers 会将帧缓冲绑回 0，还原须排在其后
		previous.destroyBuffers();
		mc.getMainRenderTarget().bindWrite(true);
	}
	public boolean matches(int width, int height) {
		return this.width == width && this.height == height;
	}
	public void close() {
		target.destroyBuffers();
		// destroyBuffers 会将帧缓冲绑回 0；不还原则本帧余下绘制落入屏幕帧缓冲
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** 废弃本画布：本帧仍可能被绘制，故推迟到下一次渲染前归还。 */
	public void retire() {
		RETIRED.add(this);
	}
	public RenderType renderType() {
		return renderType;
	}
	/** 将命令增量绘制进画布：原点在左下、y 朝上；不做半格平移，移位会使奇数尺寸图形偏移。 */
	public void apply(List<DrawCmd> commands) {
		var main = mc.getMainRenderTarget();
		target.bindWrite(true);
		var scissor = offscreen();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, width, 0F, height, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
		var stack = RenderSystem.getModelViewStack();
		stack.pushMatrix();
		stack.identity();
		RenderSystem.applyModelViewMatrix();
		blend();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		var shapes = new Batch(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR, GameRenderer::getPositionColorShader);
		var images = new Batch(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, GameRenderer::getPositionTexColorShader);
		replay(commands, shapes, images);
		shapes.flush();
		images.flush();
		// 收尾将 alpha 置为不透明：低于 0.1 的边缘会被铺面批次的 alpha 测试丢弃
		makeOpaque(width, height);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		stack.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();
		restoreScissor(scissor);
		main.bindWrite(true);
	}
	/** {@code (x, y)} 为左下角的矩形边界，改作中心锚会使 1×1 矩形落在采样格之外。 */
	private record Bounds(double minX, double minY, double maxX, double maxY) {
		private Bounds(DrawCmd command) {
			this(command.x(), command.y(), command.x() + command.p1(), command.y() + command.p2());
		}
	}
	/** 累积的一批顶点；两类图形的顶点格式不同，合不到一起，换批时按顺序各画各的。 */
	private static class Batch {
		private final Mode mode;
		private final VertexFormat format;
		private final Supplier<ShaderInstance> shader;
		private BufferBuilder builder;
		private boolean used;
		private Batch(Mode mode, VertexFormat format, Supplier<ShaderInstance> shader) {
			this.mode = mode;
			this.format = format;
			this.shader = shader;
			builder = Tesselator.getInstance().begin(mode, format);
		}
		private void vertex(PoseStack pose, int color, float x, float y, float u, float v) {
			builder().addVertex(pose.last(), x, y, 0F).setColor(color).setUv(u, v);
		}
		/** 本批的顶点出口，调用后这一批即视为非空。 */
		private VertexConsumer builder() {
			used = true;
			return builder;
		}
		private void flush() {
			if (!used) return;
			// 文本批次经 RenderType，收尾会关闭混合，不还原则其后图形不参与混合
			blend();
			RenderSystem.setShader(shader);
			BufferUploader.drawWithShader(builder.buildOrThrow());
			builder = Tesselator.getInstance().begin(mode, format);
			used = false;
		}
	}
}
