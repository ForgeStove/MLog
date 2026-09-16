package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 把 {@code print} 的输出画在处理器方块上方。 */
@OnlyIn(Dist.CLIENT)
public class MicroProcessorRenderer implements BlockEntityRenderer<MicroProcessorBlockEntity> {
	/** 文字缩放，约等于原版名字牌大小。 */
	private static final float SCALE = 0.025F;
	/** 全亮光照，让文字在暗处也能看清。 */
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float LINE_GAP = 1F;
	private final Font font;
	public MicroProcessorRenderer(Context context) {
		font = context.getFont();
	}
	@Override
	public void render(
		MicroProcessorBlockEntity processor,
		float partialTick,
		PoseStack pose,
		MultiBufferSource buffer,
		int packedLight,
		int packedOverlay
	) {
		var text = processor.getDisplayText();
		if (text.isEmpty()) return;
		pose.pushPose();
		pose.translate(0.5F, 1.3F, 0.5F);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		// 文字坐标是 Y 轴向下、这里是 Y 轴向上，靠负的 Y 缩放翻正（原版名字牌同款写法）
		pose.scale(SCALE, -SCALE, SCALE);
		var matrix = pose.last().pose();
		var y = 0F;
		for (var line : text.split("\n")) {
			font.drawInBatch(
				line,
				-font.width(line) / 2F,
				y,
				0xFFFFFFFF,
				false,
				matrix,
				buffer,
				DisplayMode.SEE_THROUGH,
				0,
				FULL_BRIGHT
			);
			y += font.lineHeight + LINE_GAP;
		}
		pose.popPose();
	}
	@Override
	public int getViewDistance() {
		return 128;
	}
}
