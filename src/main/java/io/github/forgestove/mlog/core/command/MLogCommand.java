package io.github.forgestove.mlog.core.command;
import com.mojang.brigadier.arguments.*;
import io.github.forgestove.mlog.core.rule.MLogRules;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
/**
 * {@code /mlog} 命令。
 * <p>目前只有 {@code /mlog gamerule <规则> [值]}：写法照原版 {@code /gamerule}，不写值就是查询。
 */
public final class MLogCommand {
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("mlog").then(Commands.literal("gamerule")
			// 和原版 /gamerule 一样要权限等级 2
			.requires(source -> source.hasPermission(2))
			.then(Commands.argument("rule", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(
					Arrays.stream(Rule.values()).map(Rule::name).toList(),
					builder
				))
				.executes(context -> query(context.getSource(), StringArgumentType.getString(context, "rule")))
				.then(Commands.argument("value", BoolArgumentType.bool())
					.executes(context -> set(
						context.getSource(),
						StringArgumentType.getString(context, "rule"),
						BoolArgumentType.getBool(context, "value")
					))))));
	}
	/** 读一条规则。返回值即规则当前的值，`/execute if` 之类可以直接用。 */
	private static int query(CommandSourceStack source, String name) {
		var rule = Rule.byName(name);
		if (rule == null) return unknown(source, name);
		var value = MLogRules.get(source.getServer()).get(rule);
		source.sendSuccess(() -> Component.translatable("command.mlog.gamerule.query", Component.translatable(rule.key()), value), false);
		return value ? 1 : 0;
	}
	/** 改一条规则。 */
	private static int set(CommandSourceStack source, String name, boolean value) {
		var rule = Rule.byName(name);
		if (rule == null) return unknown(source, name);
		MLogRules.get(source.getServer()).set(rule, value);
		source.sendSuccess(() -> Component.translatable("command.mlog.gamerule.set", Component.translatable(rule.key()), value), true);
		return value ? 1 : 0;
	}
	/** @return 报「没有这条规则」，命令按失败处理。 */
	private static int unknown(CommandSourceStack source, String name) {
		source.sendFailure(Component.translatable("command.mlog.gamerule.unknown", name));
		return 0;
	}
}
