package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.font.GlyphInfo.SpaceGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import io.github.forgestove.mlog.client.gui.LogicFont;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.nio.*;
import java.util.Objects;
import java.util.function.Function;
/** 带描边的字形：MC 的 {@code ttf} provider 无 {@code borderWidth}，故在上传字形位图前做形态学膨胀将轮廓向外扩 {@link #radius} 像素；环与芯烙入同一彩色位图，着色器将整张图乘上正文色，单个字形一次绘制完成。 */
public class OutlinedGlyphProvider implements GlyphProvider {
	/** MC 的 {@code TrueTypeGlyphProvider} 所用加载标志，沿用以保证度量一致。 */
	private static final int LOAD_FLAGS = 4194312;
	/** 描边宽度，位图上的像素数（已计入超采样）。 */
	private final int radius;
	/** 码点偏移：该 provider 只处理码点不小于偏移的部分，其余交给同一字体中排在其前的 provider，同一字体因而可并存原样与带描边两套字形。 */
	private final int offset;
	private final float oversample;
	private final IntSet skip = new IntArraySet();
	private @Nullable ByteBuffer fontMemory;
	private @Nullable FT_Face face;
	public OutlinedGlyphProvider(ByteBuffer memory, FT_Face face, float size, float oversample, float radius, int offset, String skip) {
		fontMemory = memory;
		this.face = face;
		this.oversample = oversample;
		this.offset = offset;
		// radius 以逻辑像素给出，位图上需乘超采样才能对应
		this.radius = Math.round(radius * oversample);
		skip.codePoints().forEach(this.skip::add);
		var pixelSize = Math.round(size * oversample);
		FreeType.FT_Set_Pixel_Sizes(face, pixelSize, pixelSize);
	}
	/** {@code core} 恒不大于 {@code ring}；返回值是 RGBA 位图所需的 ABGR 像素，灰阶，R=G=B。 */
	private static int bake(int ring, int core) {
		if (ring == 0) return 0;
		// 环占 ring - core、芯占 core；除以总覆盖还原为非预乘色值
		var gray = (LogicFont.OUTLINE_FACTOR * (ring - core) + 0xFF * core) / ring;
		return gray << 16 | gray << 8 | gray | ring << 24;
	}
	FT_Face validateFontOpen() {
		if (fontMemory == null || face == null) throw new IllegalStateException("Provider already closed");
		return face;
	}
	@Override
	public void close() {
		if (face != null) {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				FreeTypeUtil.checkError(FreeType.FT_Done_Face(face), "Deleting face");
			}
			face = null;
		}
		MemoryUtil.memFree(fontMemory);
		fontMemory = null;
	}
	@Nullable
	@Override
	// slot.bitmap() 返回借用引用，归属 FT_GlyphSlot，不应关闭
	@SuppressWarnings("resource")
	public GlyphInfo getGlyph(int character) {
		// 偏移范围外返回 null，该段留给同一字体中排在其前的 provider
		var code = character - offset;
		if (code < 0) return null;
		var face = validateFontOpen();
		if (skip.contains(code)) return null;
		var index = FreeType.FT_Get_Char_Index(face, code);
		if (index == 0) return null;
		FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(face, index, LOAD_FLAGS), "Loading glyph");
		var slot = Objects.requireNonNull(face.glyph(), "Glyph not initialized");
		var advance = FreeTypeUtil.x(slot.advance()) / oversample;
		FT_Bitmap bitmap = slot.bitmap();
		var w = bitmap.width();
		var h = bitmap.rows();
		// 空字形仅有前进量，无位图可膨胀
		if (w <= 0 || h <= 0) return (SpaceGlyphInfo) () -> advance;
		return new OutlinedGlyph(this, slot.bitmap_left(), slot.bitmap_top(), w, h, advance, index);
	}
	@Override
	public IntSet getSupportedGlyphs() {
		var face = validateFontOpen();
		var set = new IntOpenHashSet();
		try (var stack = MemoryStack.stackPush()) {
			IntBuffer buffer = stack.mallocInt(1);
			for (var c = FreeType.FT_Get_First_Char(face, buffer); buffer.get(0) != 0; c = FreeType.FT_Get_Next_Char(face, c, buffer))
				set.add((int) c + offset);
		}
		set.removeAll(skip);
		return set;
	}
	/** 膨胀取圆形邻域的灰度最大值，与抗锯齿位图相配：边缘的半透明像素向外铺开为实心，方形窗口会在对角方向多扩 √2 倍而出现尖角。 */
	private void uploadDilated(int xOffset, int yOffset, int srcW, int srcH, int dstW, int dstH, int glyphIndex) {
		var face = validateFontOpen();
		try (var src = new NativeImage(Format.LUMINANCE, srcW, srcH, false)) {
			if (!src.copyFromFont(face, glyphIndex)) return;
			try (var dst = new NativeImage(Format.RGBA, dstW, dstH, false)) {
				for (var y = 0; y < dstH; y++)
					for (var x = 0; x < dstW; x++) {
						var ring = 0;
						for (var dy = -radius; dy <= radius; dy++)
							for (var dx = -radius; dx <= radius; dx++) {
								// 圆形邻域：方形窗口在对角方向实际扩出 √2 倍，描边会出现尖角
								if (dx * dx + dy * dy > radius * radius) continue;
								var sx = x - radius + dx;
								var sy = y - radius + dy;
								if (sx < 0 || sy < 0 || sx >= srcW || sy >= srcH) continue;
								ring = Math.max(ring, src.getLuminanceOrAlpha(sx, sy) & 0xFF);
							}
						// 邻域中心格即原字形在该格上的覆盖：非零为芯，为零为环
						var cx = x - radius;
						var cy = y - radius;
						var core = cx < 0 || cy < 0 || cx >= srcW || cy >= srcH ? 0 : src.getLuminanceOrAlpha(cx, cy) & 0xFF;
						dst.setPixelRGBA(x, y, bake(ring, core));
					}
				dst.upload(0, xOffset, yOffset, 0, 0, dstW, dstH, false, true);
			}
		}
	}
	private static class OutlinedGlyph implements GlyphInfo {
		private final OutlinedGlyphProvider provider;
		private final float bearingX, bearingY, advance;
		private final int srcW, srcH, width, height, index;
		OutlinedGlyph(OutlinedGlyphProvider provider, int bearingX, int bearingY, int srcW, int srcH, float advance, int index) {
			this.provider = provider;
			// 位图向左上各扩 radius，故左上角相应偏移，宽度加两倍半径
			this.bearingX = (bearingX - provider.radius) / provider.oversample;
			this.bearingY = (bearingY + provider.radius) / provider.oversample;
			this.srcW = srcW;
			this.srcH = srcH;
			width = srcW + provider.radius * 2;
			height = srcH + provider.radius * 2;
			this.advance = advance;
			this.index = index;
		}
		@Override
		public float getAdvance() {
			return advance;
		}
		@Override
		public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> baker) {
			return baker.apply(new SheetGlyphInfo() {
				@Override
				public int getPixelWidth() {
					return width;
				}
				@Override
				public int getPixelHeight() {
					return height;
				}
				@Override
				public void upload(int xOffset, int yOffset) {
					provider.uploadDilated(xOffset, yOffset, srcW, srcH, width, height, index);
				}
				@Override
				public boolean isColored() {
					// 彩色位图：环与芯均已烙入字形，着色器会将整张图乘上正文色
					return true;
				}
				@Override
				public float getOversample() {
					return provider.oversample;
				}
				@Override
				public float getBearingLeft() {
					return bearingX;
				}
				@Override
				public float getBearingTop() {
					return bearingY;
				}
			});
		}
	}
}
