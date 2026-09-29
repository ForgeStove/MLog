package io.github.forgestove.mlog.client.gui.logic;
import org.jetbrains.annotations.Nullable;
/** 取色器用的颜色换算：{@code rrggbb} / {@code rrggbbaa} 字面量与 HSV 之间。 */
final class ColorHex {
	private ColorHex() {}
	/** @return RGBA 分量各 0~255；位数不对或不是十六进制时返回 {@code null}。 */
	static int @Nullable [] parse(String text) {
		var hex = text.startsWith("%") ? text.substring(1) : text;
		if (hex.length() != 6 && hex.length() != 8) return null;
		try {
			// 按无符号解析：补满八位后最高位即红色通道的最高位，Integer.parseInt 会溢出
			var rgba = Integer.parseUnsignedInt(hex.length() == 6 ? hex + "ff" : hex, 16);
			return new int[]{rgba >>> 24 & 0xFF, rgba >>> 16 & 0xFF, rgba >>> 8 & 0xFF, rgba & 0xFF};
		} catch (NumberFormatException e) {
			return null;
		}
	}
	/** @return 八位十六进制（恒带 alpha），前面不带 {@code %}。 */
	static String format(int r, int g, int b, int a) {
		return String.format("%02x%02x%02x%02x", r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF);
	}
	/** @return 不透明时六位、否则八位；输入框用这个，写回卡片要恒八位。 */
	static String formatShort(int r, int g, int b, int a) {
		var rgba = format(r, g, b, a);
		return a >= 0xFF ? rgba.substring(0, 6) : rgba;
	}
	/** @return 色相 0~360、饱和度 0~1、明度 0~1。 */
	static float[] toHsv(int r, int g, int b) {
		var red = r / 255F;
		var green = g / 255F;
		var blue = b / 255F;
		var max = Math.max(red, Math.max(green, blue));
		var min = Math.min(red, Math.min(green, blue));
		var delta = max - min;
		var h = 0F;
		if (delta > 0F) {
			if (max == red) h = 60F * ((green - blue) / delta % 6F);
			else if (max == green) h = 60F * ((blue - red) / delta + 2F);
			else h = 60F * ((red - green) / delta + 4F);
			if (h < 0F) h += 360F;
		}
		return new float[]{h, max <= 0F ? 0F : delta / max, max};
	}
	/** @return RGB 分量各 0~255，色相取 0~360。 */
	static int[] fromHsv(float h, float s, float v) {
		var c = v * s;
		var x = c * (1F - Math.abs(h / 60F % 2F - 1F));
		var m = v - c;
		var rgb = switch ((int) (h / 60F) % 6) {
			case 0 -> new float[]{c, x, 0F};
			case 1 -> new float[]{x, c, 0F};
			case 2 -> new float[]{0F, c, x};
			case 3 -> new float[]{0F, x, c};
			case 4 -> new float[]{x, 0F, c};
			default -> new float[]{c, 0F, x};
		};
		return new int[]{
			Math.round((rgb[0] + m) * 255F),
			Math.round((rgb[1] + m) * 255F),
			Math.round((rgb[2] + m) * 255F)
		};
	}
}
