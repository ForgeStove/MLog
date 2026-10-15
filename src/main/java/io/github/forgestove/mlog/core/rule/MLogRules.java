package io.github.forgestove.mlog.core.rule;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.*;

import java.util.*;
/** 本存档的逻辑处理器规则，由 {@code /mlog gamerule} 读写；存在主世界的存档数据里，换存档不会串。 */
public class MLogRules extends SavedData {
	private static final String ID = "mlog_rules";
	private static final Factory<MLogRules> FACTORY = new Factory<>(MLogRules::new, MLogRules::load);
	/** 热路径上每 tick 都要读规则，而存档数据查询不便宜。 */
	private static @Nullable MinecraftServer cachedServer;
	private static @Nullable MLogRules cached;
	private final EnumMap<Rule, Boolean> values = new EnumMap<>(Rule.class);
	private static MLogRules load(CompoundTag tag, Provider registries) {
		var rules = new MLogRules();
		for (var rule : Rule.values()) if (tag.contains(rule.name())) rules.values.put(rule, tag.getBoolean(rule.name()));
		return rules;
	}
	public static MLogRules get(MinecraftServer server) {
		// 同一服务器只持有一份规则表，按引用判等即可
		if (server != cachedServer) {
			cachedServer = server;
			cached = server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
		}
		return Objects.requireNonNull(cached);
	}
	public void set(Rule rule, boolean value) {
		values.put(rule, value);
		setDirty();
	}
	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull Provider registries) {
		for (var rule : Rule.values()) tag.putBoolean(rule.name(), get(rule));
		return tag;
	}
	public boolean get(Rule rule) {
		return values.getOrDefault(rule, false);
	}
	/** 可开关的规则，名字即 {@code /mlog gamerule} 里的参数名，默认全关。 */
	public enum Rule {
		disableMicroProcessor,
		disableWorldProcessor,
		;
		/** 认不出来时返回 {@code null}。 */
		public static Rule byName(String name) {
			for (var rule : values()) if (rule.name().equals(name)) return rule;
			return null;
		}
		public String key() {
			return "command.mlog.gamerule." + name();
		}
	}
}
