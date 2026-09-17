package io.github.forgestove.mlog.client.event;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.gui.LogicFont;
import io.github.forgestove.mlog.client.render.OutlineRenderer;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import io.github.forgestove.mlog.core.net.LinkPayload;
import io.github.forgestove.mlog.logic.LogicLink;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
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
	private static @Nullable BlockPos processor;
	public static void start(BlockPos pos) {
		processor = pos;
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
		var target = hit.getBlockPos();
		// 点的是处理器自己：当作「选完了」，退出链接模式，同时把这一下拦掉，免得顺手把方块挖了
		if (target.equals(processor)) {
			exit();
			event.setCanceled(true);
			return;
		}
		// 已经链接过的再点一次就断开，对齐 Mindustry 的 onConfigureBuildTapped
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
	 * 给已链接的方块描一圈边，并在它上方写出链接名，对齐 Mindustry 配置界面里的
	 * {@code Drawf.square(..., Pal.place)} 与 {@code drawPlaceText}。
	 * <p>框就是方块本身的体积，不往外扩——Mindustry 那边方框也正好贴着方块。
	 */
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		var origin = processor;
		if (origin == null) return;
		var linked = linksOf(origin);
		if (linked == null || linked.isEmpty()) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		for (var link : linked) {
			var pos = link.absolute(origin);
			OutlineRenderer.renderBox(pose, cam, new AABB(pos), LINE_W, PLACE);
			renderLinkName(pose, cam, buffers, pos, link.name());
		}
		buffers.endBatch();
	}
	/**
	 * 把链接名画在方块顶上，正面朝向相机——MC 的名字标签也是这么摆的。
	 * <p>字体走界面那套：{@link LogicFont} 的字形来自 ttf，比原版位图放大后耐看，也和界面里的字一致。
	 */
	private static void renderLinkName(PoseStack pose, Vec3 camera, MultiBufferSource buffers, BlockPos pos, String name) {
		var font = mc.font;
		var text = LogicFont.literal(name);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(pos.getX() + 0.5 - camera.x, pos.getY() + 1.3 - camera.y, pos.getZ() + 0.5 - camera.z);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		// 字号取名字标签那个比例。x 不能取负：相机朝向的四元数已经转过一次，再翻一次文字就镜像了
		pose.scale(0.025F, -0.025F, 0.025F);
		var matrix = pose.last().pose();
		var x = -width / 2F;
		// 描边照 LogicFont.drawOutlined 的做法：先铺一层描边字体，再把正文压上去
		font.drawInBatch(
			text.copy().withStyle(style -> style.withFont(LogicFont.OUTLINE_ID)),
			x,
			0F,
			LogicFont.OUTLINE,
			false,
			matrix,
			buffers,
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		font.drawInBatch(text, x, 0F, ACCENT, false, matrix, buffers, DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
		// 底下补一条横线，描边画在外圈，和正文不重叠，同一深度也不会打架
		var lineY = font.lineHeight;
		OutlineRenderer.renderFrame(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, 1F, LogicFont.OUTLINE);
		OutlineRenderer.renderRect(pose.last(), x, lineY, x + width, lineY + UNDERLINE_H, ACCENT);
		pose.popPose();
	}
	/** ESC 会打开暂停菜单，这里把它拦下来改成退出链接模式。 */
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
}
