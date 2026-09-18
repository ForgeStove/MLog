package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
