package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.font.providers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.io.*;
import java.nio.ByteBuffer;
/**
 * 描边字体的配置，对应字体 json 里的 {@code "type": "mlog:outlined"}。
 * <p>字段与 MC 的 {@code ttf} 一致，另加一个 {@code radius} 控制描边宽度。
 */
@OnlyIn(Dist.CLIENT)
public record OutlinedGlyphProviderDefinition(
	ResourceLocation location, float size, float oversample, int radius, String skip
) implements GlyphProviderDefinition {
	public static final MapCodec<OutlinedGlyphProviderDefinition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("file").forGetter(OutlinedGlyphProviderDefinition::location),
		Codec.FLOAT.optionalFieldOf("size", 11F).forGetter(OutlinedGlyphProviderDefinition::size),
		Codec.FLOAT.optionalFieldOf("oversample", 1F).forGetter(OutlinedGlyphProviderDefinition::oversample),
		Codec.INT.optionalFieldOf("radius", 1).forGetter(OutlinedGlyphProviderDefinition::radius),
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
			memory = TextureUtil.readResource(in);
			memory.flip();
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
			return new OutlinedGlyphProvider(memory, face, size, oversample, radius, skip);
		} catch (Exception e) {
			if (face != null) synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				FreeType.FT_Done_Face(face);
			}
			MemoryUtil.memFree(memory);
			throw e;
		}
	}
}
