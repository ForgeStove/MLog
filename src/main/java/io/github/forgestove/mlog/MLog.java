package io.github.forgestove.mlog;
import io.github.forgestove.mlog.core.net.*;
import io.github.forgestove.mlog.core.register.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
@Mod(MLog.ID)
public class MLog {
	public static final String ID = "mlog";
	public MLog(IEventBus modBus) {
		MLogBlocks.BLOCKS.register(modBus);
		MLogItems.ITEMS.register(modBus);
		MLogBlockEntities.BLOCK_ENTITIES.register(modBus);
		MLogMenus.MENUS.register(modBus);
		MLogSounds.SOUNDS.register(modBus);
		modBus.addListener(MLogCreativeTabs::onBuildTabs);
		modBus.addListener(MLogNetwork::register);
	}
}
