package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.gui.LogicFont;
import io.github.forgestove.mlog.content.processor.*;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 准星指向世界处理器时，在其上方显示暗色名称。 */
public class ProcessorTag {
	/** 暗色命名：白色 50% 透明度。 */
	private static final int COLOR = 0x80FFFFFF;
	public static void onRenderLevel(RenderLevelStageEvent event) {
		// 在天气阶段之后绘制，否则云、雨会遮挡
		if (event.getStage() != Stage.AFTER_WEATHER) return;
		var level = mc.level;
		var player = mc.player;
		if (level == null || player == null) return;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) return;
		var pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof WorldProcessorBlock)) return;
		if (!player.canUseGameMasterBlocks()) return;
		if (!(level.getBlockEntity(pos) instanceof AbstractProcessorBlockEntity processor)) return;
		var tag = processor.getTag();
		if (tag.isEmpty()) return;
		var pose = event.getPoseStack();
		var cam = event.getCamera().getPosition();
		var buffers = mc.renderBuffers().bufferSource();
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		draw(pose, cam, buffers, Vec3.atLowerCornerOf(pos).add(0.5, 1.25, 0.5), tag);
		pose.popPose();
		buffers.endBatch();
	}
	private static void draw(PoseStack pose, Vec3 cam, MultiBufferSource buffers, Vec3 at, String tag) {
		var text = LogicFont.literal(tag);
		var width = LogicFont.width(text);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		// 顶点还要乘全局视图矩阵，取其旋转的逆使字形落回屏幕平面
		var view = new Quaternionf().setFromNormalized(new Matrix3f(RenderSystem.getModelViewMatrix()));
		pose.mulPose(view.conjugate());
		pose.scale(0.025F, -0.025F, 0.025F);
		mc.font.drawInBatch(
			text,
			-width / 2F,
			0F,
			COLOR,
			false,
			pose.last().pose(),
			buffers,
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		pose.popPose();
	}
}
