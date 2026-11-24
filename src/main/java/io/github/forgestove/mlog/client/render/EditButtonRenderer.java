package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.gui.HoverTip;
import io.github.forgestove.mlog.client.gui.valuebox.EditButtonBox;
import io.github.forgestove.mlog.compat.sable.SableSubLevelPose;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.content.processor.EditButtonTransform.Faced;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class EditButtonRenderer {
	private static final List<Component> TIP = List.of(HoverTip.text("mlog.gui.edit"));
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		render(pose, cam);
		pose.popPose();
		mc.renderBuffers().bufferSource().endBatch();
	}
	private static void render(PoseStack pose, Vec3 cam) {
		var level = mc.level;
		if (level == null) return;
		var face = faceUnderCrosshair();
		var button = buttonUnderCrosshair();
		var at = face != null ? face : button;
		if (at == null) return;
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(at), cam);
		var camera = local != null ? local : cam;
		new EditButtonBox(level, at).passive(button == null).render(pose, camera);
		if (local != null) pose.popPose();
	}
	public static void onClientTick(Post ignoredEvent) {
		if (buttonUnderCrosshair() != null) HoverTip.show(TIP);
	}
	private static @Nullable BlockPos faceUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		var pos = hit.getBlockPos();
		return Faced.INSTANCE.isActiveFace(level.getBlockState(pos), hit.getDirection()) ? pos : null;
	}
	private static @Nullable BlockPos buttonUnderCrosshair() {
		var hit = hitOnProcessor();
		var level = mc.level;
		if (hit == null || level == null) return null;
		return AbstractProcessorBlock.isEditButton(level, hit.getBlockPos(), hit) ? hit.getBlockPos() : null;
	}
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
	private static boolean accessible(Level level, BlockPos pos) {
		return ProcessorRightClick.accessible(level, pos, mc.player);
	}
}
