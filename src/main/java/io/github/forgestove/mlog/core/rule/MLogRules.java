package io.github.forgestove.mlog.core.rule;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;
/**
 * 本存档的逻辑处理器规则，由 {@code /mlog gamerule} 读写。
 * <p>存在主世界的存档数据里，和原版 {@code /gamerule} 一样按存档算，换存档不会串。
 */
public class MLogRules extends SavedData {
	/** 存档数据名。 */
	private static final String ID = "mlog_rules";
	/** 查表用的工厂，提成常量免得每次调用都新建一个。 */
	private static final Factory<MLogRules> FACTORY = new Factory<>(MLogRules::new, MLogRules::load);
	/** 可以开关的规则，名字就是 {@code /mlog gamerule} 里的那个词。都是默认关。 */
	public enum Rule {
		/** 微型逻辑处理器不执行。 */
		disableMicroProcessor,
		/** 世界处理器不执行。 */
		disableWorldProcessor,
		;
		/** @return 规则名的本地化键。 */
		public String key() {
			return "command.mlog.gamerule." + name();
		}
		/** @return 名字对应的规则，认不出来时返回 {@code null}。 */
		public static Rule byName(String name) {
			for (var rule : values()) if (rule.name().equals(name)) return rule;
			return null;
		}
	}
	private final EnumMap<Rule, Boolean> values = new EnumMap<>(Rule.class);
	/** @return 这条规则的值。 */
	public boolean get(Rule rule) {
		return values.getOrDefault(rule, false);
	}
	/** 改一条规则。 */
	public void set(Rule rule, boolean value) {
		values.put(rule, value);
		setDirty();
	}
	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull Provider registries) {
		for (var rule : Rule.values()) tag.putBoolean(rule.name(), get(rule));
		return tag;
	}
	private static MLogRules load(CompoundTag tag, Provider registries) {
		var rules = new MLogRules();
		for (var rule : Rule.values()) if (tag.contains(rule.name())) rules.values.put(rule, tag.getBoolean(rule.name()));
		return rules;
	}
	/** @return 这个服务器的规则表。 */
	public static MLogRules get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
	}
}
