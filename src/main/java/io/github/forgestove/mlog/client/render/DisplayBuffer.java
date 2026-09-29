package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.RenderStateShard.*;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;
import java.util.function.Supplier;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 一块画布的离屏缓冲。
 * <p>命令按到达顺序增量绘制，内容留存于缓冲；仅 {@code clear} 命令与画布新建／改建会清底。
 * <p>绘制状态跨帧保留，见 {@link #color} 等字段。
 */
@OnlyIn(Dist.CLIENT)
public final class DisplayBuffer {
	/** 颜色分量由 0~255 折算到 0~1。 */
	private static final float CHANNEL = 1F / 255F;
	/** 新建画布时的底色，取模型背板那一色，未绘制的像素与背板一致。 */
	private static final int BACKGROUND = 0x565666;
	/** 画布名的序号：同名会互相顶掉。 */
	private static int next;
	/** 画布在贴图管理器中的名字，以及铺到方块面上使用的批次。 */
	private final ResourceLocation location;
	private final RenderType renderType;
	/** 跨帧保留的绘制状态。 */
	private final PoseStack pose = new PoseStack();
	private int color = 0xFFFFFFFF;
	private float stroke = 1F;
	private RenderTarget target;
	private int width, height;
	public DisplayBuffer(int width, int height) {
		this.width = width;
		this.height = height;
		target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		// 渲染批次按名字索引贴图，须先将色纹理注册到贴图管理器
		location = ResourceLocation.fromNamespaceAndPath(MLog.ID, "tile_logic_display_canvas/" + next++);
		Minecraft.getInstance().getTextureManager().register(
			location, new AbstractTexture() {
				@Override
				public int getId() {
					return target.getColorTextureId();
				}
				@Override
				public void load(ResourceManager resourceManager) {}
			}
		);
		// 顶点色与亮度均须给出：光影包对自建 RenderType 走兜底 program，属性缺失时会按默认值渲染成透明材质；
		// 本条 fsh 不采样亮度，故给出满亮不影响原版观感
		renderType = RenderType.create(
			"mlog_tile_logic_display_canvas", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, Mode.QUADS, 1536, false, false,
			CompositeState.builder()
				// 屏幕色不随世界光照，暗处仍可辨识
				.setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader))
				.setTextureState(new TextureStateShard(location, false, false))
				// 缓冲的 alpha 会被内部混合改坏，按不透明铺开，否则会透出后面的方块
				.setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
				.setCullState(RenderStateShard.NO_CULL)
				.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
				.createCompositeState(false)
		);
		// 画布自挂上批次即被采样，须先清底，避免暴露未初始化的显存
		reset();
	}
	/** 清一次底并复位绘制状态；新建画布时调用。 */
	private void reset() {
		fillBackground();
		color = 0xFFFFFFFF;
		stroke = 1F;
		pose.setIdentity();
	}
	/** 就地用底色清一遍。 */
	private void fillBackground() {
		var scissor = offscreen();
		target.bindWrite(true);
		clear(BACKGROUND >> 16 & 0xFF, BACKGROUND >> 8 & 0xFF, BACKGROUND & 0xFF);
		restoreScissor(scissor);
		mc.getMainRenderTarget().bindWrite(true);
	}
	/**
	 * 关闭裁剪框；裁剪框会裁去离屏绘制的一角，且清底与拷贝均受其影响。
	 *
	 * @return 进入前的裁剪开关，绘制完成后据此还原
	 */
	private static boolean offscreen() {
		var scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		RenderSystem.disableScissor();
		return scissor;
	}
	private static void restoreScissor(boolean scissor) {
		if (scissor) GlStateManager._enableScissorTest();
	}
	/** 将另一块画布的内容按给定像素位移搬入本画布，越界部分由 GL 裁剪。 */
	public void copyFrom(DisplayBuffer source, int dx, int dy) {
		blitFrom(source.target, source.width, source.height, dx, dy);
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** 将另一份离屏目标按像素位移绘入本画布；调用方负责还原帧缓冲绑定。 */
	private void blitFrom(RenderTarget source, int sourceWidth, int sourceHeight, int dx, int dy) {
		var scissor = offscreen();
		target.bindWrite(true);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
		GlStateManager._glBlitFrameBuffer(
			0, 0, sourceWidth, sourceHeight,
			dx, dy, dx + sourceWidth, dy + sourceHeight,
			GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
		);
		restoreScissor(scissor);
	}
	/** 用给定色清一遍。 */
	private static void clear(int red, int green, int blue) {
		RenderSystem.clearColor(red * CHANNEL, green * CHANNEL, blue * CHANNEL, 1F);
		RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
	}
	/**
	 * 离屏这一遍的混合式。
	 * <p>alpha 通道须按 {@code ONE/ONE_MINUS_SRC_ALPHA} 攒，不能用 {@link RenderSystem#defaultBlendFunc} 那套
	 * {@code ONE/ZERO}：后者拿源 alpha 直接覆盖目标 alpha，画一条全透明的图形就把缓冲挖空，
	 * 画布再铺到方块面上便透出后面的方块。
	 */
	private static void blend() {
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
			GlConst.GL_SRC_ALPHA,
			GlConst.GL_ONE_MINUS_SRC_ALPHA,
			GlConst.GL_ONE,
			GlConst.GL_ONE_MINUS_SRC_ALPHA
		);
	}
	/** 按顺序将命令翻译为图形；同类顶点累积，换批时依次提交。 */
	private void replay(List<DrawCmd> commands, Batch shapes, Batch images) {
		for (var command : commands)
			switch (command.type()) {
				case clear -> {
					// 清屏会把已经攒下的图形一并抹掉，先按顺序画掉
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
					// 贴图在批次提交时才绑定，图标各在各的图集，不先画掉就会被后一张顶掉
					images.flush();
					image(images, pose, color, command);
				}
				case print -> {
					shapes.flush();
					images.flush();
					print(pose, command, color);
				}
				case translate -> pose.translate((float) command.x(), (float) command.y(), 0F);
				// 存的是按步长折算过的整数，还原成倍数
				case scale -> pose.scale((float) (command.x() * GraphicsType.SCALE_STEP), (float) (command.y() * GraphicsType.SCALE_STEP), 1F);
					// 原点在左下、y 朝上，绕 +z 转即逆时针
				case rotate -> pose.mulPose(Axis.ZP.rotationDegrees((float) command.p1()));
				case reset -> pose.setIdentity();
			}
	}
	/** {@code (x, y)} 为左下角的矩形边界；改作中心锚会使 1×1 矩形落在采样格之外。 */
	private record Bounds(double minX, double minY, double maxX, double maxY) {
		private Bounds(DrawCmd command) {
			this(command.x(), command.y(), command.x() + command.p1(), command.y() + command.p2());
		}
	}
	/**
	 * 将画布 alpha 压为不透明。
	 * <p>字体批次以字形覆盖度覆盖 alpha，铺面批次的 fsh 会丢弃 alpha 低于 0.1 的像素，故须压平；
	 * 只能覆盖而不能折算——alpha 乘以任何系数都无法回升到 1。
	 */
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
		// 还原成离屏那套，后面的帧照旧
		blend();
	}
	/** 线段展开为带截面的矩形，两端各沿走向伸出半个线宽。 */
	private static void line(Batch batch, PoseStack pose, int color, double x0, double y0, double x1, double y1, float width) {
		var dx = x1 - x0;
		var dy = y1 - y0;
		var length = Math.sqrt(dx * dx + dy * dy);
		if (length < 1E-6) return;
		// 沿向与垂向各取半个线宽，垂向即沿向转 90°
		var half = width / 2;
		var ux = dx / length * half;
		var uy = dy / length * half;
		quad(batch, pose, color,
			x0 - ux - uy, y0 - uy + ux,
			x0 - ux + uy, y0 - uy - ux,
			x1 + ux + uy, y1 + uy - ux,
			x1 + ux - uy, y1 + uy + ux
		);
	}
	/** 正多边形：实心按三角扇填充，轮廓按边铺斜接四边形。 */
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
				quad(batch, pose, color,
					x + Math.cos(from) * inner, y + Math.sin(from) * inner,
					x + Math.cos(to) * inner, y + Math.sin(to) * inner,
					x + Math.cos(to) * outer, y + Math.sin(to) * outer,
					x + Math.cos(from) * outer, y + Math.sin(from) * outer
				);
				continue;
			}
			var consumer = batch.builder();
			vertex(consumer, pose, color, x, y);
			vertex(consumer, pose, color, x + Math.cos(from) * radius, y + Math.sin(from) * radius);
			vertex(consumer, pose, color, x + Math.cos(to) * radius, y + Math.sin(to) * radius);
		}
	}
	/** 内容图标按当前颜色贴到画布上；认不出内容或它没有图标时什么都不画。 */
	private static void image(Batch batch, PoseStack pose, int color, DrawCmd command) {
		var packed = (int) command.p1() & 0x3FF | (int) command.p4() << 10;
		if (packed < 0) return;
		// 类型 0 是物品、1 是方块，编号按各自的注册表算
		var type = packed & 0x1F;
		var id = packed >> 5;
		var item = type == 1 ? BuiltInRegistries.BLOCK.byId(id).asItem() : BuiltInRegistries.ITEM.byId(id);
		if (item == Items.AIR) return;
		// 取代表图标：物品与方块都有这一张，模型没有单一正面时至少颜色是对的
		var sprite = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0).getParticleIcon(ModelData.EMPTY);
		RenderSystem.setShaderTexture(0, sprite.atlasLocation());
		var rotation = Math.toRadians(command.p3());
		var cos = (float) Math.cos(rotation);
		var sin = (float) Math.sin(rotation);
		var half = (float) command.p2() / 2;
		var x = (float) command.x();
		var y = (float) command.y();
		// 以中心为原点转过之后的四个角，绕一圈；图标的 v 朝下、画布 y 朝上，故上沿取 v1、下沿取 v0
		batch.vertex(pose, color, x + half * -cos + half * sin, y + half * -sin - half * cos, sprite.getU0(), sprite.getV1());
		batch.vertex(pose, color, x + half * cos + half * sin, y + half * sin - half * cos, sprite.getU1(), sprite.getV1());
		batch.vertex(pose, color, x + half * cos - half * sin, y + half * sin + half * cos, sprite.getU1(), sprite.getV0());
		batch.vertex(pose, color, x + half * -cos - half * sin, y + half * -sin + half * cos, sprite.getU0(), sprite.getV0());
	}
	/** 文本按对齐方式摆好，逐行交给字体绘制；换行与行高都由字体自己定。 */
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
			// 字体的 y 朝下，这一行在局部翻一次再画回 y 朝上的画布
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
	/** 轴对齐的实心矩形，角点按左上与右下给。 */
	private static void quad(Batch batch, PoseStack pose, int color, double minX, double minY, double maxX, double maxY) {
		quad(batch, pose, color, minX, minY, maxX, minY, maxX, maxY, minX, maxY);
	}
	private static void vertex(VertexConsumer consumer, PoseStack pose, int color, double x, double y) {
		consumer.addVertex(pose.last(), (float) x, (float) y, 0F).setColor(color);
	}
	/** 尺寸变化时就地改建离屏目标，旧内容按给定像素位移搬入；替换缓冲会摘除贴图名。 */
	public void resize(int width, int height, int dx, int dy) {
		if (matches(width, height)) return;
		var previous = target;
		var previousWidth = this.width;
		var previousHeight = this.height;
		this.width = width;
		this.height = height;
		target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		fillBackground();
		blitFrom(previous, previousWidth, previousHeight, dx, dy);
		// destroyBuffers 会将帧缓冲绑回 0，还原须排在其后
		previous.destroyBuffers();
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** @return 分辨率是否与给定的一致。 */
	public boolean matches(int width, int height) {
		return this.width == width && this.height == height;
	}
	/** 释放离屏缓冲占用的显存，并摘掉贴图管理器里的名字。 */
	public void close() {
		Minecraft.getInstance().getTextureManager().release(location);
		target.destroyBuffers();
		// destroyBuffers 会将帧缓冲绑回 0；淘汰发生于渲染过程中的 BUFFERS.put，不还原则本帧余下绘制落入屏幕帧缓冲
		mc.getMainRenderTarget().bindWrite(true);
	}
	/** @return 画布铺到方块面上使用的批次。 */
	public RenderType renderType() {
		return renderType;
	}
	/**
	 * 将一批命令增量绘制进画布。
	 * <p>投影按画布像素铺开，原点在左下、y 朝上；不做半格平移，移位会使奇数尺寸图形整体偏移。
	 */
	public void apply(List<DrawCmd> commands) {
		var main = mc.getMainRenderTarget();
		target.bindWrite(true);
		var scissor = offscreen();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(
			new Matrix4f().setOrtho(0F, width, 0F, height, -1000, 1000),
			VertexSorting.ORTHOGRAPHIC_Z
		);
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
		// 收尾压平 alpha：字体批次会以字形覆盖度覆盖它，低于 0.1 的边缘会被铺面批次的 alpha 测试丢弃
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
	/** 攒起来的一批顶点。两类图形的顶点格式不同，合不到一起，换批时按顺序各画各的。 */
	private static final class Batch {
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
		/** 单条带纹理的顶点。 */
		private void vertex(PoseStack pose, int color, float x, float y, float u, float v) {
			builder().addVertex(pose.last(), x, y, 0F).setColor(color).setUv(u, v);
		}
		/** @return 本批的顶点出口，调用后这一批就视为非空。 */
		private VertexConsumer builder() {
			used = true;
			return builder;
		}
		private void flush() {
			if (!used) return;
			// 文本那一批走 RenderType，收尾会把混合关掉；不补回来的话，其后画的图形就不参与混合了
			blend();
			RenderSystem.setShader(shader);
			BufferUploader.drawWithShader(builder.buildOrThrow());
			builder = Tesselator.getInstance().begin(mode, format);
			used = false;
		}
	}
}
