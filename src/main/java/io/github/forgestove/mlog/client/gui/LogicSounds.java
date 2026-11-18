package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.core.register.MLogSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class LogicSounds {
	public static void button() {
		play(MLogSounds.UI_BUTTON.get());
	}
	/** 经 {@code forUI} 播放，音量归属游戏的「界面」通道且不随距离衰减。 */
	private static void play(SoundEvent sound) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1F, 1F));
	}
	public static void click() {
		play(MLogSounds.CLICK.get());
	}
	public static void back() {
		play(MLogSounds.UI_BACK.get());
	}
}
