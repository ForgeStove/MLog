package io.github.forgestove.mlog.logic;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
/** 语句表：扫一遍带 {@link Statement} 的语句类，按语句名建索引，供 {@link LParser} 分派与语句表读取。 */
public final class Statements {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** 语句名到语句类。名字取自类名（{@link LStatement#typeName(Class)}），与语句表、lang key 同源。 */
	private static final Map<String, Class<? extends LStatement>> CLASSES = new TreeMap<>();
	static {
		var annoName = Statement.class.getName();
		ModList.get().getAllScanData().forEach(scanData -> scanData.getAnnotations().forEach(annoData -> {
			if (!annoName.equals(annoData.annotationType().getClassName())) return;
			// 用 clazz().getClassName()：嵌套类是 Outer$Inner 形式，Class.forName 认这个
			var name = annoData.clazz().getClassName();
			try {
				var cls = Class.forName(name);
				if (!LStatement.class.isAssignableFrom(cls)) return;
				CLASSES.put(LStatement.typeName(cls), cls.asSubclass(LStatement.class));
			} catch (Exception e) {
				LOGGER.error("Unable to load logic statement: {}", name, e);
			}
		}));
	}
	/** 语句表里可选的语句类型，按语句名排序，同类语句之间就是这个显示顺序。 */
	public static final List<Supplier<LStatement>> ALL = CLASSES.keySet().stream()
		.map(name -> (Supplier<LStatement>) () -> create(name))
		.toList();
	/** @return 语句名对应的新实例，认不出来或造不出来时返回 {@code null}。 */
	private static @Nullable LStatement create(String name) {
		var cls = CLASSES.get(name);
		if (cls == null) return null;
		try {
			return cls.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			LOGGER.error("Unable to instantiate logic statement: {}", name, e);
			return null;
		}
	}
	/**
	 * 按首 token 分派到各语句的解析器。
	 *
	 * @return 语句名认不出来、或造不出实例时返回 {@code null}，由调用处换成无法解析的占位；
	 * 	语句自己抛的异常（如未知的运算名）照旧往外传，那边也是按无法解析处理
	 */
	public static @Nullable LStatement parse(String[] tokens, int length) {
		var statement = create(tokens[0]);
		return statement == null ? null : statement.parse(tokens, length);
	}
}
