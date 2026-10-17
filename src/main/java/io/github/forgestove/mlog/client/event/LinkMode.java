package io.github.forgestove.mlog.client.event;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.render.OutlineRenderer;
import io.github.forgestove.mlog.compat.sable.SableSubLevelPose;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlock.FaceFrame;
import io.github.forgestove.mlog.core.net.LinkPayload;
import io.github.forgestove.mlog.logic.LogicLink;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.lwjgl.glfw.GLFW;

import java.lang.Math;
import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 链接模式仅允许连接一个处理器：左键点击方块建立或断开链接，右键或 ESC 退出。 */
public class LinkMode {
	/** 描边线宽，单位为格；取 1 像素，即 {@code 1/16} 格。 */
	private static final float LINE_W = 1 / 16F;
	/** 链接名下划线厚度，单位为字体像素；此处字体较小，取 2 像素过粗。 */
	private static final float UNDERLINE_H = 1F;
	// 角标为纯白描边，不填充
	private static final int BUTTON_COLOR = 0xFFFFFFFF;
	/** 角标尺寸，单位为格（1 像素为 {@code 1/16} 格）：四角各为 2×2 像素的缺内角角块，外角位于 2.5 像素处。 */
	private static final float MARKER = 5 / 16F, CORNER_LEN = 2 / 16F, CORNER_W = 1 / 16F;
	/** 中心图标宽度，单位为格；取 4 像素，内容略大于角块内缘并覆盖角块一角。 */
	private static final float ICON_WIDTH = 2 / 16F;
	private static final List<Component> EDIT_TIP = List.of(HoverTip.text("gui.mlog.edit"));
	private static @Nullable BlockPos processor;
	// 客户端与服务端均须拦截，否则服务端仍会放置手持方块；该状态由其他监听器先写入，故本方法注册于最低优先级
	public static void onRightClickBlock(RightClickBlock event) {
		var level = event.getLevel();
		var pos = event.getPos();
		if (event.getEntity().isShiftKeyDown()) return;
		// Create 扳手的左键快速拆除会合成一次右键且潜行状态只发给服务端，客户端看到的是未潜行的右键，接受该事件会把拆除改成进入链接模式
		if (event.getSide() == LogicalSide.CLIENT && !mc.options.keyUse.isDown()) return;
		if (!(level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock)) return;
		// 无权限的世界处理器不会开启界面，也不进入链接模式，右键仍须交由方块与物品处理
		if (!accessible(level, pos)) return;
		if (AbstractProcessorBlock.isEditButton(level, pos, event.getHitVec())) return;
		// 取消事件会跳过物品的 useOn
		if (event.getUseBlock().isFalse()) return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		// 链接模式为纯客户端逻辑，服务端只须取消事件
		if (event.getSide() == LogicalSide.CLIENT) start(pos);
	}
	private static boolean accessible(Level level, BlockPos pos) {
		// 仅世界处理器需要权限；玩家信息尚未就绪时不拦截，服务端另有校验
		if (!(level.getBlockState(pos).getBlock() instanceof WorldProcessorBlock)) return true;
		var player = mc.player;
		return player != null && player.canUseGameMasterBlocks();
	}
	public static void start(BlockPos pos) {
		// GUI 的“链接”按钮亦调用此方法，无权限时同样不进入
		if (mc.level == null || !accessible(mc.level, pos)) return;
		processor = pos;
		// 进入链接模式时播放提示音，避免与编辑界面的 uiButton 音效叠加
		LogicSounds.click();
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.hint"), true);
	}	/** 整组图形在按钮所在面上的旋转角及其余弦（45 度时二者相等）；角标按局部坐标构建后统一旋转。 */
	private static final float SPIN_DEGREES = 45F, SPIN_COS = Mth.cos(SPIN_DEGREES * Mth.DEG_TO_RAD);
	public static void onMouseButton(Pre event) {
		// 按下与抬起均触发此方法，仅处理按下，否则一次点击会发送两个数据包
		if (event.getAction() != GLFW.GLFW_PRESS || processor == null || mc.screen != null) return;
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			exit();
			event.setCanceled(true);
			return;
		}
		if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		// 未命中时 mc.hitResult 仍是 BlockHitResult，仅 type 为 MISS
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return;
		// 坐标为目标在其所属空间内的位置
		var target = hit.getBlockPos();
		// 命中处理器自身时退出，并拦截本次点击以免破坏方块
		if (target.equals(processor)) {
			exit();
			event.setCanceled(true);
			return;
		}
		PacketDistributor.sendToServer(new LinkPayload(processor, target, isLinked(target)));
		event.setCanceled(true);
	}
	private static void exit() {
		processor = null;
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.done"), true);
	}
	private static boolean isLinked(BlockPos target) {
		var origin = processor;
		if (origin == null) return false;
		var linked = linksOf(origin);
		return linked != null && linked.stream().anyMatch(link -> link.absolute(origin).equals(target));
	}
	/** 方块未同步至客户端时返回 {@code null}。 */
	private static @Nullable List<LogicLink> linksOf(BlockPos origin) {
		if (mc.level == null || !(mc.level.getBlockEntity(origin) instanceof AbstractProcessorBlockEntity be)) return null;
		// 服务端的链接名与有效性仅在运行中维护，空闲处理器由客户端按世界现算
		be.refreshLinks();
		return be.getLinks();
	}
	/** 为已链接方块描边并在其上方显示链接名；描边框采用方块自身体积，不向外扩展。 */
	public static void onRenderLevel(RenderLevelStageEvent event) {
		// 在天气阶段之后绘制，否则云、雨会遮挡
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var pose = event.getPoseStack();
		// 相机须取自事件，实体渲染器中那份可能已被替换
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		// 事件位姿与其他模组共用，压一层干净位姿后只依赖世界坐标与相机
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		renderEditButton(pose, cam);
		var origin = processor;
		if (origin != null) {
			// 方块位于子层级内时坐标为 plot 坐标，须整体位姿变换
			var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(origin), cam);
			var camera = local != null ? local : cam;
			// 连接范围是以处理器为中心、三轴各 ±linkRange 格的立方体；灰色粗框在下、主色细框在上
			var range = linkRange(origin);
			if (range != null && range > 0) OutlineRenderer.renderOutlinedBox(pose, camera, new AABB(origin).inflate(range), GRAY, ACCENT);
			// 该层不测深度，须先绘制，后续描边框才能覆盖其上
			OutlineRenderer.renderBox(pose, camera, shapeBox(origin), LINE_W, ACCENT);
			// 处理器位姿须先弹出，否则链接目标会受处理器朝向影响
			if (local != null) pose.popPose();
			// 链接目标按所属空间绘制，跨空间时需各自压入位姿
			var linked = linksOf(origin);
			if (linked != null) for (var link : linked) {
				var pos = link.absolute(origin);
				var targetLocal = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(pos), cam);
				var targetCamera = targetLocal != null ? targetLocal : cam;
				var boxColor = link.valid() ? PLACE : TEXT_DIM;
				OutlineRenderer.renderBox(pose, targetCamera, shapeBox(pos), LINE_W, boxColor);
				if (targetLocal != null) pose.popPose();
				// 名称须在世界坐标中绘制，压入位姿会使文字倾斜
				renderLinkName(
					pose,
					cam,
					buffers,
					SableSubLevelPose.toWorld(Vec3.atLowerCornerOf(pos).add(0.5, 1.3, 0.5)),
					link.name(),
					link.valid() ? ACCENT : TEXT_DIM
				);
			}
		}
		pose.popPose();
		buffers.endBatch();
	}
	/** 不限范围、该格不是处理器或方块未加载时返回 {@code null}。 */
	private static @Nullable Integer linkRange(BlockPos pos) {
		var level = mc.level;
		return level != null && level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock block ? block.linkRange() : null;
	}
	/** 方块形状的世界坐标包围盒；用 {@code getShape} 取 {@code FACING} 旋转后的形状，为空（空气或区块未加载）时退回整个方块体积。 */
	private static AABB shapeBox(BlockPos pos) {
		var level = mc.level;
		if (level == null) return new AABB(pos);
		var shape = level.getBlockState(pos).getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}
	private static void renderEditButton(PoseStack pose, Vec3 cam) {
		var level = mc.level;
		if (level == null) return;
		var face = faceUnderCrosshair();
		var button = buttonUnderCrosshair();
		var at = face != null ? face : button;
		if (at == null) return;
		// 方块位于子层级内时进行位姿变换，按 plot 坐标绘制
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(at), cam);
		var camera = local != null ? local : cam;
		// 准星指向按钮所在面即绘制铅笔图标，无须正压按钮
		if (face != null) renderIcon(pose, camera, AbstractProcessorBlock.buttonFrame(level, face));
		// 角标仅在准星正压在按钮上时绘制
		if (button != null) {
			var frame = AbstractProcessorBlock.buttonFrame(level, button);
			var flat = pose.last();
			var half = MARKER / 2F;
			renderCorner(flat, camera, frame, -half, -half, 1, 1);
			renderCorner(flat, camera, frame, half, -half, -1, 1);
			renderCorner(flat, camera, frame, -half, half, 1, -1);
			renderCorner(flat, camera, frame, half, half, -1, -1);
		}
		if (local != null) pose.popPose();
	}
	/** {@code at} 须为世界坐标且不得压入子层级位姿，否则结构旋转会使文字倾斜。 */
	private static void renderLinkName(PoseStack pose, Vec3 cam, MultiBufferSource buffers, Vec3 at, String name, int color) {
		var font = mc.font;
		var text = LogicFont.literal(name);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		// 顶点还要乘全局视图矩阵，取其旋转的逆使字形落回屏幕平面
		var view = new Quaternionf().setFromNormalized(new Matrix3f(RenderSystem.getModelViewMatrix()));
		pose.mulPose(view.conjugate());
		// 字号采用名称标签比例；x 不可取负，再次翻转会导致文字镜像
		pose.scale(0.025F, -0.025F, 0.025F);
		var matrix = pose.last().pose();
		var x = -width / 2F;
		font.drawInBatch(
			LogicFont.outlineShift(text),
			x,
			0F,
			color,
			false,
			matrix,
			buffers,
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		// 下划线：描边位于外圈，与正文不重叠
		var lineY = font.lineHeight;
		OutlineRenderer.renderFrame(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, 1F, LogicFont.outlineColor(color));
		OutlineRenderer.renderRect(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, color);
		pose.popPose();
	}
	/** 判定范围比角标宽一档：准星指向该面即绘制铅笔图标。 */
	private static @Nullable BlockPos faceUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		var pos = hit.getBlockPos();
		return hit.getDirection() == level.getBlockState(pos).getValue(AbstractProcessorBlock.FACING) ? pos : null;
	}
	private static void renderCorner(Pose flat, Vec3 cam, FaceFrame frame, float x, float y, float dx, float dy) {
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_LEN, y + dy * CORNER_W);
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_W, y + dy * CORNER_LEN);
	}
	/** 姿态取自 {@link FaceFrame#rotation()}，并绕面内法向旋转 {@link #SPIN_DEGREES} 度；字形尺寸由字体固定，按目标宽度反推缩放。 */
	private static void renderIcon(PoseStack pose, Vec3 cam, FaceFrame frame) {
		var icon = LogicIcons.PENCIL.component();
		var width = LogicFont.width(icon);
		var scale = ICON_WIDTH / width;
		var center = frame.center();
		pose.pushPose();
		pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
		pose.mulPose(frame.rotation());
		// 绕局部 -z 旋转，自面外侧观察为逆时针
		pose.mulPose(Axis.ZN.rotationDegrees(SPIN_DEGREES));
		pose.scale(scale, scale, scale);
		mc.font.drawInBatch(
			icon,
			-width / 2F,
			// 高度为 0 的容器居中，即字形中心位于原点
			LogicIcons.centerY(0, 0),
			BUTTON_COLOR,
			false,
			pose.last().pose(),
			mc.renderBuffers().bufferSource(),
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		pose.popPose();
	}
	private static void renderPatch(Pose flat, Vec3 cam, FaceFrame frame, float x0, float y0, float x1, float y1) {
		var minX = Math.min(x0, x1);
		var minY = Math.min(y0, y1);
		var maxX = Math.max(x0, x1);
		var maxY = Math.max(y0, y1);
		OutlineRenderer.renderQuad(
			flat,
			cam,
			BUTTON_COLOR,
			spin(frame, minX, minY),
			spin(frame, maxX, minY),
			spin(frame, maxX, maxY),
			spin(frame, minX, maxY)
		);
	}
	private static Vec3 spin(FaceFrame frame, float x, float y) {
		return frame.point((x - y) * SPIN_COS, (x + y) * SPIN_COS);
	}
	/** 拦截 ESC 打开暂停菜单，改为退出链接模式。 */
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
	public static void onClientTick(Post ignoredEvent) {
		if (buttonUnderCrosshair() != null) HoverTip.show(EDIT_TIP);
	}
	private static @Nullable BlockPos buttonUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		return AbstractProcessorBlock.isEditButton(level, hit.getBlockPos(), hit) ? hit.getBlockPos() : null;
	}
	/** 旁观、潜行、冒险、距离不足或无权限时返回 {@code null}。 */
	private static @Nullable BlockHitResult hitOnProcessor() {
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) return null;
		if (player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) return null;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return null;
		var pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock)) return null;
		if (!accessible(level, pos)) return null;
		return hit;
	}

}
