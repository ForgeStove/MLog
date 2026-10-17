package io.github.forgestove.mlog.logic;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.*;

import java.util.*;
/** {@code setflag} / {@code getflag} 的全局标记：跨维度共用一份并随存档保存。 */
public class MLogFlags extends SavedData {
	private static final String ID = "mlog_flags", KEY = "flags";
	private static final Factory<MLogFlags> FACTORY = new Factory<>(MLogFlags::new, MLogFlags::load);
	/** 指令每次执行都要读标记，而存档数据查询不便宜。 */
	private static @Nullable MinecraftServer cachedServer;
	private static @Nullable MLogFlags cached;
	private final Set<String> flags = new HashSet<>();
	/** 客户端世界返回 {@code null}。 */
	public static @Nullable MLogFlags of(@Nullable Level level) {
		return level instanceof ServerLevel serverLevel ? get(serverLevel.getServer()) : null;
	}
	public static MLogFlags get(MinecraftServer server) {
		// 同一服务器只持有一份标记表，按引用判等即可
		if (server != cachedServer) {
			cachedServer = server;
			cached = server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
		}
		return Objects.requireNonNull(cached);
	}
	private static MLogFlags load(CompoundTag tag, Provider registries) {
		var data = new MLogFlags();
		var list = tag.getList(KEY, Tag.TAG_STRING);
		for (var i = 0; i < list.size(); i++) data.flags.add(list.getString(i));
		return data;
	}
	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull Provider registries) {
		var list = new ListTag();
		for (var flag : flags) list.add(StringTag.valueOf(flag));
		tag.put(KEY, list);
		return tag;
	}
	public boolean has(String flag) {
		return flags.contains(flag);
	}
	public void set(String flag, boolean value) {
		if (value ? flags.add(flag) : flags.remove(flag)) setDirty();
	}
}
