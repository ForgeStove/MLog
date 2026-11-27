package io.github.forgestove.mlog.logic;
import java.lang.annotation.*;
/** 标记可被逻辑代码解析的语句类，供 {@link Statements} 经 NeoForge ASM 扫描自动发现并注册。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterStatement {
	String id();
	/** 语句在列表中的排序值，按升序排列；值相同时按语句名排序。 */
	int order() default Integer.MAX_VALUE;
}
