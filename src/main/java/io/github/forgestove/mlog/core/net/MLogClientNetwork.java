package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.content.display.TileLogicDisplayBlockEntity;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.*;

import java.util.List;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 客户端的包处理，只在客户端加载。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientNetwork {
	public static void applyVars(LogicVarsPayload payload) {
		var level = mc.level;
		if (level == null) return;
		if (level.getBlockEntity(payload.pos()) instanceof AbstractProcessorBlockEntity processor) processor.applyVars(payload.vars());
	}
	public static void applyDisplay(DisplayPayload payload) {
		apply(payload.pos(), payload.commands());
	}
	/** 合并包：逐格套用，与单格包同一条路径。 */
	public static void applyBatch(DisplayBatchPayload payload) {
		for (var entry : payload.entries()) apply(entry.pos(), entry.commands());
	}
	private static void apply(BlockPos pos, List<DrawCmd> commands) {
		var level = mc.level;
		if (level == null) return;
		if (level.getBlockEntity(pos) instanceof TileLogicDisplayBlockEntity display) display.draw(commands);
	}
}
