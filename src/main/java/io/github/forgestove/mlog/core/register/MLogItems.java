package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.*;
/** 物品注册。 */
public final class MLogItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MLog.ID);
	public static final DeferredItem<BlockItem> MICRO_PROCESSOR = ITEMS.registerSimpleBlockItem(
		MLogBlocks.MICRO_PROCESSOR,
		new Properties()
	);
}
