package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 服务端推给客户端的运行中变量快照；代码、链接与 {@code print} 输出随方块实体同步，不在此重复传。 */
public record LogicVarsPayload(BlockPos pos, CompoundTag vars) implements CustomPacketPayload {
	public static final Type<LogicVarsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MLog.ID, "logic_vars"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LogicVarsPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		LogicVarsPayload::pos,
		ByteBufCodecs.TRUSTED_COMPOUND_TAG,
		LogicVarsPayload::vars,
		LogicVarsPayload::new
	);
	@Override
	public Type<LogicVarsPayload> type() {
		return TYPE;
	}
}
