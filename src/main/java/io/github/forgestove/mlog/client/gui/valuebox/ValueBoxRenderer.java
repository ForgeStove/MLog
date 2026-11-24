package io.github.forgestove.mlog.client.gui.valuebox;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import org.joml.Matrix4f;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
public class ValueBoxRenderer {
	public static void renderSprite(PoseStack pose, int color) {
		var buffers = mc.renderBuffers().bufferSource();
		var consumer = buffers.getBuffer(RenderTypes.SPRITE);
		var matrix = pose.last().pose();
		var red = ARGB32.red(color) / 255F;
		var green = ARGB32.green(color) / 255F;
		var blue = ARGB32.blue(color) / 255F;
		var alpha = ARGB32.alpha(color) / 255F;
		vertex(consumer, matrix, -0.5F, -0.5F, red, green, blue, alpha, 0F, 0F);
		vertex(consumer, matrix, 0.5F, -0.5F, red, green, blue, alpha, 1F, 0F);
		vertex(consumer, matrix, 0.5F, 0.5F, red, green, blue, alpha, 1F, 1F);
		vertex(consumer, matrix, -0.5F, 0.5F, red, green, blue, alpha, 0F, 1F);
		buffers.endBatch(RenderTypes.SPRITE);
	}
	private static void vertex(
		VertexConsumer consumer,
		Matrix4f matrix,
		float x,
		float y,
		float red,
		float green,
		float blue,
		float alpha,
		float u,
		float v
	) {
		consumer.addVertex(matrix, x, y, 0F).setColor(red, green, blue, alpha).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
	}
	private static class RenderTypes extends RenderType {
		private static final ResourceLocation TEXTURE = getMLogRes("textures/gui/edit_button.png");
		/** 与方块面共面，不写深度以免遮挡后绘制的图标。 */
		private static final RenderType SPRITE = create(
			"mlog_value_box",
			DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			Mode.QUADS,
			256,
			false,
			false,
			CompositeState.builder()
				.setShaderState(RENDERTYPE_TEXT_SHADER)
				.setTextureState(new TextureStateShard(TEXTURE, false, false))
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setDepthTestState(LEQUAL_DEPTH_TEST)
				.setWriteMaskState(COLOR_WRITE)
				.setCullState(NO_CULL)
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		);
		private RenderTypes(
			String name,
			VertexFormat format,
			Mode mode,
			int bufferSize,
			boolean affectsCrumbling,
			boolean sortOnUpload,
			Runnable setupState,
			Runnable clearState
		) {
			super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
		}
	}
}
