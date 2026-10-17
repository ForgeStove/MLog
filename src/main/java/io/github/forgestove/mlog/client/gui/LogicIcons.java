package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** 图标以字体渲染，位图仅用于生成字体；码点由字体生成器配置分配，枚举名与图集名可能不同（{@code search} = {@code zoom}、{@code pencil} = {@code pencil_}）。 */
public enum LogicIcons {
	WARNING(0x26A0),
	OK(0xE800),
	BOOK_OPEN(0xE801),
	BACK(0xE802),
	RIGHT(0xE803),
	UP(0xE804),
	DOWN(0xE805),
	PAUSE(0xE806),
	HOME(0xE807),
	IMAGE(0xE808),
	STAR(0xE809),
	MUSIC(0xE80A),
	RESIZE(0xE80B),
	VOLUME_OFF(0xE80C),
	DISCORD(0xE80D),
	/** 语句表分类标题前的小图标。 */
	LOGIC(0xE80E),
	WRENCH(0xE80F),
	POWER(0xE810),
	LIST(0xE811),
	FLIP_X(0xE812),
	ADD(0xE813),
	DISTRIBUTION(0xE814),
	CANCEL(0xE815),
	/** 主界面底部的「编辑」按钮。 */
	EDIT(0xE816),
	HAMMER(0xE817),
	MOVE(0xE818),
	CHART_BAR(0xE819),
	PLANE_OUTLINE(0xE81A),
	SAVE(0xE81B),
	LINK(0xE81C),
	FOLDER(0xE81D),
	/** 获取数据弹窗顶部的分类图标。 */
	BOX(0xE81E),
	VOLUME_DOWN(0xE81F),
	VOLUME_UP(0xE820),
	TAG(0xE821),
	STEAM(0xE822),
	ROTATE(0xE823),
	DOWN_OPEN(0xE824),
	LEFT_OPEN(0xE825),
	UP_OPEN(0xE826),
	MAP(0xE827),
	TAGS(0xE828),
	PLAY(0xE829),
	ITCHIO(0xE82A),
	LINE(0xE82B),
	ADMIN(0xE82C),
	SPRAY_1(0xE82D),
	FOLDER_OPEN(0xE82E),
	INBOX(0xE82F),
	CRAFTING(0xE830),
	CLOUD(0xE831),
	RAIN(0xE832),
	PLANET(0xE833),
	ARROW_NOTE(0xE834),
	UNDO(0xE835),
	REDO(0xE836),
	INFO_CIRCLE(0xE837),
	CLOUD_SUN(0xE838),
	RIGHT_OPEN_OUT(0xE839),
	RIGHT_OPEN(0xE83A),
	WAVES(0xE83B),
	DRIZZLE(0xE83C),
	GOOGLEPLAY(0xE83D),
	FILTERS(0xE83E),
	LAYERS(0xE83F),
	CLOUD_FLASH(0xE840),
	CLOUD_WIND(0xE841),
	FLIP_Y(0xE842),
	CHART_ALT(0xE843),
	DIAGONAL(0xE844),
	ANDROID(0xE845),
	FILL(0xE84C),
	DEFENSE(0xE84D),
	PASTE(0xE852),
	EFFECT(0xE853),
	BOOK(0xE85B),
	LIQUID(0xE85C),
	HOST(0xE85D),
	PRODUCTION(0xE85E),
	EXIT(0xE85F),
	MODE_PVP(0xE861),
	TERRAIN(0xE864),
	MODE_ATTACK(0xE865),
	REFRESH_1(0xE867),
	NONE(0xE868),
	PENCIL(0xE869),
	REFRESH(0xE86A),
	MODE_SURVIVAL(0xE86B),
	COMMAND_RALLY(0xE86C),
	UNITS(0xE86D),
	COMMAND_ATTACK(0xE86E),
	TRASH(0xE86F),
	CHAT(0xE870),
	TURRET(0xE871),
	PLAYERS(0xE872),
	EDITOR(0xE873),
	COPY(0xE874),
	TREE(0xE875),
	LOCK_OPEN(0xE876),
	PICK(0xE877),
	EXPORT(0xE878),
	DOWNLOAD(0xE879),
	UPLOAD(0xE87B),
	SETTINGS(0xE87C),
	SPRAY(0xE87D),
	SEARCH(0xE88A),
	POWER_OLD(0xE88B),
	MENU(0xE88C),
	LOCK(0xE88D),
	EYE(0xE88E),
	EYE_OFF(0xE88F),
	GRID(0xF029),
	FILTER(0xF0B0),
	SITEMAP(0xF0E8),
	DOWNLOAD_CLOUD(0xF0ED),
	UPLOAD_CLOUD(0xF0EE),
	FILE_TEXT(0xF0F6),
	FOLDER_EMPTY(0xF114),
	FOLDER_OPEN_EMPTY(0xF115),
	TERMINAL(0xF120),
	INFO(0xF129),
	ERASER(0xF12D),
	FILE(0xF15B),
	FILE_TEXT_FILL(0xF15C),
	TRELLO(0xF181),
	FILE_IMAGE(0xF1C5),
	FILE_ARCHIVE(0xF1C6),
	FILE_AUDIO(0xF1C7),
	FILE_VIDEO(0xF1C8),
	FILE_CODE(0xF1C9),
	BATTERY_4(0xF240),
	BATTERY_3(0xF241),
	BATTERY_2(0xF242),
	BATTERY_1(0xF243),
	BATTERY_0(0xF244),
	REDDIT_ALIEN(0xF281),
	GITHUB_SQUARE(0xF300),
	GITHUB(0xF308),
	;
	/** 字形中心相对基线的高度：实测范围 -9.34 ~ +1.66，中点 -3.84，垂直居中时需将其加回。 */
	private static final float GLYPH_CENTER = -3.84F;
	/** 居中后的额外下移量：字形几何中点与视觉重心不重合，实测偏高。 */
	private static final float GLYPH_NUDGE = 0.5F;
	private final Component text;
	LogicIcons(char code) {
		// 枚举构造器不可引用尚未初始化的静态字段，故需即时取值
		text = Component.literal(String.valueOf(code)).withStyle(style -> style.withFont(fontId()));
	}
	LogicIcons(int i) {
		this((char) i);
	}
	public static ResourceLocation fontId() {
		return getMLogRes("icons");
	}
	/** {@link #render} 垂直居中所需的 {@code y}；该 {@code y} 为文字基线而非顶部，按行高计算会偏出容器；供世界渲染等自行绘制处使用。 */
	public static int centerY(int containerY, int containerHeight) {
		return Math.round(containerY + containerHeight / 2F + GLYPH_CENTER + GLYPH_NUDGE);
	}
	public Component component() {
		return text;
	}
	/** 按比例绘制图标；分类标题等行高较小的位置需单独缩放。缩放锚点取左边缘加图标垂直中心，以原点缩放会使字形相对原点的偏移一同缩放而整体向左上偏移。 */
	public void renderScaled(GuiGraphics gui, int x, int y, float scale, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(x * (1F - scale), (y + GLYPH_CENTER) * (1F - scale), 0F);
		pose.scale(scale, scale, 1F);
		render(gui, x, y, color);
		pose.popPose();
	}
	public void render(GuiGraphics gui, int x, int y, int color) {
		// 不启用阴影：图标线条细，投影会使轮廓模糊
		gui.drawString(mc.font, text, x, y, color, false);
	}
	public int width(float scale) {
		return Math.round(width() * scale);
	}
	public int width() {
		return mc.font.width(text);
	}
}
