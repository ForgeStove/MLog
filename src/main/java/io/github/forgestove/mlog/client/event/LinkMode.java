package io.github.forgestove.mlog.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.render.OutlineRenderer;
import io.github.forgestove.mlog.compat.sable.SableSubLevelPose;
import io.github.forgestove.mlog.content.microprocessor.*;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlock.FaceFrame;
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
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;

/** 链接模式：左键点击方块建立或断开链接，右键或 ESC 退出。同一时间仅允许连接一个处理器。 */
@OnlyIn(Dist.CLIENT)
public final class LinkMode {
	/** 描边线宽，单位为格。一格为 16 像素，此处取 1 像素。 */
	private static final float LINE_W = 1 / 16F;
	/**
	 * 链接名下划线厚度，单位为字体像素。
	 * <p>不使用 GUI 的 {@code UNDERLINE_H}：GUI 字号较大，此处字体较小，2 像素会显得过重。
	 */
	private static final float UNDERLINE_H = 1F;
	/**
	 * 编辑按钮颜色。
	 * <p>角标为纯白描边，不填充。
	 */
	private static final int BUTTON_COLOR = 0xFFFFFFFF;
	/**
	 * 角标尺寸：方框边长、臂长与臂厚，单位为格（1 像素为 {@code 1/16} 格）。
	 * <p>四角各为一个 2×2 像素的缺内角角块，外角位于 2.5 像素处，并朝中心伸出两条臂。
	 * 臂不延伸至中心，以避免四角连成完整边框。
	 */
	private static final float MARKER = 5 / 16F, CORNER_LEN = 2 / 16F, CORNER_W = 1 / 16F;
	/**
	 * 中心图标宽度，单位为格。
	 * <p>取 4 像素：内容略大于角块内缘并覆盖角块一角，仅在外侧保留半个像素。
	 */
	private static final float ICON_WIDTH = 2 / 16F;
	/** 准星停留在按钮上时，屏幕底部显示的提示。 */
	private static final List<Component> EDIT_TIP = List.of(HoverTip.text("gui.mlog.edit"));
	private static @Nullable BlockPos processor;
	/**
	 * 右键处理器：命中编辑按钮时不拦截，交由方块自身开启界面；其余位置进入链接模式。
	 * <p>客户端与服务端均须拦截。若仅客户端拦截，服务端仍会放置手持方块，导致方块被放置并进入链接模式。
	 * <p>潜行时不拦截，以便向处理器放置方块或使用物品。
	 * <p>其他模组将事件的 {@code useBlock} 置为 {@code FALSE} 时同样不拦截：该状态表示跳过方块自身交互，交由物品处理。
	 * 该判断依赖其他监听器先写入状态，因此本方法注册于最低优先级，见 {@code MLogClient}。
	 */
	public static void onRightClickBlock(RightClickBlock event) {
		var level = event.getLevel();
		var pos = event.getPos();
		if (event.getEntity().isShiftKeyDown()) return;
		// Create 扳手的左键快速拆除由一次合成的右键触发：玩家按下左键，潜行状态仅发送至服务端，
		// 客户端会观察到一次未潜行的右键。若接受该事件，会将拆除方块转为进入链接模式，因此仅处理玩家实际按下的右键。
		if (event.getSide() == LogicalSide.CLIENT && !mc.options.keyUse.isDown()) return;
		if (!(level.getBlockState(pos).getBlock() instanceof MicroProcessorBlock)) return;
		// 无权限的世界处理器（非 OP）不拦截：此类处理器不会开启界面，也不进入链接模式，
		// 右键仍交由其他逻辑处理，如手持方块的使用。
		if (!accessible(level, pos)) return;
		// 命中编辑按钮时不拦截，交由方块自身开启界面。
		if (MicroProcessorBlock.isEditButton(level, pos, event.getHitVec())) return;
		// 若事件声明本次交互由物品处理，同样不拦截：不取消事件则原版流程继续，物品的 useOn 正常执行。
		if (event.getUseBlock().isFalse()) return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		// 链接模式为纯客户端逻辑，服务端取消事件即可。
		if (event.getSide() == LogicalSide.CLIENT) start(pos);
	}
	/**
	 * 整组图形在按钮所在面上的旋转角度及其余弦（45 度时余弦等于正弦）。
	 * <p>角标按局部坐标构建后统一旋转，因此调整该角度时角标与图标会一同旋转。
	 */
	private static final float SPIN_DEGREES = 45F, SPIN_COS = Mth.cos(SPIN_DEGREES * Mth.DEG_TO_RAD);
	/** @return 玩家是否可操作该处理器。世界处理器与命令方块相同，仅 OP 可操作。 */
	private static boolean accessible(Level level, BlockPos pos) {
		// 仅世界处理器需要权限；玩家信息尚未就绪时不拦截，服务端另有校验。
		if (!(level.getBlockState(pos).getBlock() instanceof WorldProcessorBlock)) return true;
		var player = mc.player;
		return player != null && player.canUseGameMasterBlocks();
	}
	public static void start(BlockPos pos) {
		// GUI 的“链接”按钮亦调用此方法；无权限时同样不进入。
		if (mc.level == null || !accessible(mc.level, pos)) return;
		processor = pos;
		// 进入链接模式时播放提示音（默认 Sounds.click）。
		// 在此处播放而非右键处理中：点击编辑按钮会开启界面，该路径已有 uiButton 音效，避免叠加。
		LogicSounds.click();
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.hint"), true);
	}
	public static void onMouseButton(Pre event) {
		// 按下与抬起均会触发此方法，仅处理按下事件：否则一次点击会发送两个数据包，第二个将命中“已经链接”状态。
		if (event.getAction() != GLFW.GLFW_PRESS || processor == null || mc.screen != null) return;
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			exit();
			event.setCanceled(true);
			return;
		}
		if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		// 未命中时 mc.hitResult 仍可能是 BlockHitResult，仅 type 为 MISS，无法通过 instanceof 排除。
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return;
		// 坐标为目标在其所属空间内的位置，空间判定由服务端负责。
		var target = hit.getBlockPos();
		// 命中处理器自身：视为选取完成，退出链接模式，并拦截本次点击以避免破坏方块。
		if (target.equals(processor)) {
			exit();
			event.setCanceled(true);
			return;
		}
		// 已链接的目标再次点击时断开链接。
		PacketDistributor.sendToServer(new LinkPayload(processor, target, isLinked(target)));
		event.setCanceled(true);
	}
	private static void exit() {
		processor = null;
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.done"), true);
	}
	/** @return 目标是否已链接至当前处理器。 */
	private static boolean isLinked(BlockPos target) {
		var origin = processor;
		if (origin == null) return false;
		var linked = linksOf(origin);
		return linked != null && linked.stream().anyMatch(link -> link.absolute(origin).equals(target));
	}
	/** @return 指定处理器的链接列表；方块未同步至客户端时返回 {@code null}。 */
	private static @Nullable List<LogicLink> linksOf(BlockPos origin) {
		return mc.level != null && mc.level.getBlockEntity(origin) instanceof MicroProcessorBlockEntity be ? be.getLinks() : null;
	}
	/**
	 * 为已链接方块绘制描边，并在其上方显示链接名。
	 * <p>描边框采用方块自身体积，不向外扩展。
	 */
	public static void onRenderLevel(RenderLevelStageEvent event) {
		// 在天气阶段之后绘制：云、雨等在半透明方块之后绘制，过早绘制会被其遮挡。
		// 注意：LevelRenderer#renderClouds 会为 pose 叠加与相机相关的平移（poseStack.translate(-camX, …, -camZ)）。
		// 若再次出现描边框随相机移动的现象，应检查此处，并将阶段提前至 AFTER_TRANSLUCENT_BLOCKS。
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		// 编辑按钮随准星显示，与链接模式是否启用无关。
		renderEditButton(pose, cam);
		var origin = processor;
		if (origin != null) {
			// 方块位于子层级内时，坐标为 plot 坐标：整体进行一次位姿变换，后续按 plot 坐标绘制。
			var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(origin), cam);
			var camera = local != null ? local : cam;
			// 连接范围：以处理器为中心、三个轴各 ±RANGE 格的立方体，判定与绘制使用同一形状。
			// 灰色粗框作为底层，主色细框叠加其上。
			// 范围受 LogicLink.RANGE 限制，始终绘制。
			OutlineRenderer.renderOutlinedBox(pose, camera, new AABB(origin).inflate(LogicLink.RANGE), GRAY, ACCENT);
			// 为处理器自身绘制描边，以与周围链接目标区分。
			// 球面为不测深度的覆盖层，需先绘制，后续描边框才能稳定覆盖其上。
			OutlineRenderer.renderBox(pose, camera, shapeBox(origin), LINE_W, ACCENT);
			// 处理器位姿须先弹出：链接目标位于其他空间时按世界坐标绘制，保留该位姿会受处理器朝向影响。
			if (local != null) pose.popPose();
			// 链接目标按所属空间绘制：跨空间时与处理器坐标系不同，需分别压入位姿。
			var linked = linksOf(origin);
			if (linked != null) for (var link : linked) {
				var pos = link.absolute(origin);
				var targetLocal = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(pos), cam);
				var targetCamera = targetLocal != null ? targetLocal : cam;
				// 失效链接使用暗色：框与名称均更换颜色，正常链接保持原色。
				var boxColor = link.valid() ? PLACE : TEXT_DIM;
				// 链接目标同样按形状绘制，与处理器描边保持一致。
				OutlineRenderer.renderBox(pose, targetCamera, shapeBox(pos), LINE_W, boxColor);
				if (targetLocal != null) pose.popPose();
				// 名称在世界坐标中绘制：压入位姿后朝向会带有结构旋转，导致文字倾斜。
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
		buffers.endBatch();
	}
	/**
	 * @return 方块形状的包围盒（世界坐标），用于描边。
	 * <p>使用 {@code getShape} 而非整个方块体积：模型尺寸决定包围盒大小，朝向变化时形状随之旋转
	 * （处理器形状即按 {@code FACING} 旋转后的形状）。
	 * <p>形状为空（空气或区块未加载）时退回整个方块体积。链接目标可能位于未加载区块中，
	 * 此时至少仍可绘制一个框。
	 */
	private static AABB shapeBox(BlockPos pos) {
		var level = mc.level;
		if (level == null) return new AABB(pos);
		var shape = level.getBlockState(pos).getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}
	/**
	 * 绘制编辑按钮：准星指向该面时显示铅笔图标，准星正压在按钮上时绘制角标
	 * （分别由 {@link #faceUnderCrosshair()} 与 {@link #buttonUnderCrosshair()} 判断）。
	 * <p>按钮位于处理器 {@code FACING} 面，位置与朝向由 {@link FaceFrame} 提供。
	 * <p>框尺寸有 4 / 6 / 8 像素档位，此处内容宽度不超过图标，采用 6PX 档。
	 */
	private static void renderEditButton(PoseStack pose, Vec3 cam) {
		var level = mc.level;
		if (level == null) return;
		var face = faceUnderCrosshair();
		var button = buttonUnderCrosshair();
		var at = face != null ? face : button;
		if (at == null) return;
		// 与链接框一致：方块位于子层级内时进行位姿变换，后续按 plot 坐标绘制。
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(at), cam);
		var camera = local != null ? local : cam;
		// 铅笔图标：准星指向按钮所在面时绘制，无需正压在按钮上。
		if (face != null) renderIcon(pose, camera, MicroProcessorBlock.buttonFrame(level, face));
		// 角标：仅在准星正压在按钮上时绘制，与底部提示条件相同。
		if (button != null) {
			var frame = MicroProcessorBlock.buttonFrame(level, button);
			var flat = pose.last();
			var half = MARKER / 2F;
			renderCorner(flat, camera, frame, -half, -half, 1, 1);
			renderCorner(flat, camera, frame, half, -half, -1, 1);
			renderCorner(flat, camera, frame, -half, half, 1, -1);
			renderCorner(flat, camera, frame, half, half, -1, -1);
		}
		if (local != null) pose.popPose();
	}
	/**
	 * 在 {@code at} 上方绘制链接名，正面朝向相机，与原版名称标签一致；{@code color} 同时用于正文与下划线。
	 * <p>坐标须为世界坐标，且不得压入子层级位姿，否则结构旋转会与朝向复合，导致文字倾斜。
	 * <p>字体使用界面字体（{@link LogicFont}），描边采用同一字体的膨胀字形，一次绘制即可得到描边与正文。
	 */
	private static void renderLinkName(PoseStack pose, Vec3 camera, MultiBufferSource buffers, Vec3 at, String name, int color) {
		var font = mc.font;
		var text = LogicFont.literal(name);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		// 字号采用名称标签比例。x 不可取负：相机朝向四元数已旋转一次，再次翻转会导致文字镜像。
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
		// 下方补充一条横线。描边位于外圈，与正文不重叠，且同一深度下不冲突。
		var lineY = font.lineHeight;
		OutlineRenderer.renderFrame(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, 1F, LogicFont.outlineColor(color));
		OutlineRenderer.renderRect(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, color);
		pose.popPose();
	}
	/** @return 准星位于编辑按钮所在面时对应的处理器。判定范围比角标宽一档：只要指向该面即绘制铅笔图标。 */
	private static @Nullable BlockPos faceUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		var pos = hit.getBlockPos();
		return hit.getDirection() == level.getBlockState(pos).getValue(MicroProcessorBlock.FACING) ? pos : null;
	}
	/**
	 * 绘制一个角的角标：{@code (x, y)} 为外角在按钮局部坐标系中的位置（原点位于按钮中心），
	 * 朝 {@code (dx, dy)} 方向伸出两条臂。
	 * <p>每条臂均拼接为矩形，以保证外角为实心。若使用带中心线的线段绘制，线仅覆盖角点两侧各半个线宽，
	 * 角外侧会缺失半格，形成角部空缺而边缘有线的情况。
	 */
	private static void renderCorner(Pose flat, Vec3 cam, FaceFrame frame, float x, float y, float dx, float dy) {
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_LEN, y + dy * CORNER_W);
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_W, y + dy * CORNER_LEN);
	}
	/**
	 * 将铅笔图标置于按钮中心，并贴合按钮所在面。
	 * <p>姿态由 {@link FaceFrame#rotation()} 提供：pose 的 XY 平面即为该面，
	 * 文字 y 轴向下与之相符，自面外侧观察方向为正。
	 * <p>该姿态还需绕面内法向旋转 {@link #SPIN_DEGREES} 度，以与角标保持一致。
	 */
	private static void renderIcon(PoseStack pose, Vec3 cam, FaceFrame frame) {
		var icon = LogicIcons.PENCIL.component();
		var width = LogicFont.width(icon);
		// 字形尺寸由字体固定，根据目标宽度反推缩放，更换字形时无需重新调整字号。
		var scale = ICON_WIDTH / width;
		var center = frame.center();
		pose.pushPose();
		pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
		pose.mulPose(frame.rotation());
		// 绕局部 -z（面朝外一侧）旋转：自面外侧观察为逆时针，与贴合顶面时一致。
		pose.mulPose(Axis.ZN.rotationDegrees(SPIN_DEGREES));
		pose.scale(scale, scale, scale);
		mc.font.drawInBatch(
			icon,
			-width / 2F,
			// 基线取值与界面一致：高度为 0 的容器居中，等价于字形中心位于原点。
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
	/** 将局部坐标系中的矩形旋转 {@link #SPIN_DEGREES} 度后绘制到该面上；两个角点顺序无关。 */
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
	/** @return 按钮局部坐标（原点位于按钮中心）绕中心旋转 {@link #SPIN_DEGREES} 度后的世界坐标。 */
	private static Vec3 spin(FaceFrame frame, float x, float y) {
		return frame.point((x - y) * SPIN_COS, (x + y) * SPIN_COS);
	}
	/** ESC 会打开暂停菜单，此处拦截该行为并改为退出链接模式。 */
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
	/** 准星停留在按钮上时通知 {@link HoverTip} 续期提示，计时由 {@link HoverTip} 自行结束。 */
	public static void onClientTick(Post ignoredEvent) {
		if (buttonUnderCrosshair() != null) HoverTip.show(EDIT_TIP);
	}
	/**
	 * @return 准星正压在编辑按钮上时对应的处理器；未命中时返回 {@code null}。
	 * <p>按钮为贴在该面中心的小块区域，仅命中该区域才视为可点击，角标与底部提示均以此为准。
	 */
	private static @Nullable BlockPos buttonUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		return MicroProcessorBlock.isEditButton(level, hit.getBlockPos(), hit) ? hit.getBlockPos() : null;
	}
	/** @return 准星指向处理器时的命中结果；条件不满足（旁观、潜行、冒险、距离不足、无权限）时返回 {@code null}。 */
	private static @Nullable BlockHitResult hitOnProcessor() {
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) return null;
		if (player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) return null;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return null;
		var pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof MicroProcessorBlock)) return null;
		// 无权限的世界处理器亦不绘制编辑按钮，与点击时的判断一致。
		if (!accessible(level, pos)) return null;
		return hit;
	}

}