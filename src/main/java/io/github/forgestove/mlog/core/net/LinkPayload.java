package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.*;
import net.minecraft.core.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.*;
import net.minecraft.resources.*;
/** 客户端增删一条方块链接。{@code remove} 为 {@code true} 时是断开链接。 */
public record LinkPayload(BlockPos pos, BlockPos target, boolean remove) implements CustomPacketPayload {
	public static final Type<LinkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MLog.ID, "link"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LinkPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		LinkPayload::pos,
		BlockPos.STREAM_CODEC,
		LinkPayload::target,
		ByteBufCodecs.BOOL,
		LinkPayload::remove,
		LinkPayload::new
	);
	@Override
	public Type<LinkPayload> type() {
		return TYPE;
	}
}
