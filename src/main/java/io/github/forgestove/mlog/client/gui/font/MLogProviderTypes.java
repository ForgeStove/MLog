package io.github.forgestove.mlog.client.gui.font;
import io.github.forgestove.mlog.MLog;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
/**
 * 注入到 MC {@code GlyphProviderType} 的字体类型：NeoForge 的 {@code @NamedEnum} 与 {@code IExtensibleEnum} 允许运行时经 ASM 增加值，名字须为 {@code modid:name}
 * ，并在 {@code META-INF/enumextensions.json} 声明、由 {@code neoforge.mods.toml} 的 {@code enumExtensions} 指向。
 */
public class MLogProviderTypes {
	// 字体 json 中写 "type": "mlog:outlined" 即启用，字段见 OutlinedGlyphProviderDefinition
	public static final EnumProxy<GlyphProviderType> OUTLINED = new EnumProxy<>(
		GlyphProviderType.class,
		MLog.ID + ":outlined",
		OutlinedGlyphProviderDefinition.CODEC
	);
	// 字体 json 中写 "type": "mlog:ttf" 即启用，字段见 TTFGlyphProviderDefinition
	public static final EnumProxy<GlyphProviderType> TTF = new EnumProxy<>(
		GlyphProviderType.class,
		MLog.ID + ":ttf",
		TTFGlyphProviderDefinition.CODEC
	);
}
