package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.font.providers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.io.*;
import java.nio.ByteBuffer;
/**
 * 描边字体配置，对应字体 json 中的 {@code "type": "mlog:outlined"}：{@code radius} 为描边宽度，{@code offset} 为码点偏移下限，该 provider 只接管码点不小于它的字形。
 * 同一字体可借此排入第二套膨胀字形，与其前的 {@code ttf} provider 互不干扰，两套字形共存于同一张图谱；{@code skip} 中的码点不生成描边字形。
 */
public record OutlinedGlyphProviderDefinition(
	ResourceLocation location, float size, float oversample, float radius, int offset, String skip
) implements GlyphProviderDefinition {
	public static final MapCodec<OutlinedGlyphProviderDefinition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("file").forGetter(OutlinedGlyphProviderDefinition::location),
		Codec.FLOAT.optionalFieldOf("size", 11F).forGetter(OutlinedGlyphProviderDefinition::size),
		Codec.FLOAT.optionalFieldOf("oversample", 1F).forGetter(OutlinedGlyphProviderDefinition::oversample),
		Codec.FLOAT.optionalFieldOf("radius", 1F).forGetter(OutlinedGlyphProviderDefinition::radius),
		Codec.INT.optionalFieldOf("offset", 0).forGetter(OutlinedGlyphProviderDefinition::offset),
		Codec.STRING.optionalFieldOf("skip", "").forGetter(OutlinedGlyphProviderDefinition::skip)
	).apply(instance, OutlinedGlyphProviderDefinition::new));
	@Override
	public GlyphProviderType type() {
		return MLogProviderTypes.OUTLINED.getValue();
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
			}
			return new OutlinedGlyphProvider(memory, face, size, oversample, radius, offset, skip);
		} catch (Exception e) {
			if (face != null) synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				FreeType.FT_Done_Face(face);
			}
			MemoryUtil.memFree(memory);
			throw e;
		}
	}
}
