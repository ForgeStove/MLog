package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.GameMasterBlockItem;
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
	/** 用 {@code GameMasterBlockItem} 而不是普通的方块物品：非 OP 放不下，和命令方块一样。 */
	public static final DeferredItem<GameMasterBlockItem> WORLD_PROCESSOR = ITEMS.register(
		"world_processor",
		() -> new GameMasterBlockItem(MLogBlocks.WORLD_PROCESSOR.get(), new Properties())
	);
	public static final DeferredItem<BlockItem> MEMORY_CELL = ITEMS.registerSimpleBlockItem(MLogBlocks.MEMORY_CELL, new Properties());
	public static final DeferredItem<BlockItem> MEMORY_BANK = ITEMS.registerSimpleBlockItem(MLogBlocks.MEMORY_BANK, new Properties());
}
