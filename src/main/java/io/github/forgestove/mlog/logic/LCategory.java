package io.github.forgestove.mlog.logic;
/** 语句分类，决定卡片配色与语句表里的分组。颜色取自 Mindustry 的 {@code Pal}。 */
public enum LCategory {
	unknown(0xFF4D4D4D),
	io(0xFFA08A8A),
	block(0xFFD4816B),
	operation(0xFF877BAD),
	control(0xFF6BB2B2),
	;
	public final int color;
	LCategory(int color) {
		this.color = color;
	}
	/** @return 分类名的 lang key。 */
	public String nameKey() {
		return "lcategory.mlog." + name();
	}
	/** @return 分类说明的 lang key，界面上挂在分类名上做悬停提示。 */
	public String descriptionKey() {
		return nameKey() + ".description";
	}
}
