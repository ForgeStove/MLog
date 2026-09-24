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
				.title(Component.translatable("itemGroup.mlog"))
				.icon(() -> new ItemStack(MLogItems.MICRO_PROCESSOR.get()))
				.displayItems((parameters, output) -> {
					output.accept(MLogItems.MICRO_PROCESSOR.get());
					output.accept(MLogItems.MEMORY_CELL.get());
					output.accept(MLogItems.MEMORY_BANK.get());
					if (!parameters.hasPermissions()) return;
					output.accept(MLogItems.WORLD_PROCESSOR.get());
					output.accept(MLogItems.WORLD_CELL.get());
				})
				.build()
		);
	}
}
