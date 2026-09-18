package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.registries.DeferredRegister.Items;
/** 物品注册。 */
public final class MLogItems {
	public static final Items ITEMS = DeferredRegister.createItems(MLog.ID);
	public static final DeferredItem<BlockItem> MICRO_PROCESSOR = ITEMS.registerSimpleBlockItem(
		MLogBlocks.MICRO_PROCESSOR,
		new Properties()
	);
}
