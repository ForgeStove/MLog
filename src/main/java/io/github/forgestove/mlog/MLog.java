package io.github.forgestove.mlog;
import io.github.forgestove.mlog.compat.create.CreateDisplays;
import io.github.forgestove.mlog.core.MLogMods;
import io.github.forgestove.mlog.core.net.MLogNetwork;
import io.github.forgestove.mlog.core.command.MLogCommand;
import io.github.forgestove.mlog.core.register.*;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
		MLogMods.create.executeIfInstalled(()-> CreateDisplays.register(modBus));
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, MLogCommand::register);
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> RedstoneSources.clear());
	}
}
