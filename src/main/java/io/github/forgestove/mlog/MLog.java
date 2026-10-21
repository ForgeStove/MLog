package io.github.forgestove.mlog;
import io.github.forgestove.mlog.compat.create.*;
import io.github.forgestove.mlog.content.display.DisplaySender;
import io.github.forgestove.mlog.core.MLogMods;
import io.github.forgestove.mlog.core.command.MLogCommand;
import io.github.forgestove.mlog.core.net.MLogNetwork;
import io.github.forgestove.mlog.core.register.*;
import io.github.forgestove.mlog.logic.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
@Mod(MLog.ID)
public class MLog {
	public static final String ID = "mlog";
	public MLog(IEventBus modBus) {
		MLogBlocks.BLOCKS.register(modBus);
		MLogItems.ITEMS.register(modBus);
		MLogBlockEntities.BLOCK_ENTITIES.register(modBus);
		MLogMenus.MENUS.register(modBus);
		MLogSounds.SOUNDS.register(modBus);
		MLogCreativeTabs.TABS.register(modBus);
		modBus.addListener(MLogNetwork::register);
		MLogMods.create.executeIfInstalled(() -> {
			CreateDisplays.register(modBus);
			CreateSchematics.register(modBus);
		});
		IEventBus gameBus = NeoForge.EVENT_BUS;
		gameBus.addListener(MLogCommand::register);
		gameBus.addListener(DisplaySender::flush);
		gameBus.addListener(RedstoneSources::clear);
		gameBus.addListener(DisplaySender::clear);
		gameBus.addListener((Post event) -> MLogUnits.closeContainers(event.getLevel()));
	}
}
