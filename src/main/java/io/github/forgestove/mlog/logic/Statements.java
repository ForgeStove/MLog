package io.github.forgestove.mlog.logic;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;
/** 语句表：收集 {@link RegisterStatement} 标注的语句类，供 {@link LParser} 分派与界面枚举。 */
public final class Statements {
	/** 全部语句条目，按注解顺序排序，同序时按语句名；条目不含语句状态。 */
	public static final List<Entry> ALL;
	private static final Logger LOGGER = LogUtils.getLogger();
	/** 语句名到条目的索引，供 {@link #parse} 按首个 token 分派。 */
	private static final Map<String, Entry> BY_ID = new HashMap<>();
	/** 语句名到注解顺序的索引，仅用于排序。 */
	private static final Map<String, Integer> ORDERS = new HashMap<>();
	static {
		var annoName = RegisterStatement.class.getName();
		List<Entry> entries = new ArrayList<>();
		ModList.get().getAllScanData().forEach(scanData -> scanData.getAnnotations().forEach(annoData -> {
			if (!annoName.equals(annoData.annotationType().getClassName())) return;
			var name = annoData.clazz().getClassName();
			try {
				var cls = Class.forName(name);
				if (!MLogStatement.class.isAssignableFrom(cls)) return;
				var anno = cls.getAnnotation(RegisterStatement.class);
				if (anno == null) return;
				entries.add(entry(anno, cls.asSubclass(MLogStatement.class)));
			} catch (Exception e) {
				LOGGER.error("Unable to load logic statement: {}", name, e);
			}
		}));
		entries.sort(Comparator.comparingInt((Entry e) -> ORDERS.getOrDefault(e.id(), Integer.MAX_VALUE)).thenComparing(Entry::id));
		ALL = List.copyOf(entries);
		for (var entry : ALL) BY_ID.put(entry.id(), entry);
	}
	/** @return 语句表条目；分类、特权、隐藏三项取自原型实例，构造器随之缓存。 */
	private static Entry entry(RegisterStatement anno, Class<? extends MLogStatement> cls) throws ReflectiveOperationException {
		var constructor = cls.getDeclaredConstructor();
		var prototype = newInstance(constructor, anno.id());
		ORDERS.put(anno.id(), anno.order());
		return new Entry(
			anno.id(),
			prototype.category(),
			prototype.privileged(),
			prototype.hidden(),
			() -> newInstance(constructor, anno.id())
		);
	}
	/** @return 带语句名的语句实例。 */
	private static MLogStatement newInstance(Constructor<? extends MLogStatement> constructor, String id) {
		try {
			var statement = constructor.newInstance();
			// 语句名在此注入，实例侧无须查询注解
			statement.id = id;
			return statement;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to instantiate logic statement: " + id, e);
		}
	}
	/** 按首个 token 分派到对应的解析器；语句名未注册或实例化失败时抛出异常，由 {@link LParser} 捕获并替换为占位语句。 */
	public static MLogStatement parse(String[] tokens, int length) {
		var statement = Objects.requireNonNull(BY_ID.get(tokens[0])).factory().get().parse(tokens, length);
		// 读取完成后修正字段
		statement.afterRead();
		return statement;
	}
	/** 语句表条目：语句的类级信息与实例工厂。 */
	public record Entry(String id, LCategory category, boolean privileged, boolean hidden, Supplier<MLogStatement> factory) {
		/** @return 语句名的 lang key。 */
		public String nameKey() {
			return MLogStatement.nameKey(id);
		}
		/** @return 语句说明的 lang key，没有对应文本时语句表不出悬停提示。 */
		public String tipKey() {
			return MLogStatement.tipKey(id);
		}
	}
}
