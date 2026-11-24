package io.github.forgestove.mlog.client.gui.valuebox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.forgestove.mlog.client.gui.LogicFont;
import io.github.forgestove.mlog.client.gui.LogicIcons;
import io.github.forgestove.mlog.content.processor.behaviour.ValueBoxTransform;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 以字形图标作为内容的面板。
 * 与 Create 的 {@code IconValueBox} 同构；{@link #spin} 为本项目对编辑按钮菱形的扩展。
 */
public class IconValueBox extends ValueBox {
	/** 图标宽度，单位为格。 */
	private static final float ICON_WIDTH = 2 / 16F;
	private final LogicIcons icon;
	private float spinDegrees;
	public IconValueBox(Level level, BlockPos pos, Component label, LogicIcons icon, ValueBoxTransform transform) {
		super(level, pos, label, transform);
		this.icon = icon;
	}
	/** 内容与外框一起绕面内法向旋转，用于菱形按钮。 */
	public IconValueBox spin(float degrees) {
		spinDegrees = degrees;
		return this;
	}
	@Override
	protected void renderOutline(PoseStack pose) {
		pose.pushPose();
		spin(pose);
		super.renderOutline(pose);
		pose.popPose();
	}
	@Override
	protected void renderContents(PoseStack pose) {
		var glyph = icon.component();
		var width = LogicFont.width(glyph);
		var scale = ICON_WIDTH / width;
		pose.pushPose();
		spin(pose);
		pose.scale(scale, scale, scale);
		mc.font.drawInBatch(
			glyph,
			-width / 2F,
			// 高度为 0 的容器居中，即字形中心位于原点
			LogicIcons.centerY(0, 0),
			overrideColor,
			false,
			pose.last().pose(),
			mc.renderBuffers().bufferSource(),
			DisplayMode.SEE_THROUGH,
			0,
			LightTexture.FULL_BRIGHT
		);
		pose.popPose();
	}
	private void spin(PoseStack pose) {
		if (spinDegrees != 0) pose.mulPose(Axis.ZN.rotationDegrees(spinDegrees));
	}
}
