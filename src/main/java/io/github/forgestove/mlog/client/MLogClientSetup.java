package io.github.forgestove.mlog.client;
import io.github.forgestove.mlog.client.gui.MicroProcessorScreen;
import io.github.forgestove.mlog.core.register.*;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
/** 客户端专属的注册：界面。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientSetup {
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(MLogMenus.MICRO_PROCESSOR.get(), MicroProcessorScreen::new);
	}
}
