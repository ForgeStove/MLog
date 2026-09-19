package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class MLogCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MLog.ID);
	static {
		TABS.register(
			"main",
			() -> CreativeModeTab.builder()
				.title(Component.translatable("itemGroup." + MLog.ID))
				.icon(() -> new ItemStack(MLogItems.MICRO_PROCESSOR.get()))
				.displayItems((parameters, output) -> {
					output.accept(MLogItems.MICRO_PROCESSOR.get());
					output.accept(MLogItems.MEMORY_CELL.get());
					output.accept(MLogItems.MEMORY_BANK.get());
					// 世界处理器和世界内存元都跟命令方块一样：没有操作命令方块的权限就不列出来，免得拿了也放不下
					if (parameters.hasPermissions()) {
						output.accept(MLogItems.WORLD_PROCESSOR.get());
						output.accept(MLogItems.WORLD_CELL.get());
					}
				})
				.build()
		);
	}
}
