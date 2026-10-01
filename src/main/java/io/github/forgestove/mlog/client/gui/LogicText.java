package io.github.forgestove.mlog.client.gui;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.*;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 一段文本及其折行结果。
 * <p>折行按列宽缓存：算行高与绘制问的是同一个宽度，一帧里会被问很多次。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicText {
	private final String text;
	private final Map<Integer, List<FormattedCharSequence>> lines = new HashMap<>();
	public LogicText(String text) {
		this.text = text;
	}
	/** @return 文本本身。 */
	public String text() {
		return text;
	}
	/** @return 文本按 {@code width} 折行后的结果，同一宽度只算一次。 */
	public List<FormattedCharSequence> lines(int width) {
		return lines.computeIfAbsent(width, w -> mc.font.split(LogicFont.text(text), w));
	}
}
