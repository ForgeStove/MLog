package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
/** 音乐播放请求；是否已有音乐在播由客户端判断，服务端查不到。 */
public record MusicPayload(Optional<ResourceLocation> sound, boolean interrupt) implements CustomPacketPayload {
	public static final Type<MusicPayload> TYPE = new Type<>(MLogUtil.getMLogRes("music"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MusicPayload> STREAM_CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeBoolean(payload.sound().isPresent());
			payload.sound().ifPresent(buf::writeResourceLocation);
			buf.writeBoolean(payload.interrupt());
		}, buf -> {
			var sound = buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.<ResourceLocation>empty();
			return new MusicPayload(sound, buf.readBoolean());
		}
	);
	@Override
	public Type<MusicPayload> type() {
		return TYPE;
	}
}
