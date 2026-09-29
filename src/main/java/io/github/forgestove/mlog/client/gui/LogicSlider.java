package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.*;

import java.util.function.Consumer;
import java.util.function.Supplier;
/**
 * 界面滑条：取值范围 {@code 0} 至 {@code max}，轨道渐变由调用方绘制，自身绘制轨道框、把手三态并处理拖动。
 * <p>取值经两个访问器直接读写调用方，自身不留值，外部改值后把手随之移动。
 */
@OnlyIn(Dist.CLIENT)
public class LogicSlider extends AbstractWidget {
	/** 轨道渐变，绘制在轨道框与把手之下。 */
	public interface Track {
		void render(GuiGraphics gui, int x, int y, int width, int height);
	}
	/** 把手尺寸，由 29×42 的纹理折成；宽度同时决定取值到像素的换算范围。 */
	private static final int KNOB_W = 12, KNOB_H = 17;
	/** 轨道纹理的折算比例，纹理原始像素乘上它即界面像素。 */
	private static final float TRACK_SCALE = 0.4F;
	/** 取值上限。 */
	private final float max;
	private final Supplier<Float> get;
	private final Consumer<Float> set;
	private final Track track;
	/** 是否处于按下拖动状态，拖动期间鼠标移出轨道仍保持手型与按下纹理。 */
	private boolean dragging;
	public LogicSlider(int x, int y, int width, int height, float max, Supplier<Float> get, Consumer<Float> set, Track track) {
		super(x, y, width, height, Component.empty());
		this.max = max;
		this.get = get;
		this.set = set;
		this.track = track;
	}
	/** 按下音沿用按钮音。 */
	@Override
	public void playDownSound(SoundManager handler) {
		LogicSounds.button();
	}
	/** 按下即取按下处的取值，与拖动一致。 */
	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		dragging = true;
		moveTo(mouseX);
	}
	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		moveTo(mouseX);
	}
	@Override
	public void onRelease(double mouseX, double mouseY) {
		dragging = false;
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (dragging || isHovered()) LogicCursor.setHand();
		var x = getX();
		var y = getY();
		var width = getWidth();
		var height = getHeight();
		track.render(gui, x, y, width, height);
		LogicGuiTextures.SLIDER_BACK.render(gui, x, y, width, height, TRACK_SCALE);
		// 把手在轨道内滑动：两端各留半个把手，不出轨道范围
		var knobX = x + KNOB_W / 2 + Math.round(get.get() / max * (width - KNOB_W));
		knob().render(gui, knobX - KNOB_W / 2, y, KNOB_W, KNOB_H);
	}
	/** @return 把手当前所用的纹理：拖动取按下态，悬停取悬停态。 */
	private LogicGuiTextures knob() {
		if (dragging) return LogicGuiTextures.SLIDER_KNOB_DOWN;
		return isHovered() ? LogicGuiTextures.SLIDER_KNOB_OVER : LogicGuiTextures.SLIDER_KNOB;
	}
	/** 把鼠标横坐标折算成取值写回调用方。 */
	private void moveTo(double mouseX) {
		var ratio = Mth.clamp((float) ((mouseX - getX() - KNOB_W / 2F) / (getWidth() - KNOB_W)), 0F, 1F);
		set.accept(ratio * max);
	}
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
