package io.github.forgestove.mlog.core;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.LoadingModList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.function.Supplier;
@SuppressWarnings("unused")
public enum Mods {
	minecraft,
	create,
	sable,
	sableCompanion,
	;
	private final String id;
	private volatile boolean loaded;
	Mods() {
		id = name().toLowerCase(Locale.ROOT);
	}
	/**
	 * @return 模组 id
	 */
	@Contract(pure = true)
	public String id() {
		return id;
	}
	public @NonNull Block getBlock(String id) {
		return BuiltInRegistries.BLOCK.get(rl(id));
	}
	@Contract("_ -> new")
	public @NonNull ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(id, path);
	}
	public @NonNull Item getItem(String id) {
		return BuiltInRegistries.ITEM.get(rl(id));
	}
	public boolean contains(ItemLike entry) {
		if (!isLoaded()) return false;
		Item asItem = entry.asItem();
		return RegisteredObjectsHelper.getKeyOrThrow(asItem).getNamespace().equals(id);
	}
	public boolean isLoaded() {
		if (!loaded) loaded = LoadingModList.get().getModFileById(id) != null;
		return loaded;
	}
	/**
	 * 模组已加载时执行代码并返回结果
	 *
	 * @param toRun 仅在模组已加载时执行
	 * @return 模组未加载返回 {@link Optional#empty()}，否则返回 {@link Supplier<T> toRun} 的值
	 */
	public <T> Optional<T> runIfInstalled(Supplier<T> toRun) {
		if (isLoaded()) return Optional.ofNullable(toRun.get());
		return Optional.empty();
	}
	/**
	 * 模组已加载时执行代码（无返回值）
	 *
	 * @param toExecute 仅在模组已加载时执行
	 */
	public void executeIfInstalled(Runnable toExecute) {
		if (isLoaded()) toExecute.run();
	}
}
