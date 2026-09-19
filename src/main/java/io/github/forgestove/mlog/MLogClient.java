package io.github.forgestove.mlog;
import io.github.forgestove.mlog.client.MLogClientSetup;
import io.github.forgestove.mlog.client.event.*;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.render.ModelOutline;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
@Mod(value = MLog.ID, dist = Dist.CLIENT)
public class MLogClient {
	public MLogClient(IEventBus modBus) {
		modBus.addListener(MLogClientSetup::registerScreens);
		modBus.addListener(HoverTip::register);
		var gameBus = NeoForge.EVENT_BUS;
		gameBus.addListener(LinkMode::onMouseButton);
		gameBus.addListener(LinkMode::onClientTick);
		gameBus.addListener(HoverTip::tick);
		gameBus.addListener(LinkMode::onScreenOpening);
		gameBus.addListener(LinkMode::onRightClickBlock);
		gameBus.addListener(LinkMode::onRenderLevel);
		gameBus.addListener(ModelOutline::onRenderHighlight);
		gameBus.addListener(LogicCursor::reset);
		gameBus.addListener(LogicCursor::apply);
	}
}
