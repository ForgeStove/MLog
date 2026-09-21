package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.core.register.MLogSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 界面音效。
 * <p>按钮的点击音没有全局钩子，改由各个控件在响应处自己调。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicSounds {
	/** 按钮点击。 */
	public static void button() {
		play(MLogSounds.UI_BUTTON.get());
	}
	/** 点击方块（处理器）的那一声。 */
	public static void click() {
		play(MLogSounds.CLICK.get());
	}
	/** 走 {@code forUI}：音量归游戏的「界面」通道，也不随距离衰减。 */
	private static void play(SoundEvent sound) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1F, 1F));
	}
	/** 关闭 / 返回。 */
	public static void back() {
		play(MLogSounds.UI_BACK.get());
	}
}
