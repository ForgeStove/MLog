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

import java.util.List;
import java.util.function.Supplier;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 一块画布的离屏缓冲。
 * <p>命令每变一次就整份重放：画完的内容一直留在缓冲里，逐条增量画反而要额外记录每条命令的落点，
 * 而完整的重放量对显卡来说微不足道。
 */
@OnlyIn(Dist.CLIENT)
public final class DisplayBuffer {
	/** 颜色分量由 0~255 折算到 0~1。 */
	private static final float CHANNEL = 1F / 255F;
	/** 重放开始时的底色，取模型背板那一色，未绘制的像素与背板一致。 */
	private static final int BACKGROUND = 0x565666;
	/** 画布名的序号：同名会互相顶掉。 */
	private static int next;
	/** 画布在贴图管理器中的名字，以及铺到方块面上使用的批次。 */
	private final ResourceLocation location;
	private final RenderType renderType;
	private RenderTarget target;
	private int width, height;
	private int revision = -1;
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
		renderType = RenderType.create(
			"mlog_tile_logic_display_canvas", DefaultVertexFormat.POSITION_TEX, Mode.QUADS, 1536, false, false, CompositeState.builder()
				// 屏幕色不随世界光照，暗处仍可辨识
				.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexShader))
				.setTextureState(new TextureStateShard(location, false, false))
				// 缓冲的 alpha 会被内部混合改坏，按不透明铺开，否则会透出后面的方块
				.setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
				.setCullState(RenderStateShard.NO_CULL)
				.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
				.createCompositeState(false)
		);
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
	/** 按顺序把命令翻译成图形；同一批的顶点攒在一起，换批时按顺序画掉。 */
	private static void replay(List<DrawCmd> commands, PoseStack pose, Batch shapes, Batch images) {
		var color = 0xFFFFFFFF;
		var stroke = 1F;
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
					var bounds = new Bounds(command);
					// 四条边两端各让出半个线宽，四角才拼得上；齐平收尾会缺掉外角那一小块
					var half = stroke / 2;
					line(shapes, pose, color, bounds.minX() - half, bounds.minY(), bounds.maxX() + half, bounds.minY(), stroke);
					line(shapes, pose, color, bounds.maxX(), bounds.minY() - half, bounds.maxX(), bounds.maxY() + half, stroke);
					line(shapes, pose, color, bounds.maxX() + half, bounds.maxY(), bounds.minX() - half, bounds.maxY(), stroke);
					line(shapes, pose, color, bounds.minX(), bounds.maxY() + half, bounds.minX(), bounds.minY() - half, stroke);
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
				// 画布与 MDT 同手性：原点在左下、y 朝上，绕 +z 转正对着看就是逆时针
				case rotate -> pose.mulPose(Axis.ZP.rotationDegrees((float) command.p1()));
				case reset -> pose.setIdentity();
			}
	}
	/** 以 {@code (x, y)} 为中心、按给定宽高算出的矩形边界；MDT 的 {@code Fill.crect} 与 {@code Lines.rect} 都是中心锚。 */
	private record Bounds(double minX, double minY, double maxX, double maxY) {
		private Bounds(DrawCmd command) {
			this(
				command.x() - command.p1() / 2,
				command.y() - command.p2() / 2,
				command.x() + command.p1() / 2,
				command.y() + command.p2() / 2
			);
		}
	}
	/** 一条线段展开成有截面的矩形；两端齐平，不做折角拼接。 */
	private static void line(Batch batch, PoseStack pose, int color, double x0, double y0, double x1, double y1, float width) {
		var dx = x1 - x0;
		var dy = y1 - y0;
		var length = Math.sqrt(dx * dx + dy * dy);
		if (length < 1E-6) return;
		var half = width / 2;
		var ox = -dy / length * half;
		var oy = dx / length * half;
		quad(batch, pose, color, x0 + ox, y0 + oy, x1 + ox, y1 + oy, x1 - ox, y1 - oy, x0 - ox, y0 - oy);
	}
	/** 正多边形：实心的按三角扇铺满，轮廓的按边逐条画线。 */
	private static void regular(DrawCmd command, PoseStack pose, Batch batch, int color, float stroke, boolean outline) {
		var sides = Mth.clamp((int) command.p1(), 3, GraphicsType.MAX_SIDES);
		var radius = command.p2();
		var rotation = Math.toRadians(command.p3());
		var x = command.x();
		var y = command.y();
		var step = Math.PI * 2 / sides;
		for (var i = 0; i < sides; i++) {
			var from = rotation + i * step;
			var to = from + step;
			var fromX = x + Math.cos(from) * radius;
			var fromY = y + Math.sin(from) * radius;
			var toX = x + Math.cos(to) * radius;
			var toY = y + Math.sin(to) * radius;
			if (outline) {
				line(batch, pose, color, fromX, fromY, toX, toY, stroke);
				continue;
			}
			var consumer = batch.builder();
			vertex(consumer, pose, color, x, y);
			vertex(consumer, pose, color, fromX, fromY);
			vertex(consumer, pose, color, toX, toY);
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
	/** 尺寸变化时就地改建离屏目标：替换缓冲会摘掉贴图名，同帧内已使用该批次的格会采样到缺省贴图。 */
	public void resize(int width, int height) {
		if (matches(width, height)) return;
		this.width = width;
		this.height = height;
		target.destroyBuffers();
		target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		revision = -1;
	}
	/** @return 分辨率是否与给定的一致。 */
	public boolean matches(int width, int height) {
		return this.width == width && this.height == height;
	}
	/** 释放离屏缓冲占用的显存，并摘掉贴图管理器里的名字。 */
	public void close() {
		Minecraft.getInstance().getTextureManager().release(location);
		target.destroyBuffers();
	}
	/** @return 画布铺到方块面上使用的批次。 */
	public RenderType renderType() {
		return renderType;
	}
	/** @return 命令序列是否变过，没变就不必重画。 */
	public boolean stale(int revision) {
		return this.revision != revision;
	}
	/**
	 * 整份重放一遍命令。
	 * <p>投影按画布像素铺开，原点在左下角、y 朝上，与 {@code draw} 的坐标系一致（同 MDT，见 {@code LExecutor} 里
	 * 展开 {@code print} 时换行做的是 {@code curY -= lineHeight}）；模型视图与相机无关，推平后再还原。
	 * <p>不做平移，采样点落在半整数上：这样才和 MDT 的 {@code Draw.proj(0, 0, w, h)} 逐像素一致。
	 * 代价是中心压在画布外缘的 1 像素图形画不出来（它唯一落在画布内的采样点就是被规则排除的右/下边缘），
	 * 这是 MDT 本来的行为，别再为了「让它显示出来」把投影挪半格——那会让所有奇数尺寸的图形整体偏一格。
	 */
	public void render(List<DrawCmd> commands, int revision) {
		var main = mc.getMainRenderTarget();
		target.bindWrite(true);
		// 界面上留下的裁剪框会把离屏绘制裁掉一角，画布边上的像素就会缺失；
		// MC 没有查询裁剪是否开着的接口，直接问 GL，画完再按原样恢复
		var scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		RenderSystem.disableScissor();
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
		var pose = new PoseStack();
		var shapes = new Batch(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR, GameRenderer::getPositionColorShader);
		var images = new Batch(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, GameRenderer::getPositionTexColorShader);
		clear(BACKGROUND >> 16 & 0xFF, BACKGROUND >> 8 & 0xFF, BACKGROUND & 0xFF);
		replay(commands, pose, shapes, images);
		shapes.flush();
		images.flush();
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		stack.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();
		// 裁剪框全程没被碰过，重新打开就是原样
		if (scissor) GlStateManager._enableScissorTest();
		main.bindWrite(true);
		this.revision = revision;
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
