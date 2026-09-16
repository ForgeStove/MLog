package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.font.GlyphInfo.SpaceGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.nio.*;
import java.util.Objects;
import java.util.function.Function;
/**
 * 带描边的字形，对齐 Mindustry 的 {@code Fonts.outline}。
 * <p>MC 自带的 {@code ttf} provider 没有 {@code borderWidth} 参数，字形是"瘦"的；
 * 这里在字形位图上传前做一次形态学膨胀，把轮廓向外扩 {@link #radius} 像素。
 * <p>关键点是膨胀后依然是<b>单通道</b>位图，所以照样能被 tint——这正是双层绘制的基础：
 * 先用这份字体铺一层描边色，再用普通字体把正文色压上去，被盖住的中心自然只剩下一圈描边。
 * <p>Mindustry 是在 FreeType 生成字形时就把描边烘焙进去的，一次画完；MC 没有那个参数，
 * 只能多铺一层。
 */
@OnlyIn(Dist.CLIENT)
public class OutlinedGlyphProvider implements GlyphProvider {
	/** MC 的 {@code TrueTypeGlyphProvider} 用的加载标志，照搬以保证度量一致。 */
	private static final int LOAD_FLAGS = 4194312;
	/** 描边宽度，位图上的像素数（已把超采样算进去）。 */
	private final int radius;
	private final float oversample;
	private final IntSet skip = new IntArraySet();
	private @Nullable ByteBuffer fontMemory;
	private @Nullable FT_Face face;
	public OutlinedGlyphProvider(ByteBuffer memory, FT_Face face, float size, float oversample, int radius, String skip) {
		fontMemory = memory;
		this.face = face;
		this.oversample = oversample;
		// radius 按逻辑像素给，位图上得乘超采样才对应得上
		this.radius = Math.round(radius * oversample);
		skip.codePoints().forEach(this.skip::add);
		var pixelSize = Math.round(size * oversample);
		FreeType.FT_Set_Pixel_Sizes(face, pixelSize, pixelSize);
	}
	@Nullable
	@Override
	// slot.bitmap() 返回的是借来的引用，归 FT_GlyphSlot 所有，不该关闭
	@SuppressWarnings("resource")
	public GlyphInfo getGlyph(int character) {
		var face = validateFontOpen();
		if (skip.contains(character)) return null;
		var index = FreeType.FT_Get_Char_Index(face, character);
		if (index == 0) return null;
		FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(face, index, LOAD_FLAGS), "Loading glyph");
		var slot = Objects.requireNonNull(face.glyph(), "Glyph not initialized");
		var advance = FreeTypeUtil.x(slot.advance()) / oversample;
		FT_Bitmap bitmap = slot.bitmap();
		var w = bitmap.width();
		var h = bitmap.rows();
		// 空字形（空格之类）只有前进量，没有位图可扩
		if (w <= 0 || h <= 0) return (SpaceGlyphInfo) () -> advance;
		return new OutlinedGlyph(this, slot.bitmap_left(), slot.bitmap_top(), w, h, advance, index);
	}
	FT_Face validateFontOpen() {
		if (fontMemory == null || face == null) throw new IllegalStateException("Provider already closed");
		return face;
	}
	@Override
	public IntSet getSupportedGlyphs() {
		var face = validateFontOpen();
		var set = new IntOpenHashSet();
		try (var stack = MemoryStack.stackPush()) {
			IntBuffer buffer = stack.mallocInt(1);
			for (var c = FreeType.FT_Get_First_Char(face, buffer); buffer.get(0) != 0; c = FreeType.FT_Get_Next_Char(face, c, buffer))
				set.add((int) c);
		}
		set.removeAll(skip);
		return set;
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
	/**
	 * 把字形位图向外膨胀 {@link #radius} 像素后上传。
	 * <p>膨胀用的是形态学里的"取邻域最大值"，对灰度抗锯齿位图正好合适——边缘那圈半透明像素
	 * 会被向外铺开成实心，不会像多次偏移绘制那样叠出虚边。
	 */
	private void uploadDilated(int xOffset, int yOffset, int srcW, int srcH, int dstW, int dstH, int glyphIndex) {
		var face = validateFontOpen();
		try (var src = new NativeImage(Format.LUMINANCE, srcW, srcH, false)) {
			if (!src.copyFromFont(face, glyphIndex)) return;
			try (var dst = new NativeImage(Format.LUMINANCE, dstW, dstH, false)) {
				for (var y = 0; y < dstH; y++)
					for (var x = 0; x < dstW; x++) {
						var max = 0;
						for (var dy = -radius; dy <= radius; dy++)
							for (var dx = -radius; dx <= radius; dx++) {
								// 圆形邻域：方形窗口在对角方向实际扩出 √2 倍，描边看着会长角
								if (dx * dx + dy * dy > radius * radius) continue;
								var sx = x - radius + dx;
								var sy = y - radius + dy;
								if (sx < 0 || sy < 0 || sx >= srcW || sy >= srcH) continue;
								max = Math.max(max, src.getLuminanceOrAlpha(sx, sy) & 0xFF);
							}
						dst.setPixelLuminance(x, y, (byte) max);
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
			// 位图向左上各扩了 radius，所以左上角要跟着挪，宽度也要加上两倍半径
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
				@Override
				public boolean isColored() {
					return false;
				}
				@Override
				public void upload(int xOffset, int yOffset) {
					provider.uploadDilated(xOffset, yOffset, srcW, srcH, width, height, index);
				}
			});
		}
	}
}
