package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.core.register.MLogSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 界面音效。
 * <p>按钮点击音无全局钩子，由各控件在响应处自行调用。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicSounds {
	/** 按钮点击。 */
	public static void button() {
		play(MLogSounds.UI_BUTTON.get());
	}
	/** 经 {@code forUI} 播放：音量归属游戏的「界面」通道，不随距离衰减。 */
	private static void play(SoundEvent sound) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1F, 1F));
	}
	/** 点击方块（处理器）的音效。 */
	public static void click() {
		play(MLogSounds.CLICK.get());
	}
	/** 关闭 / 返回。 */
	public static void back() {
		play(MLogSounds.UI_BACK.get());
	}
}
