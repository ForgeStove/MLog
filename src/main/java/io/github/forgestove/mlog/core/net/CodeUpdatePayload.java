package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 客户端保存逻辑代码。 */
public record CodeUpdatePayload(BlockPos pos, String code) implements CustomPacketPayload {
	public static final Type<CodeUpdatePayload> TYPE = new Type<>(MLogUtil.getMLogRes("code_update"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CodeUpdatePayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		CodeUpdatePayload::pos,
		ByteBufCodecs.STRING_UTF8,
		CodeUpdatePayload::code,
		CodeUpdatePayload::new
	);
	@Override
	public Type<CodeUpdatePayload> type() {
		return TYPE;
	}
}
