package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.*;
import net.minecraft.core.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.*;
import net.minecraft.resources.*;
/** 客户端保存逻辑代码。 */
public record CodeUpdatePayload(BlockPos pos, String code) implements CustomPacketPayload {
	public static final Type<CodeUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MLog.ID, "code_update"));
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
