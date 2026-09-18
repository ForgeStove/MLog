package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.RenderHighlightEvent.Block;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 方块被准星指着时那圈轮廓，照着<b>模型的面</b>描，而不是套碰撞箱画方框。
 * <p>做法取自铁砧工艺的 {@code ModelBlockSelection}：也是从烘焙好的模型拿几何来画选择框。
 * 碰撞箱归服务端算，模型却只有客户端才有，所以这种精细轮廓只能待在渲染这一侧，
 * 也正因为如此，它不影响碰撞——两件事各走各的。
 */
@OnlyIn(Dist.CLIENT)
public final class ModelOutline {
	/** 描边颜色，取值同原版的选中框：四成黑。 */
	private static final float RED = 0F, GREEN = 0F, BLUE = 0F, ALPHA = 0.4F;
	/** 取模型面时的随机源固定住，免得同一块方块每帧抽到不同的面而闪。 */
	private static final RandomSource RANDOM = RandomSource.create(42L);
	public static void onRenderHighlight(Block event) {
		var level = mc.level;
		if (level == null) return;
		var pos = event.getTarget().getBlockPos();
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MicroProcessorBlock)) return;
		var quads = quads(state);
		// 模型还没烘出来就什么都别画，但也别把原版那圈给拦掉了
		if (quads.isEmpty()) return;
		event.setCanceled(true);
		var pose = event.getPoseStack().last();
		var camera = event.getCamera().getPosition();
		var consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
		for (var quad : quads) edge(consumer, pose, camera, pos, quad.getVertices());
	}
	/** @return 这个状态的全部面，含各朝向与不朝向任何方向的那些。 */
	private static List<BakedQuad> quads(BlockState state) {
		var model = mc.getBlockRenderer().getBlockModel(state);
		// 渲染类型传 null 才会把面全都给出来，传具体类型只拿得到那一条管线用的
		var result = new ArrayList<>(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));
		for (var side : Direction.values()) result.addAll(model.getQuads(state, side, RANDOM, ModelData.EMPTY, null));
		return result;
	}
	/** 一个面绕一圈画四条边。 */
	private static void edge(VertexConsumer consumer, Pose pose, Vec3 camera, BlockPos pos, int[] vertices) {
		var stride = vertices.length / 4;
		for (var i = 0; i < 4; i++) {
			vertex(consumer, pose, camera, pos, vertices, i * stride);
			vertex(consumer, pose, camera, pos, vertices, (i + 1) % 4 * stride);
		}
	}
	/** 顶点格式是 POSITION_COLOR_TEX_LIGHTMAP_NORMAL，位置就压在最前面那三个 int 里。 */
	private static void vertex(VertexConsumer consumer, Pose pose, Vec3 camera, BlockPos pos, int[] vertices, int offset) {
		consumer.addVertex(
			pose,
			(float) (Float.intBitsToFloat(vertices[offset]) + pos.getX() - camera.x),
			(float) (Float.intBitsToFloat(vertices[offset + 1]) + pos.getY() - camera.y),
			(float) (Float.intBitsToFloat(vertices[offset + 2]) + pos.getZ() - camera.z)
		).setColor(RED, GREEN, BLUE, ALPHA).setNormal(pose, 0F, 1F, 0F);
	}
}
