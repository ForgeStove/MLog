package io.github.forgestove.mlog.client.audio;
import io.github.forgestove.mlog.core.net.MusicPayload;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.HashSet;
import java.util.Set;
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
		if (payload.interrupt()) manager.stop(null, SoundSource.MUSIC);
		// 不中断时已有音乐就什么都不做，对应 MDT 的 playOnce
		else if (!PLAYING.isEmpty()) return;
		var id = payload.sound().orElse(null);
		if (id == null) return;
		var event = BuiltInRegistries.SOUND_EVENT.get(id);
		if (event == null) return;
		manager.play(SimpleSoundInstance.forMusic(event));
	}
}
