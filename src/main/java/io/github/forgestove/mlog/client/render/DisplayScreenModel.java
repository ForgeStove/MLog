package io.github.forgestove.mlog.client.render;
import io.github.forgestove.mlog.content.display.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 屏幕那一面按邻居换到变体贴图上对应的那一张。
 * <p>连接掩码由 {@link #getModelData} 在区块编译时算出并随 {@code ModelData} 传入 {@link #getQuads}，
 * 不写入方块状态：邻居变化时区块自行重编。
 * <p>选格须用八向掩码查表：同一缺口组合有多张瓷砖，仅凭四条边无法区分，内凹结构的拐角会因此断开。
 */
@OnlyIn(Dist.CLIENT)
public class DisplayScreenModel extends BakedModelWrapper<BakedModel> {
	/** 方格图一行几格、共几行；第 t 格为第 t 张瓷砖，47 张占 8×6。 */
	private static final int SHEET_U = 8, SHEET_V = 6;
	/** 顶点数据的跨度，以及 U、V 在其中的下标。 */
	private static final int STRIDE = 8, U_INDEX = 4, V_INDEX = 5;
	/** 屏幕的连接掩码，由 {@link #getModelData} 算好带进来。 */
	private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();
	public DisplayScreenModel(BakedModel original) {
		super(original);
	}
	/**
	 * 按世界算出连接掩码。
	 * <p>界面预览把方块置于只识别该格自身状态的虚拟世界中，由此读不到邻居；改从方块实体自身的世界
	 * 按真实坐标计算，否则预览中始终显示为独立一格。
	 */
	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
		if (!state.hasProperty(TileLogicDisplayBlock.ORIENTATION)) return data;
		var orientation = state.getValue(TileLogicDisplayBlock.ORIENTATION);
		if (level.getBlockEntity(pos) instanceof TileLogicDisplayBlockEntity display && display.getLevel() != null)
			return ModelData.of(CONNECTIONS, DisplayGroup.connections(display.getLevel(), display.getBlockPos(), orientation));
		return ModelData.of(CONNECTIONS, DisplayGroup.connections(level, pos, orientation));
	}
	@Override
	public List<BakedQuad> getQuads(
		@Nullable BlockState state,
		@Nullable Direction side,
		RandomSource random,
		ModelData modelData,
		@Nullable RenderType renderType
	) {
		var quads = super.getQuads(state, side, random, modelData, renderType);
		if (state == null || !state.hasProperty(TileLogicDisplayBlock.ORIENTATION)) return quads;
		// 屏幕面即法向的那一面，三个模型中仅它使用方格图
		var orientation = state.getValue(TileLogicDisplayBlock.ORIENTATION);
		if (side != orientation.front()) return quads;
		var connections = modelData.get(CONNECTIONS);
		var tile = DisplayGroup.tile(connections == null ? 0 : connections);
		int rotation = DisplayGroup.rotation(orientation);
		List<BakedQuad> result = null;
		for (var i = 0; i < quads.size(); i++) {
			if (result == null) result = new ArrayList<>(quads);
			result.set(i, tile(quads.get(i), tile, rotation));
		}
		return result == null ? quads : result;
	}
	/** @return 把顶点从整张方格图折进第 {@code tile} 张瓷砖那一格的副本，按 {@code rotation} 在格内转 90° 的整数倍。 */
	private static BakedQuad tile(BakedQuad quad, int tile, int rotation) {
		var sprite = quad.getSprite();
		var uWidth = sprite.getU1() - sprite.getU0();
		var vHeight = sprite.getV1() - sprite.getV0();
		var cellU = uWidth / SHEET_U;
		var cellV = vHeight / SHEET_V;
		var column = tile % SHEET_U;
		var row = tile / SHEET_U;
		var baseU = sprite.getU0() + column * cellU;
		var baseV = sprite.getV0() + row * cellV;
		var vertices = quad.getVertices().clone();
		for (var vertex = 0; vertex < 4; vertex++) {
			var at = vertex * STRIDE;
			// 顶点本来铺满整张图，先折成格内比例；摆向不为 0 时，采样点再在格内转过去
			var u = (Float.intBitsToFloat(vertices[at + U_INDEX]) - sprite.getU0()) / uWidth;
			var v = (Float.intBitsToFloat(vertices[at + V_INDEX]) - sprite.getV0()) / vHeight;
			for (var i = 0; i < rotation; i++) {
				var t = u;
				u = 1F - v;
				v = t;
			}
			vertices[at + U_INDEX] = Float.floatToRawIntBits(baseU + u * cellU);
			vertices[at + V_INDEX] = Float.floatToRawIntBits(baseV + v * cellV);
		}
		return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade());
	}
}
