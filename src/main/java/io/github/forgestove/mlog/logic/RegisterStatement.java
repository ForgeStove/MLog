package io.github.forgestove.mlog.logic;
import java.lang.annotation.*;
/** 标记能被逻辑代码解析的语句类，使得 {@link Statements} 通过 NeoForge ASM 扫描自动发现并注册 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterStatement {
	/** 语句名。分派文本、查语言键、搜语句表都用它，不跟类名绑；取值是注册名。 */
	String id();
	/** 语句表里的显示顺序，以递增方式排序；数值是位次，没移植的语句留着空位。同序时按语句名排序。 */
	int order() default Integer.MAX_VALUE;
}
