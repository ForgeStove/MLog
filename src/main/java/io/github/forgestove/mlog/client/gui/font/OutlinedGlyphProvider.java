package io.github.forgestove.mlog.client.gui.font;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.font.GlyphInfo.SpaceGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import io.github.forgestove.mlog.client.gui.LogicFont;
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
 * 带描边的字形。
 * <p>MC 自带的 {@code ttf} provider 没有 {@code borderWidth} 参数，字形是"瘦"的；
 * 这里在字形位图上传前做一次形态学膨胀，把轮廓向外扩 {@link #radius} 像素。
 * <p>这里把环烙进字形：
 * 上传的是<b>彩色</b>位图（{@link #bake} 里环是深灰、芯是白的），MC 的彩色字形着色器会把整个
 * 字形再乘一遍正文色，于是环是「正文色 × 深灰」、芯是正文色，
 * 一个字形一次画完，没有两层谁盖谁的问题。
 */
@OnlyIn(Dist.CLIENT)
public class OutlinedGlyphProvider implements GlyphProvider {
	/** MC 的 {@code TrueTypeGlyphProvider} 用的加载标志，照搬以保证度量一致。 */
	private static final int LOAD_FLAGS = 4194312;
	/** 描边宽度，位图上的像素数（已把超采样算进去）。 */
	private final int radius;
	/**
	 * 码点偏移：配了它，这个 provider 只服务「码点 ≥ 偏移」的那一段，其余交给同一字体里排在它前面的 provider。
	 * <p>于是同一个字体里能并放两套字形——原样的和带描边的，靠码点区分。带描边那套一次就画完环和芯。
	 */
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
		// 偏移之外的一律不认：这一段是留给同一个字体里排在前面的 provider 的
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
				set.add((int) c + offset);
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
	 * 把字形位图向外膨胀 {@link #radius} 像素、并把环一起烙进去后上传。
	 * <p>膨胀用的是形态学里的"取邻域最大值"，对灰度抗锯齿位图正好合适——边缘那圈半透明像素
	 * 会被向外铺开成实心，不会像多次偏移绘制那样叠出虚边。
	 * <p>环与芯合成在一张 RGBA 位图里：环深灰、芯白，画的时候整个字形再被正文色乘一遍，
	 * 就得到「正文色 × 深灰」的环和正文色的芯，一次画完。
	 */
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
								// 圆形邻域：方形窗口在对角方向实际扩出 √2 倍，描边看着会长角
								if (dx * dx + dy * dy > radius * radius) continue;
								var sx = x - radius + dx;
								var sy = y - radius + dy;
								if (sx < 0 || sy < 0 || sx >= srcW || sy >= srcH) continue;
								ring = Math.max(ring, src.getLuminanceOrAlpha(sx, sy) & 0xFF);
							}
						// 邻域中心那一格正是原字形在这一格上的覆盖：有它就是芯，没有就是环
						var cx = x - radius;
						var cy = y - radius;
						var core = cx < 0 || cy < 0 || cx >= srcW || cy >= srcH ? 0 : src.getLuminanceOrAlpha(cx, cy) & 0xFF;
						dst.setPixelRGBA(x, y, bake(ring, core));
					}
				dst.upload(0, xOffset, yOffset, 0, 0, dstW, dstH, false, true);
			}
		}
	}
	/**
	 * 把一格上的环与芯合成到一起，颜色按覆盖度加权——和先铺环、再把芯压上去的结果完全一样，
	 * 只是烙在了同一格上，画的时候一次乘色就够了。
	 *
	 * @param ring 膨胀后的覆盖（环 + 芯）
	 * @param core 原字形在同一格上的覆盖，恒不大于 {@code ring}
	 * @return RGBA 位图要的 ABGR 像素；灰阶，R=G=B
	 */
	private static int bake(int ring, int core) {
		if (ring == 0) return 0;
		// 环占 ring - core、芯占 core；再除以总覆盖，还原成非预乘的色值
		var gray = (LogicFont.OUTLINE_FACTOR * (ring - core) + 0xFF * core) / ring;
		return gray << 16 | gray << 8 | gray | ring << 24;
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
					// 彩色位图：环和芯都烙在字形里，着色器会把整张图乘上正文色
					return true;
				}
				@Override
				public void upload(int xOffset, int yOffset) {
					provider.uploadDilated(xOffset, yOffset, srcW, srcH, width, height, index);
				}
			});
		}
	}
}
