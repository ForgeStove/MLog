package io.github.forgestove.mlog.logic;
import java.lang.annotation.*;
/** 标记可被逻辑代码解析的语句类，供 {@link Statements} 经 NeoForge ASM 扫描自动发现并注册。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterStatement {
	/** 语句名，取值为注册名。 */
	String id();
	/** 语句表内的显示顺序，以递增方式排序，同序时按语句名排序。 */
	int order() default Integer.MAX_VALUE;
}
