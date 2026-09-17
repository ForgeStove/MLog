package io.github.forgestove.mlog.client.gui;
import net.neoforged.api.distmarker.*;
/** 界面配色，取自 Mindustry 的 {@code Pal}。 */
@OnlyIn(Dist.CLIENT)
public final class LogicColors {
	/** Pal.accent，强调文字与聚焦态。 */
	public static final int ACCENT = 0xFFFFD37F;
	/** 普通说明文字。 */
	public static final int TEXT = 0xFFFFFFFF;
	/** 次要文字。 */
	public static final int TEXT_DIM = 0xFFA0A0A0;
	/** Pal.place，跳转节点悬停色，也是变量表里数字类型的颜色。 */
	public static final int PLACE = 0xFF6335F8;
	/** Pal.ammo，字符串类型。 */
	public static final int AMMO = 0xFFFF8947;
	/** Pal.logicBlocks，方块类型。 */
	public static final int BLOCKS = 0xFFD4816B;
	/** Pal.logicOperations，物品类型。 */
	public static final int OPERATIONS = 0xFF877BAD;
	/** Pal.logicIo，枚举类型。 */
	public static final int IO = 0xFFA08A8A;
	/**
	 * Pal.gray 与它的压暗版，变量表第一格的底与左侧竖条。
	 * <p>名字带 {@code CELL} 是避开变量表里同名的宽度常量 {@code STUB}——同名字段会遮蔽静态导入，
	 * 那片底就画成了宽度值 3，变成一滩几乎透明的黑。
	 */
	public static final int STUB_CELL = 0xFF4D4D4D, STUB_DIM = 0xFF262626;
	/** 卡片底色：Mindustry 的 0.3 黑，压深一点便于在世界背景上阅读。 */
	public static final int CARD_BG = 0x99000000;
	/**
	 * 对话框背后的压暗层，对齐 Mindustry 的 {@code DialogStyle.stageBackground}（九成黑）。
	 * <p>对话框是独立界面，世界和父界面都已经画过了，所以这里要半透明而不是死黑。
	 */
	public static final int STAGE = 0xE6000000;
	/** 主界面的压暗层，让卡片在明亮场景里也看得清。 */
	public static final int DIM = 0xC0101010;
	/** 滚动条槽与分隔线。 */
	public static final int PANEL_BORDER = 0xFF000000;
	/** 卡片投影。 */
	public static final int SHADOW = 0x50000000;
	/** 类别色头部栏上的深色文字与图标。 */
	public static final int HEADER_TEXT = 0xFF202020;
	/** 参数下划线，未聚焦时。 */
	public static final int BORDER = 0xFF505050;
	/** 列表悬停高亮。 */
	public static final int HOVER = 0x40FFFFFF;
	/** 按钮悬停底色，对应 Mindustry 的 {@code Styles.flatOver}。 */
	public static final int FLAT_OVER = 0xFF454545;
	/**
	 * @return 按钮悬停底色，对齐 Mindustry 的 {@code Styles.flatOver}。
	 * 	<p>arc 画背景时是「drawable 自身的 tint 乘上控件的 color」（{@code TextureRegionDrawable.draw}），
	 * 	所以那边看到的不是纯灰，而是 0x454545 乘上语句的类别色。
	 */
	public static int flatOver(int color) {
		return (color >> 16 & 0xFF) * 0x45 / 0xFF << 16
			| (color >> 8 & 0xFF) * 0x45 / 0xFF << 8
			| (color & 0xFF) * 0x45 / 0xFF
			| 0xFF000000;
	}
	/** Pal.darkishGray，语句表的分类标题与它后面的分隔线。 */
	public static final int DARKISH = 0xFF4D4D4D;
}
