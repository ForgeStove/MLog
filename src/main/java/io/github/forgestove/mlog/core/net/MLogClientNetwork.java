package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.render.UnitMarker;
import io.github.forgestove.mlog.content.display.TileLogicDisplayBlockEntity;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;

import java.util.List;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class MLogClientNetwork {
	public static void applyLinkUnits(LinkUnitsPayload payload) {
		LinkMode.applyUnits(payload.pos(), payload.ids());
	}
	public static void applyUnitOwner(UnitOwnerPayload payload) {
		UnitMarker.applyOwner(payload.entityId(), payload.owner().orElse(null));
	}
	public static void applyVars(LogicVarsPayload payload) {
		var level = mc.level;
		if (level == null) return;
		if (level.getBlockEntity(payload.pos()) instanceof AbstractProcessorBlockEntity processor) processor.applyVars(payload.vars());
	}
	public static void applyDisplay(DisplayPayload payload) {
		apply(payload.pos(), payload.commands());
	}
	public static void applyBatch(DisplayBatchPayload payload) {
		for (var entry : payload.entries()) apply(entry.pos(), entry.commands());
	}
	private static void apply(BlockPos pos, List<DrawCmd> commands) {
		var level = mc.level;
		if (level == null) return;
		if (level.getBlockEntity(pos) instanceof TileLogicDisplayBlockEntity display) display.draw(commands);
	}
}
