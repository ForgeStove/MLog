package io.github.forgestove.mlog.client.gui.valuebox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.content.processor.EditButtonTransform.Faced;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class EditButtonBox extends ValueBox {
	private static final float ICON_WIDTH = 2 / 16F;
	private static final float SPIN_DEGREES = 45F;
	public EditButtonBox(Level level, BlockPos pos) {
		super(level, pos, Faced.INSTANCE);
	}
	@Override
	protected void renderOutline(PoseStack pose) {
		pose.pushPose();
		pose.mulPose(Axis.ZN.rotationDegrees(SPIN_DEGREES));
		ValueBoxRenderer.renderSprite(pose, color);
		pose.popPose();
	}
	@Override
	protected void renderContents(PoseStack pose) {
		var icon = LogicIcons.PENCIL.component();
		var width = LogicFont.width(icon);
		var scale = ICON_WIDTH / width;
		pose.pushPose();
		pose.mulPose(Axis.ZN.rotationDegrees(SPIN_DEGREES));
		pose.scale(scale, scale, scale);
		mc.font.drawInBatch(
			icon,
			-width / 2F,
			LogicIcons.centerY(0, 0),
			color,
			false,
			pose.last().pose(),
			mc.renderBuffers().bufferSource(),
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		pose.popPose();
	}
}
