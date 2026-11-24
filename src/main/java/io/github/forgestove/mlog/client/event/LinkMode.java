package io.github.forgestove.mlog.client.event;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.render.OutlineRenderer;
import io.github.forgestove.mlog.compat.sable.SableSubLevelPose;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.core.net.*;
import io.github.forgestove.mlog.logic.LogicLink;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class LinkMode {
	private static @Nullable BlockPos processor;
	private static int[] units = new int[0];
	private static long nextUnitRequest;
	public static void onRightClickBlock(RightClickBlock event) {
		if (event.getSide() != LogicalSide.CLIENT) return;
		if (!mc.options.keyUse.isDown()) return;
		if (!ProcessorRightClick.canOverride(event)) return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		start(event.getPos());
	}
	private static boolean accessible(Level level, BlockPos pos) {
		return ProcessorRightClick.accessible(level, pos, mc.player);
	}
	public static boolean active() {
		return processor != null;
	}
	public static void start(BlockPos pos) {
		if (mc.level == null || !accessible(mc.level, pos)) return;
		processor = pos;
		units = new int[0];
		requestUnits();
		LogicSounds.click();
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("mlog.gui.link.hint"), true);
	}
	public static void onMouseButton(Pre event) {
		if (event.getAction() != GLFW.GLFW_PRESS || processor == null || mc.screen != null) return;
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			exit();
			event.setCanceled(true);
			return;
		}
		if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return;
		var target = hit.getBlockPos();
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
		units = new int[0];
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable("mlog.gui.link.done"), true);
	}
	private static boolean isLinked(BlockPos target) {
		var origin = processor;
		if (origin == null) return false;
		var linked = linksOf(origin);
		return linked != null && linked.stream().anyMatch(link -> link.absolute(origin).equals(target));
	}
	private static @Nullable List<LogicLink> linksOf(BlockPos origin) {
		if (mc.level == null || !(mc.level.getBlockEntity(origin) instanceof AbstractProcessorBlockEntity be)) return null;
		be.refreshLinks();
		return be.getLinks();
	}
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		var origin = processor;
		if (origin != null) {
			var range = linkRange(origin);
			if (range != null && range > 0) renderRangeBox(pose, cam, origin, range);
			var boxes = new ArrayList<BoxSpec>();
			boxes.add(new BoxSpec(Vec3.atLowerCornerOf(origin), shapeBox(origin), ACCENT));
			var linked = linksOf(origin);
			if (linked != null) for (var link : linked) {
				var pos = link.absolute(origin);
				boxes.add(new BoxSpec(Vec3.atLowerCornerOf(pos), shapeBox(pos), link.valid() ? PLACE : TEXT_DIM));
			}
			var partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
			if (mc.level != null) for (var id : units) {
				var entity = mc.level.getEntity(id);
				if (entity != null) boxes.add(new BoxSpec(
					null,
					entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position())),
					ACCENT
				));
			}
			for (var box : boxes) renderBoxLayer(pose, cam, box, true);
			for (var box : boxes) renderBoxLayer(pose, cam, box, false);
			if (linked != null) for (var link : linked) {
				var pos = link.absolute(origin);
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
	private static void renderRangeBox(PoseStack pose, Vec3 cam, BlockPos origin, int range) {
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(origin), cam);
		var camera = local != null ? local : cam;
		OutlineRenderer.renderOutlinedBox(pose, camera, new AABB(origin).inflate(range), GRAY, ACCENT);
		if (local != null) pose.popPose();
	}
	private static void renderBoxLayer(PoseStack pose, Vec3 cam, BoxSpec spec, boolean outline) {
		var local = spec.at() != null ? SableSubLevelPose.push(pose, spec.at(), cam) : null;
		var camera = local != null ? local : cam;
		OutlineRenderer.renderBoxLayer(pose, camera, spec.box(), outline, outline ? LogicFont.outlineColor(spec.color()) : spec.color());
		if (local != null) pose.popPose();
	}
	private static @Nullable Integer linkRange(BlockPos pos) {
		var level = mc.level;
		return level != null && level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock block ? block.linkRange() : null;
	}
	private static AABB shapeBox(BlockPos pos) {
		var level = mc.level;
		if (level == null) return new AABB(pos);
		var shape = level.getBlockState(pos).getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}
	private static void renderLinkName(PoseStack pose, Vec3 cam, MultiBufferSource buffers, Vec3 at, String name, int color) {
		var font = mc.font;
		var text = LogicFont.literal(name);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		var view = new Quaternionf().setFromNormalized(new Matrix3f(RenderSystem.getModelViewMatrix()));
		pose.mulPose(view.conjugate());
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
		var lineY = font.lineHeight;
		var underlineH = 1F;
		OutlineRenderer.renderFrame(pose.last(), x, lineY, x + width, lineY + underlineH, 1F, LogicFont.outlineColor(color));
		OutlineRenderer.renderRect(pose.last(), x, lineY, x + width, lineY + underlineH, color);
		pose.popPose();
	}
	public static void onScreenOpening(Opening event) {
		if (processor == null || !(event.getNewScreen() instanceof PauseScreen)) return;
		exit();
		event.setCanceled(true);
	}
	public static void onClientTick(Post ignoredEvent) {
		if (processor == null || mc.level == null) return;
		if (!(mc.level.getBlockEntity(processor) instanceof AbstractProcessorBlockEntity)) {
			units = new int[0];
			return;
		}
		if (mc.level.getGameTime() < nextUnitRequest) return;
		requestUnits();
	}
	private static void requestUnits() {
		if (processor == null) return;
		if (mc.level != null) nextUnitRequest = mc.level.getGameTime() + 20;
		PacketDistributor.sendToServer(new LinkUnitsRequestPayload(processor));
	}
	public static void applyUnits(BlockPos pos, int[] ids) {
		if (pos.equals(processor)) units = ids;
	}
	private record BoxSpec(@Nullable Vec3 at, AABB box, int color) {}
}
