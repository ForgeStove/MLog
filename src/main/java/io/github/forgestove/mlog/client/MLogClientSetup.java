package io.github.forgestove.mlog.client;
import io.github.forgestove.mlog.client.gui.MicroProcessorScreen;
import io.github.forgestove.mlog.client.render.MicroProcessorRenderer;
import io.github.forgestove.mlog.core.register.*;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
/** 客户端专属的注册：界面与方块实体渲染器。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientSetup {
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(MLogMenus.MICRO_PROCESSOR.get(), MicroProcessorScreen::new);
	}
	public static void registerRenderers(RegisterRenderers event) {
		event.registerBlockEntityRenderer(MLogBlockEntities.MICRO_PROCESSOR.get(), MicroProcessorRenderer::new);
	}
}
