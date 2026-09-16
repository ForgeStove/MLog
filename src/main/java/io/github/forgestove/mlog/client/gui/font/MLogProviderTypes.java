package io.github.forgestove.mlog.client.gui.font;
import io.github.forgestove.mlog.MLog;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
/**
 * 往 MC 的 {@code GlyphProviderType} 里注入的字体类型。
 * <p>MC 那个枚举被 NeoForge 标了 {@code @NamedEnum} + {@code IExtensibleEnum}，可以在运行时用 ASM 加值；
 * 声明写在 {@code META-INF/enumextensions.json}，由 {@code neoforge.mods.toml} 的
 * {@code enumExtensions} 指向它。{@code @NamedEnum} 要求名字是 {@code modid:name} 格式。
 */
@OnlyIn(Dist.CLIENT)
public final class MLogProviderTypes {
	/** 字体 json 里写 {@code "type": "mlog:outlined"} 即可用上，字段见 {@link OutlinedGlyphProviderDefinition}。 */
	public static final EnumProxy<GlyphProviderType> OUTLINED = new EnumProxy<>(
		GlyphProviderType.class,
		MLog.ID + ":outlined",
		OutlinedGlyphProviderDefinition.CODEC
	);
}
