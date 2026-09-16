package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
/** 菜单注册。 */
public final class MLogMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MLog.ID);
	public static final Supplier<MenuType<MicroProcessorMenu>> MICRO_PROCESSOR = MENUS.register(
		"micro_processor",
		() -> IMenuTypeExtension.create(MicroProcessorMenu::new)
	);
}
