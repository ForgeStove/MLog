package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 修改世界处理器的显示名。 */
public record ProcessorTagPayload(BlockPos pos, String tag) implements CustomPacketPayload {
	/** 名称的最大字符数，客户端输入与服务端解码共用。 */
	public static final int MAX_TAG_LENGTH = 32;
	public static final Type<ProcessorTagPayload> TYPE = new Type<>(MLogUtil.getMLogRes("processor_tag"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProcessorTagPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ProcessorTagPayload::pos,
		// 限长在解码阶段就生效，避免恶意包塞超长字符串
		ByteBufCodecs.stringUtf8(MAX_TAG_LENGTH), ProcessorTagPayload::tag, ProcessorTagPayload::new
	);
	@Override
	public Type<ProcessorTagPayload> type() {
		return TYPE;
	}
}
