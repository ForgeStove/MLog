package io.github.forgestove.mlog.client;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.client.gui.MicroProcessorScreen;
import io.github.forgestove.mlog.client.render.*;
import io.github.forgestove.mlog.core.register.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
/** 客户端专属的注册：界面、方块实体渲染。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientSetup {
	/** 烘焙结果表的键为「方块名 + 属性串」，对应方块而非模型。 */
	private static final ResourceLocation TILE_LOGIC_DISPLAY = ResourceLocation.fromNamespaceAndPath(MLog.ID, "tile_logic_display");
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(MLogMenus.MICRO_PROCESSOR.get(), MicroProcessorScreen::new);
	}
	public static void registerRenderers(RegisterRenderers event) {
		event.registerBlockEntityRenderer(MLogBlockEntities.TILE_LOGIC_DISPLAY.get(), context -> new DisplayRenderer());
	}
	/** 屏幕面须按邻居切换方格图上的一格，故将模型再包一层：连接位与 UV 修改均在该层完成。 */
	public static void modifyBakingResult(ModifyBakingResult event) {
		event.getModels().replaceAll((location, model) -> {
			if (!TILE_LOGIC_DISPLAY.equals(location.id())) return model;
			// 同一模型会被多个变体共用，不得重复包装
			return model instanceof DisplayScreenModel ? model : new DisplayScreenModel(model);
		});
	}
}
