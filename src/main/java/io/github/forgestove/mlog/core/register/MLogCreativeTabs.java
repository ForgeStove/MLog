package io.github.forgestove.mlog.core.register;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
/** 把本模组方块挂进原版创造模式标签页。 */
public final class MLogCreativeTabs {
	public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(MLogItems.MICRO_PROCESSOR.get());
	}
}
