package io.github.forgestove.mlog.core;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.LoadingModList;

import java.util.*;
import java.util.function.Supplier;
@SuppressWarnings("unused")
public enum MLogMods {
	create,
	sableCompanion,
	;
	private final String id;
	MLogMods() {
		id = name().toLowerCase(Locale.ROOT);
	}
	public String id() {
		return id;
	}
	public Block getBlock(String id) {
		return BuiltInRegistries.BLOCK.get(rl(id));
	}
	public ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(id, path);
	}
	public Item getItem(String id) {
		return BuiltInRegistries.ITEM.get(rl(id));
	}
	public boolean contains(ItemLike entry) {
		if (!isLoaded()) return false;
		var asItem = entry.asItem();
		return RegisteredObjectsHelper.getKeyOrThrow(asItem).getNamespace().equals(id);
	}
	public boolean isLoaded() {
		return LoadingModList.get().getModFileById(id) != null;
	}
	/** 模组已加载时才会执行 {@code toRun}，未加载返回 {@code Optional.empty()}。 */
	public <T> Optional<T> runIfInstalled(Supplier<T> toRun) {
		if (isLoaded()) return Optional.ofNullable(toRun.get());
		return Optional.empty();
	}
	public void executeIfInstalled(Runnable toExecute) {
		if (isLoaded()) toExecute.run();
	}
}
