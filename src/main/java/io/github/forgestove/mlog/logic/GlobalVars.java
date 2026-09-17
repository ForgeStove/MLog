package io.github.forgestove.mlog.logic;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 内置变量表，对齐 Mindustry 的 {@code GlobalVars}：既是 {@link LAssembler} 编译时查的常量表，
 * 也是内置变量界面上的文档。
 * <p>随世界变化的变量在所有处理器之间<b>共享同一个</b> {@link LVar} 实例，由 {@link #update} 每 tick 刷新，
 * 这样 {@code @time} 才是活的——Mindustry 也是共享实例而不是各处理器拷一份。
 * <p>Mindustry 的地图尺寸（{@code @mapw} / {@code @maph}）在 MC 里没有对应概念（世界没有固定宽高），不保留。
 */
public final class GlobalVars {
	/** 界面上的分组与条目，顺序即显示顺序。 */
	public static final List<Entry> ENTRIES = List.of(
		Entry.section("sectionProcessor"),
		Entry.var("@this"),
		Entry.var("@thisx"),
		Entry.var("@thisy"),
		Entry.var("@thisz"),
		Entry.var("@links"),
		Entry.var("@ipt"),
		Entry.var("@counter"),
		Entry.section("sectionGeneral"),
		Entry.var("true"),
		Entry.var("false"),
		Entry.var("@pi"),
		Entry.var("@e"),
		Entry.var("@degToRad"),
		Entry.var("@radToDeg"),
		Entry.section("sectionTime"),
		Entry.var("@time"),
		Entry.var("@tick"),
		Entry.var("@second"),
		Entry.var("@minute")
	);
	/** 名字到内置变量。 */
	private static final Map<String, LVar> VARS = build();
	private static Map<String, LVar> build() {
		var map = new LinkedHashMap<String, LVar>();
		map.put("true", constant("true", 1));
		map.put("false", constant("false", 0));
		map.put("@pi", constant("@pi", Math.PI));
		map.put("@e", constant("@e", Math.E));
		map.put("@degToRad", constant("@degToRad", Math.PI / 180));
		map.put("@radToDeg", constant("@radToDeg", 180 / Math.PI));
		// 时间类的值随世界变化，先占位，由 update 刷新
		map.put("@time", constant("@time"));
		map.put("@tick", constant("@tick"));
		map.put("@second", constant("@second"));
		map.put("@minute", constant("@minute"));
		return Collections.unmodifiableMap(map);
	}
	private static LVar constant(String name, double value) {
		var var = new LVar(name);
		var.constant = true;
		var.numval = value;
		return var;
	}
	private static LVar constant(String name) {
		return constant(name, 0);
	}
	/** @return 名字对应的内置变量，没有则返回 {@code null}。 */
	public static @Nullable LVar get(String name) {
		return VARS.get(name);
	}
	/**
	 * 刷新随世界变化的变量，处理器的 tick 里调用。多个处理器重复调用是幂等的。
	 * <p>这里直接写 {@code numval}，绕过 {@link LVar#setnum} 对常量的写保护。
	 */
	public static void update(Level level) {
		var tick = level.getGameTime();
		VARS.get("@tick").numval = tick;
		VARS.get("@time").numval = tick * 50D;
		VARS.get("@second").numval = tick / 20D;
		VARS.get("@minute").numval = tick / 1200D;
	}
	/** 一个条目：分组标题（{@code name} 以 {@code section} 开头）或一个变量。 */
	public record Entry(String name, boolean section) {
		static Entry section(String name) {
			return new Entry(name, true);
		}
		static Entry var(String name) {
			return new Entry(name, false);
		}
		/** @return 说明文字的本地化键，与 Mindustry 一致。 */
		public String descKey() {
			return "lglobal." + name;
		}
	}
}
