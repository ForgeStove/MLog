package io.github.forgestove.mlog.client.gui;
public class LogicColors {
	/** 强调文字与聚焦态。 */
	public static final int ACCENT = 0xFFFFD37F;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFA0A0A0;
	/** 悬停提示底色，不透明度 80%。 */
	public static final int TIP_BG = 0xCC000000;
	/** 悬停提示文字，较正文暗一档。 */
	public static final int TIP_TEXT = 0xFFBFBFBF;
	/** 跳转节点悬停色，也是变量表里数字类型的颜色。 */
	public static final int PLACE = 0xFF6335F8;
	public static final int AMMO = 0xFFFF8947;
	public static final int BLOCKS = 0xFFD4816B;
	public static final int UNITS = 0xFFC7B59D;
	public static final int OPERATIONS = 0xFF877BAD;
	public static final int IO = 0xFFA08A8A;
	/** 变量表首格底色与左侧竖条；名字带 {@code CELL} 以避开变量表中同名的宽度常量 {@code STUB}，同名字段会遮蔽静态导入并画出近乎透明的黑。 */
	public static final int STUB_CELL = 0xFF4D4D4D, STUB_DIM = 0xFF262626;
	/** 卡片底色：不透明度 30%，压深以便在世界背景上阅读。 */
	public static final int CARD_BG = 0x99000000;
	/** 对话框背后的压暗层，不透明度 90%；世界与父界面已绘制，故该处须半透明而非纯黑。 */
	public static final int STAGE = 0xE6000000;
	/** 主界面的压暗层，使卡片在明亮场景中亦可辨认。 */
	public static final int DIM = 0xC0101010;
	public static final int SHADOW = 0x50000000;
	/** 类别色头部栏上的深色文字与图标。 */
	public static final int HEADER_TEXT = 0xFF202020;
	/** 未聚焦时的参数下划线。 */
	public static final int BORDER = 0xFF505050;
	/** 可自由输入的参数框下划线：合法与非法两色。 */
	public static final int FIELD_LINE = 0xFF454545, FIELD_LINE_INVALID = 0xFFE55454;
	public static final int HOVER = 0x40FFFFFF;
	public static final int FLAT_OVER = 0xFF454545;
	/** 语句表分类标题及其后的分隔线；可自由输入的参数框下划线为 {@link #FIELD_LINE}，两者同值不同用途。 */
	public static final int DARKISH = 0xFF4D4D4D;
	/** 世界中描边的底色，即彩色线之下的粗灰线。 */
	public static final int GRAY = 0xFF454545;
	/** 非纯灰：{@code 0x454545} 还需乘上语句的类别色。 */
	public static int flatOver(int color) {
		return (color >> 16 & 0xFF) * 0x45 / 0xFF << 16
			| (color >> 8 & 0xFF) * 0x45 / 0xFF << 8
			| (color & 0xFF) * 0x45 / 0xFF
			| 0xFF000000;
	}
}
