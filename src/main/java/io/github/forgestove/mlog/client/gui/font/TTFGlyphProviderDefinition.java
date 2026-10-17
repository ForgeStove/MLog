package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.*;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.font.providers.*;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition.Shift;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.io.*;
import java.nio.ByteBuffer;
/** 常规字形的 provider，对应字体 json 中的 {@code "type": "mlog:regular"}，资源可以是 WOFF2，见 {@link FontData}。 */
public record TTFGlyphProviderDefinition(
	ResourceLocation location, float size, float oversample, Shift shift, String skip
) implements GlyphProviderDefinition {
	/** {@code skip} 也可写成字符串数组。 */
	public static final Codec<String> SKIP_CODEC = Codec.withAlternative(
		Codec.STRING,
		Codec.STRING.listOf(),
		list -> String.join("", list)
	);
	public static final MapCodec<TTFGlyphProviderDefinition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("file").forGetter(TTFGlyphProviderDefinition::location),
		Codec.FLOAT.optionalFieldOf("size", 11F).forGetter(TTFGlyphProviderDefinition::size),
		Codec.FLOAT.optionalFieldOf("oversample", 1F).forGetter(TTFGlyphProviderDefinition::oversample),
		Shift.CODEC.optionalFieldOf("shift", Shift.NONE).forGetter(TTFGlyphProviderDefinition::shift),
		SKIP_CODEC.optionalFieldOf("skip", "").forGetter(TTFGlyphProviderDefinition::skip)
	).apply(instance, TTFGlyphProviderDefinition::new));
	@Override
	public GlyphProviderType type() {
		return MLogProviderTypes.REGULAR.getValue();
	}
	@Override
	public Either<Loader, Reference> unpack() {
		return Either.left(this::load);
	}
	private GlyphProvider load(ResourceManager resourceManager) throws IOException {
		FT_Face face = null;
		ByteBuffer memory = null;
		try (InputStream in = resourceManager.open(location.withPrefix("font/"))) {
			memory = FontData.read(in);
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pointers = stack.mallocPointer(1);
					FreeTypeUtil.assertError(
						FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0L, pointers),
						"Initializing font face"
					);
					face = FT_Face.create(pointers.get());
				}
				var format = FreeType.FT_Get_Font_Format(face);
				if (!"TrueType".equals(format)) throw new IOException("字体不是 TrueType 格式，实际为 " + format);
				FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
				return new TrueTypeGlyphProvider(memory, face, size, oversample, shift.x(), shift.y(), skip);
			}
		} catch (Exception e) {
			if (face != null) synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				FreeType.FT_Done_Face(face);
			}
			MemoryUtil.memFree(memory);
			throw e;
		}
	}
}
