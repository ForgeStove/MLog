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
/** 链接模式：左键点方块建立链接，再点一次断开，右键或 ESC 退出。同一时间只能给一个处理器链接。 */
@OnlyIn(Dist.CLIENT)
public final class LinkMode {
	/** 描边线宽，单位是格：一格 16 像素，这里取一个像素。 */
	private static final float LINE_W = 1 / 16F;
	/**
	 * 链接名下划线的厚度，单位是字体像素。
	 * <p>不直接拿界面里的 {@code UNDERLINE_H}：那边是按 GUI 字号定的，这里的字小一圈，2 像素显得笨重。
	 */
	private static final float UNDERLINE_H = 1F;
	/**
	 * 编辑按钮的颜色。
	 * <p>角标是纯白的一圈、不填底。
	 */
	private static final int BUTTON_COLOR = 0xFFFFFFFF;
	/**
	 * 角标：围出的方框边长、每条臂的长度与厚度，单位是格（一像素是 {@code 1/16}）。
	 * <p>四个角各是一个 2×2 缺内角的角块，
	 * 外角落在 2.5 像素处，朝中心伸出两条臂；臂够不到中心，四个角才不会连成一整圈框。
	 */
	private static final float MARKER = 5 / 16F, CORNER_LEN = 2 / 16F, CORNER_W = 1 / 16F;
	/**
	 * 中间那个图标的宽度，单位是格。
	 * <p>取四像素：内容比角块的内缘大一圈、压住角块一角，只在外侧留半个像素露出来。
	 */
	private static final float ICON_WIDTH = 2 / 16F;
	/** 准星停在按钮上时屏幕底部那行提示。 */
	private static final List<Component> EDIT_TIP = List.of(HoverTip.text("gui.mlog.edit"));	/**
	 * 整组图形在按钮所在的那个面上，绕按钮中心转过的角度，以及它的余弦（四十五度时余弦等于正弦）。
	 * <p>角标按局部坐标拼好再转过来，所以这里改角度，角标和图标会一起跟着转。
	 */
	private static final float SPIN_DEGREES = 45F, SPIN_COS = Mth.cos(SPIN_DEGREES * Mth.DEG_TO_RAD);
	private static @Nullable BlockPos processor;
	/**
	 * 右键处理器：命中编辑按钮时不拦截（交由方块自身开启界面），其余位置进入链接模式。
	 * <p><b>两端</b>均须拦截：仅客户端拦截时，服务端仍会放置手持方块，导致方块被放置且进入链接模式。
	 * <p>潜行时不拦截：需向处理器放置方块或使用物品时潜行即可。
	 * <p>其他模组将事件的 {@code useBlock} 置为 {@code FALSE} 时同样不拦截：该三态表示跳过方块自身的交互，
	 * 交由物品处理。
	 * 该判断依赖其他监听器先写入状态，故本方法注册在最低优先级上——见 {@code MLogClient}。
	 */
	public static void onRightClickBlock(RightClickBlock event) {
		var level = event.getLevel();
		var pos = event.getPos();
		if (event.getEntity().isShiftKeyDown()) return;
		// Create 扳手的左键快速拆除是拿一次合成的右键做的：玩家按的是左键，潜行只发给了服务端，
		// 客户端这边看着就是一次没潜行的右键。认下来会把拆掉方块变成进链接模式，所以只认玩家真按下的右键
		if (event.getSide() == LogicalSide.CLIENT && !mc.options.keyUse.isDown()) return;
		if (!(level.getBlockState(pos).getBlock() instanceof MicroProcessorBlock)) return;
		// 无权限的世界处理器（非 OP）不拦截：界面本就不会开启，此处亦不进入链接模式，
		// 右键照常交由其他处理（如手持方块的使用）
		if (!accessible(level, pos)) return;
		// 命中按钮则不拦截，交由方块自身开启界面
		if (MicroProcessorBlock.isEditButton(level, pos, event.getHitVec())) return;
		// 模组声明本次交互归其物品处理，同样不拦截：不取消则原版流程继续，物品的 useOn 照常执行
		if (event.getUseBlock().isFalse()) return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		// 链接模式是纯客户端的，服务端那边拦下就够了
		if (event.getSide() == LogicalSide.CLIENT) start(pos);
	}
	/** @return 玩家能不能操作这个处理器。世界处理器和命令方块一样只有 OP 能碰。 */
	private static boolean accessible(Level level, BlockPos pos) {
		// 只有世界处理器要权限；玩家信息还没就位时不拦，服务端那边还有一道
		if (!(level.getBlockState(pos).getBlock() instanceof WorldProcessorBlock)) return true;
		var player = mc.player;
		return player != null && player.canUseGameMasterBlocks();
	}
	public static void start(BlockPos pos) {
		// 界面上的「链接」按钮也走这里，没权限同样不进去
		if (mc.level == null || !accessible(mc.level, pos)) return;
		processor = pos;
		// 进链接模式的那一声（默认 Sounds.click）。
		// 放在这里而不是右键那里：点编辑按钮走的是开界面那条路，那边有 uiButton，别再叠一声
		LogicSounds.click();
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.hint"), true);
	}
	public static void onMouseButton(Pre event) {
		// 按下与抬起都会走到这里，只认按下：不然一次点击会发两个包，第二个撞上「已经链接过了」
		if (event.getAction() != GLFW.GLFW_PRESS || processor == null || mc.screen != null) return;
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			exit();
			event.setCanceled(true);
			return;
		}
		if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		// 打空时 mc.hitResult 也是 BlockHitResult，只是 type 为 MISS，靠 instanceof 拦不住
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return;
		// 坐标即目标在其所属空间内的位置，空间判定由服务端负责
		var target = hit.getBlockPos();
		// 命中的是处理器自身：视为选取完毕，退出链接模式，同时拦截本次点击以免破坏方块
		if (target.equals(processor)) {
			exit();
			event.setCanceled(true);
			return;
		}
		// 已经链接过的再点一次就断开
		PacketDistributor.sendToServer(new LinkPayload(processor, target, isLinked(target)));
		event.setCanceled(true);
	}
	private static void exit() {
		processor = null;
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("gui.mlog.link.done"), true);
	}
	/** @return 目标是否已经链接到当前处理器。 */
	private static boolean isLinked(BlockPos target) {
		var origin = processor;
		if (origin == null) return false;
		var linked = linksOf(origin);
		return linked != null && linked.stream().anyMatch(link -> link.absolute(origin).equals(target));
	}
	/** @return 指定处理器的链接，方块没同步到客户端时返回 {@code null}。 */
	private static @Nullable List<LogicLink> linksOf(BlockPos origin) {
		return mc.level != null && mc.level.getBlockEntity(origin) instanceof MicroProcessorBlockEntity be ? be.getLinks() : null;
	}
	/**
	 * 给已链接的方块描一圈边，并在它上方写出链接名。
	 * <p>框就是方块本身的体积，不往外扩。
	 */
	public static void onRenderLevel(RenderLevelStageEvent event) {
		// 画在天气之后：云、雨这些都是半透明块之后才画的，早一步就会被它们糊住。
		// 注意：LevelRenderer#renderClouds 里会给 pose 压一个跟相机有关的平移（poseStack.translate(-camX, …, -camZ)），
		// 上次在这里画的时候出现过「框跟着人走」，如果又看到了，问题就在这——那时得往前挪回 AFTER_TRANSLUCENT_BLOCKS
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		// 编辑按钮跟着准星走，跟链接模式在不在没关系
		renderEditButton(pose, cam);
		var origin = processor;
		if (origin != null) {
			// 方块位于子层级内时坐标为 plot 坐标：整体按位姿变换一次，后续均按 plot 坐标绘制
			var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(origin), cam);
			var camera = local != null ? local : cam;
			// 连接范围：以处理器为中心、三个轴各 ±RANGE 格的立方体，判定和画法用的是同一个形状。
			// 灰粗框垫底、主色细框压上。
			// 范围受 LogicLink.RANGE 限制，始终要画
			OutlineRenderer.renderOutlinedBox(pose, camera, new AABB(origin).inflate(LogicLink.RANGE), GRAY, ACCENT);
			// 处理器自己描一圈，好和周围的链接目标区分开。
			// 球面是不测深度的覆盖层，先画它，后面的框才能稳稳压在球上面
			OutlineRenderer.renderBox(pose, camera, shapeBox(origin), LINE_W, ACCENT);
			// 处理器的位姿须先弹出：链接目标在别的空间时按世界坐标绘制，留着这层会被处理器的朝向带偏
			if (local != null) pose.popPose();
			// 链接目标按所属空间绘制：跨空间时与处理器坐标系不同，须各自压入位姿
			var linked = linksOf(origin);
			if (linked != null) for (var link : linked) {
				var pos = link.absolute(origin);
				var targetLocal = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(pos), cam);
				var targetCamera = targetLocal != null ? targetLocal : cam;
				// 失效的链接改用暗色：框与名字都换，正常时各自保持原色
				var boxColor = link.valid() ? PLACE : TEXT_DIM;
				// 链接目标也按形状画，和处理器那一圈同一个口径
				OutlineRenderer.renderBox(pose, targetCamera, shapeBox(pos), LINE_W, boxColor);
				if (targetLocal != null) pose.popPose();
				// 名字画在世界坐标里：压过位姿后朝向会带上结构旋转，文字摆不正
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
	 * @return 方块形状的包围盒（世界坐标），描边用。
	 * 	<p>取的是 {@code getShape} 而不是整个方块体积：模型多高多宽盒子就多大，朝向变了形状也跟着转
	 * 	（处理器的形状就是按 {@code FACING} 转过的那份）。
	 * 	<p>形状为空（空气，或者区块还没加载）时退回整个方块体积——链接目标可能在没加载的区块里，
	 * 	那种时候至少还画得出一个框。
	 */
	private static AABB shapeBox(BlockPos pos) {
		var level = mc.level;
		if (level == null) return new AABB(pos);
		var shape = level.getBlockState(pos).getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}
	/**
	 * 画出编辑按钮：铅笔图标指着那一面就显示，角标只有正压在按钮上才画（{@link #faceUnderCrosshair()} /
	 * {@link #buttonUnderCrosshair()} 各管一头）。
	 * <p>按钮贴在处理器 {@code FACING} 那一面上，位置和朝向都由 {@link FaceFrame} 给。
	 * <p>框的大小有 4 / 6 / 8 像素几档，这里的内容不比图标宽，取 6PX 那一档。
	 */
	private static void renderEditButton(PoseStack pose, Vec3 cam) {
		var level = mc.level;
		if (level == null) return;
		var face = faceUnderCrosshair();
		var button = buttonUnderCrosshair();
		var at = face != null ? face : button;
		if (at == null) return;
		// 与链接框一致：方块位于子层级内时按位姿变换，后续按 plot 坐标绘制
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(at), cam);
		var camera = local != null ? local : cam;
		// 铅笔：指着按钮所在的那一面就画，不用非得压在那小块按钮上
		if (face != null) renderIcon(pose, camera, MicroProcessorBlock.buttonFrame(level, face));
		// 角标：仍旧只有正压在按钮上才画，和底部那行提示同一个条件
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
	 * 将链接名绘制于 {@code at} 上方，正面朝向相机，与原版名字标签一致，{@code color} 同时用于正文与下划线。
	 * <p>坐标须为世界坐标且不得压入子层级位姿，否则结构旋转会与朝向复合、文字倾斜。
	 * <p>字体取界面所用的一套（{@link LogicFont}），描边走同一字体的膨胀字形：一次画完即得环与正文。
	 */
	private static void renderLinkName(PoseStack pose, Vec3 camera, MultiBufferSource buffers, Vec3 at, String name, int color) {
		var font = mc.font;
		var text = LogicFont.literal(name);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		// 字号取名字标签那个比例。x 不能取负：相机朝向的四元数已经转过一次，再翻一次文字就镜像了
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
		// 下方补一条横线，描边位于外圈，与正文不重叠，同一深度亦不冲突
		var lineY = font.lineHeight;
		OutlineRenderer.renderFrame(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, 1F, LogicFont.outlineColor(color));
		OutlineRenderer.renderRect(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, color);
		pose.popPose();
	}
	/** @return 准星落在编辑按钮所在的那一面时它所在的处理器。比角标宽一档：只要指着那一面，铅笔就画出来。 */
	private static @Nullable BlockPos faceUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		var pos = hit.getBlockPos();
		return hit.getDirection() == level.getBlockState(pos).getValue(MicroProcessorBlock.FACING) ? pos : null;
	}
	/**
	 * 画一个角的角标：{@code (x, y)} 是外角在按钮局部坐标系里的位置（原点在按钮正中），
	 * 朝 {@code (dx, dy)} 那一侧伸出两条臂。
	 * <p>每条臂都拼成一个矩形，外角才是实心的——拿带中心线的线段画，线只覆盖到角点两侧的各半个线宽，
	 * 角的外侧会空掉半格，看着就成了「角上没东西、边上才有线」。
	 */
	private static void renderCorner(Pose flat, Vec3 cam, FaceFrame frame, float x, float y, float dx, float dy) {
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_LEN, y + dy * CORNER_W);
		renderPatch(flat, cam, frame, x, y, x + dx * CORNER_W, y + dy * CORNER_LEN);
	}
	/**
	 * 将铅笔图标置于按钮正中，贴合按钮所在面。
	 * <p>姿态由 {@link FaceFrame#rotation()} 给出：pose 的 XY 平面即该面，
	 * 文字的 y 轴向下亦相符，自面外侧看方向为正。
	 * <p>该姿态还需绕面内法向旋转 {@link #SPIN_DEGREES} 度，与角标保持一致。
	 */
	private static void renderIcon(PoseStack pose, Vec3 cam, FaceFrame frame) {
		var icon = LogicIcons.PENCIL.component();
		var width = LogicFont.width(icon);
		// 字形尺寸是字体定死的，按目标宽度反推缩放，换字形也不用重新调字号
		var scale = ICON_WIDTH / width;
		var center = frame.center();
		pose.pushPose();
		pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
		pose.mulPose(frame.rotation());
		// 绕局部 -z（面朝外的一侧）旋转：自面外侧观察为逆时针，与贴合顶面时一致
		pose.mulPose(Axis.ZN.rotationDegrees(SPIN_DEGREES));
		pose.scale(scale, scale, scale);
		mc.font.drawInBatch(
			icon,
			-width / 2F,
			// 基线取值与界面一致：高度为 0 的容器居中，等价于字形中心落在原点
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
	/** 把局部坐标系里的一个矩形转过 {@link #SPIN_DEGREES} 度后画到那一面上，两个角点不用管顺序。 */
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
	/** @return 按钮局部坐标（原点在按钮正中）绕中心转过 {@link #SPIN_DEGREES} 度后的世界坐标。 */
	private static Vec3 spin(FaceFrame frame, float x, float y) {
		return frame.point((x - y) * SPIN_COS, (x + y) * SPIN_COS);
	}
	/** ESC 会打开暂停菜单，这里把它拦下来改成退出链接模式。 */
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
	/** 准星停在按钮上就通知 {@link HoverTip} 续一次提示，计时由它自己退。 */
	public static void onClientTick(Post ignoredEvent) {
		if (buttonUnderCrosshair() != null) HoverTip.show(EDIT_TIP);
	}
	/**
	 * @return 准星正压在编辑按钮上时它所在的处理器；点不到就是 {@code null}。
	 * 	<p>按钮是贴在那一面正中的一小块，只有命中它才算「点得动」，角标和底部提示都跟着这个动作。
	 */
	private static @Nullable BlockPos buttonUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		return MicroProcessorBlock.isEditButton(level, hit.getBlockPos(), hit) ? hit.getBlockPos() : null;
	}
	/**
	 * @return 准星指着处理器时的那次命中，条件不满足（旁观、潜行、冒险、够不着、没权限）返回 {@code null}。
	 */
	private static @Nullable BlockHitResult hitOnProcessor() {
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) return null;
		if (player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) return null;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return null;
		var pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof MicroProcessorBlock)) return null;
		// 无权限的世界处理器亦不绘制编辑按钮，与点击时的判断一致
		if (!accessible(level, pos)) return null;
		return hit;
	}

}
