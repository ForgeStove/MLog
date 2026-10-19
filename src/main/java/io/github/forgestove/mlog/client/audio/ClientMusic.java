package io.github.forgestove.mlog.client.audio;
import io.github.forgestove.mlog.core.net.MusicPayload;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 音乐通道在客户端播放：原版 MusicManager 的曲目也经过这里，可据此判断是否已有音乐。 */
public class ClientMusic {
	/** 仍在播放的音乐实例，含原版与其他模组的曲目。 */
	private static final Set<SoundInstance> PLAYING = new HashSet<>();
	public static void onPlaySound(PlaySoundEvent event) {
		var sound = event.getSound();
		if (sound != null && sound.getSource() == SoundSource.MUSIC) PLAYING.add(sound);
	}
	public static void onClientTick(Post ignoredEvent) {
		if (PLAYING.isEmpty()) return;
		var manager = mc.getSoundManager();
		PLAYING.removeIf(sound -> !manager.isActive(sound));
	}
	public static void apply(MusicPayload payload) {
		var manager = mc.getSoundManager();
		var id = payload.sound().orElse(null);
		// 同一首仍在播放：中断请求也直接忽略。脚本每 tick 调用时不会反复停掉重建音频流
		var playing = id != null && PLAYING.stream().anyMatch(sound -> id.equals(sound.getLocation()));
		if (payload.interrupt()) {
			if (playing) return;
			manager.stop(null, SoundSource.MUSIC);
			PLAYING.clear();
		}
		// 不中断时已有音乐（含原版曲目）就什么都不做
		else if (!PLAYING.isEmpty()) return;
		if (id == null) return;
		var event = BuiltInRegistries.SOUND_EVENT.get(id);
		if (event == null) return;
		manager.play(SimpleSoundInstance.forMusic(event));
	}
}
