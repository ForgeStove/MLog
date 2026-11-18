package io.github.forgestove.mlog.client.gui;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 一段文本及其折行结果，折行按列宽缓存。
 * 行高计算与绘制使用同一宽度。
 */
public class LogicText {
	private final String text;
	private final Map<Integer, List<FormattedCharSequence>> lines = new HashMap<>();
	public LogicText(String text) {
		this.text = text;
	}
	public String text() {
		return text;
	}
	/** 文本按 {@code width} 折行后的结果，同一宽度只算一次。 */
	public List<FormattedCharSequence> lines(int width) {
		return lines.computeIfAbsent(width, w -> mc.font.split(LogicFont.text(text), w));
	}
}
