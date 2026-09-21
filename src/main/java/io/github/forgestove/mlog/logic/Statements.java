package io.github.forgestove.mlog.logic;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;
/**
 * 语句表。扫描带有 {@link RegisterStatement} 注解的语句类，按语句名建立索引，供 {@link LParser} 分派及语句表读取。
 */
public final class Statements {
	/**
	 * 所有可选语句类型，先按注解顺序、再按语句名排序。
	 * <p>{@code STATEMENT_CLASSES} 为 {@link TreeMap}，初始按名称有序；排序稳定，因此相同顺序的语句按名称排列。
	 */
	public static final List<Supplier<MLogStatement>> ALL;
	private static final Logger LOGGER = LogUtils.getLogger();
	/** 语句名到语句类的映射。名称取自 {@link RegisterStatement#id()}，与语句表及语言键同源。 */
	private static final Map<String, Class<? extends MLogStatement>> STATEMENT_CLASSES = new TreeMap<>();
	/** 语句名到注解顺序的映射，数值是该语句的位次。 */
	private static final Map<String, Integer> ORDERS = new HashMap<>();
	static {
		var annoName = RegisterStatement.class.getName();
		ModList.get().getAllScanData().forEach(scanData -> scanData.getAnnotations().forEach(annoData -> {
			if (!annoName.equals(annoData.annotationType().getClassName())) return;
			var name = annoData.clazz().getClassName();
			try {
				var cls = Class.forName(name);
				if (!MLogStatement.class.isAssignableFrom(cls)) return;
				var anno = cls.getAnnotation(RegisterStatement.class);
				if (anno == null) return;
				STATEMENT_CLASSES.put(anno.id(), cls.asSubclass(MLogStatement.class));
				ORDERS.put(anno.id(), anno.order());
			} catch (Exception e) {
				LOGGER.error("Unable to load logic statement: {}", name, e);
			}
		}));
		List<String> toSort = new ArrayList<>(STATEMENT_CLASSES.keySet());
		toSort.sort(Comparator.comparingInt((String n) -> ORDERS.getOrDefault(n, Integer.MAX_VALUE)));
		List<Supplier<MLogStatement>> list = new ArrayList<>();
		for (String name : toSort) {
			Supplier<MLogStatement> lStatementSupplier = () -> create(name);
			list.add(lStatementSupplier);
		}
		ALL = list;
	}
	/**
	 * 根据首个 token 分派到对应语句解析器。
	 *
	 * @return 语句名未知或无法实例化时返回 {@code null}，由调用方替换为无法解析占位；语句自身抛出的异常（如未知运算名）继续向外抛出，由调用方按无法解析处理。
	 */
	public static MLogStatement parse(String[] tokens, int length) {
		var statement = Objects.requireNonNull(create(tokens[0]));
		statement.parse(tokens, length);
		// 读完再修字段
		statement.afterRead();
		return statement;
	}
	/** @return 语句名对应的新实例；语句名未知或实例化失败时返回 {@code null}。 */
	private static @Nullable MLogStatement create(String name) {
		var cls = STATEMENT_CLASSES.get(name);
		if (cls == null) return null;
		try {
			return cls.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			LOGGER.error("Unable to instantiate logic statement: {}", name, e);
			return null;
		}
	}
}