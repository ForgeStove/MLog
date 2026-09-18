package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 客户端的包处理，只在客户端加载。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientNetwork {
	public static void applyVars(LogicVarsPayload payload) {
		var level = mc.level;
		if (level == null) return;
		if (level.getBlockEntity(payload.pos()) instanceof MicroProcessorBlockEntity processor) processor.applyVars(payload.vars());
	}
}
