package io.github.forgestove.mlog;
import io.github.forgestove.mlog.client.MLogClientSetup;
import io.github.forgestove.mlog.client.event.*;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.render.*;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
@Mod(value = MLog.ID, dist = Dist.CLIENT)
public class MLogClient {
	public MLogClient(IEventBus modBus) {
		modBus.addListener(MLogClientSetup::registerScreens);
		modBus.addListener(MLogClientSetup::registerRenderers);
		modBus.addListener(ModifyBakingResult.class, MLogClientSetup::modifyBakingResult);
		modBus.addListener(HoverTip::register);
		modBus.addListener(
			RegisterClientReloadListenersEvent.class,
			event -> event.registerReloadListener((ResourceManagerReloadListener) ModelOutline::reload)
		);
		modBus.addListener(ResourcePackHandler::register);
		var gameBus = NeoForge.EVENT_BUS;
		gameBus.addListener(LinkMode::onMouseButton);
		gameBus.addListener(LinkMode::onClientTick);
		gameBus.addListener(HoverTip::tick);
		gameBus.addListener(DisplayRenderer::onClientTick);
		gameBus.addListener(DisplayRenderer::onRenderLevel);
		gameBus.addListener(LinkMode::onScreenOpening);
		gameBus.addListener(EventPriority.LOWEST, LinkMode::onRightClickBlock);
		gameBus.addListener(LinkMode::onRenderLevel);
		gameBus.addListener(ModelOutline::onRenderHighlight);
		gameBus.addListener(LogicCursor::reset);
		gameBus.addListener(LogicCursor::apply);
	}
}
