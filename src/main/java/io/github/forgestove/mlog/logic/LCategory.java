package io.github.forgestove.mlog.logic;
/** 语句分类，决定卡片配色与语句表里的分组。 */
public enum LCategory {
	unknown(0xFF4D4D4D),
	io(0xFFA08A8A),
	block(0xFFD4816B),
	operation(0xFF877BAD),
	control(0xFF6BB2B2),
	unit(0xFFC7B59D),
	world(0xFF6B84D4),
	;
	public final int color;
	LCategory(int color) {
		this.color = color;
	}
	/** 界面上挂在分类名上做悬停提示。 */
	public String descriptionKey() {
		return nameKey() + ".tip";
	}
	public String nameKey() {
		return "mlog.category." + name();
	}
}
