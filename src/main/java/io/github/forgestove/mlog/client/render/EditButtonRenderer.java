package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.valuebox.IconValueBox;
import io.github.forgestove.mlog.compat.sable.SableSubLevelPose;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.content.processor.behaviour.*;
import io.github.forgestove.mlog.content.processor.behaviour.ValueBoxTransform.Sided;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class EditButtonRenderer {
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
		var hit = hitOnProcessor();
		if (hit == null) return;
		var behaviour = behaviour(hit);
		if (behaviour == null) return;
		var level = mc.level;
		if (level == null) return;
		var pos = hit.getBlockPos();
		var transform = behaviour.getSlotPositioning();
		if (transform instanceof Sided sided) sided.fromSide(hit.getDirection());
		var state = level.getBlockState(pos);
		if (!transform.shouldRender(level, pos, state)) return;
		var active = behaviour.testHit(level, pos, state, hit);
		var local = SableSubLevelPose.push(pose, Vec3.atLowerCornerOf(pos), cam);
		var camera = local != null ? local : cam;
		new IconValueBox(level, pos, behaviour.getLabel(), LogicIcons.PENCIL, transform).spin(45F)
			.wideOutline()
			.passive(!active)
			.render(pose, camera);
		if (local != null) pose.popPose();
	}
	public static void onClientTick(Post ignoredEvent) {
		var hit = hitOnProcessor();
		if (hit == null) return;
		var behaviour = behaviour(hit);
		if (behaviour == null || mc.level == null) return;
		if (!behaviour.testHit(mc.level, hit.getBlockPos(), mc.level.getBlockState(hit.getBlockPos()), hit)) return;
		HoverTip.show(List.of(HoverTip.withTipFont(behaviour.getLabel())));
	}
	private static @Nullable EditButtonBehaviour behaviour(@Nullable BlockHitResult hit) {
		var player = mc.player;
		if (hit == null || mc.level == null || player == null) return null;
		var behaviour = ProcessorBehaviour.get(mc.level, hit.getBlockPos(), EditButtonBehaviour.TYPE);
		if (behaviour == null || !behaviour.isActive() || !behaviour.mayInteract(player)) return null;
		return behaviour;
	}
	private static @Nullable BlockHitResult hitOnProcessor() {
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) return null;
		if (player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) return null;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return null;
		var pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof AbstractProcessorBlock)) return null;
		if (!ProcessorRightClick.accessible(level, pos, player)) return null;
		return hit;
	}
}
