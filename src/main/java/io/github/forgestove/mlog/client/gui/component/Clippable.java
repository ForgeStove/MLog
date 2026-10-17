package io.github.forgestove.mlog.client.gui.component;
/** 命中范围可被裁剪区限制的控件，用于滚动列表。 */
public interface Clippable {
	void setClip(int left, int top, int right, int bottom);
}
