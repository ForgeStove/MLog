package io.github.forgestove.mlog.client.gui.font;
import io.github.forgestove.mlog.MLog;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
/**
 * 注入到 MC {@code GlyphProviderType} 的字体类型。
 * <p>该枚举被 NeoForge 标注 {@code @NamedEnum} 与 {@code IExtensibleEnum}，可在运行时经 ASM 增加值；
 * 声明位于 {@code META-INF/enumextensions.json}，由 {@code neoforge.mods.toml} 的
 * {@code enumExtensions} 指向。{@code @NamedEnum} 要求名字为 {@code modid:name} 格式。
 */
@OnlyIn(Dist.CLIENT)
public final class MLogProviderTypes {
	/** 字体 json 中写 {@code "type": "mlog:outlined"} 即可启用，字段见 {@link OutlinedGlyphProviderDefinition}。 */
	public static final EnumProxy<GlyphProviderType> OUTLINED = new EnumProxy<>(
		GlyphProviderType.class,
		MLog.ID + ":outlined",
		OutlinedGlyphProviderDefinition.CODEC
	);
}
