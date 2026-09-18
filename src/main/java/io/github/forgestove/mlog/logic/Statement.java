package io.github.forgestove.mlog.logic;
import java.lang.annotation.*;
/** 标记能被逻辑代码解析的语句类，使得 {@link Statements} 通过 NeoForge ASM 扫描自动发现并注册 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Statement {}
