package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.*;
import net.minecraft.resources.*;
/**
 * 服务端把运行中的变量快照推给正在查看界面的客户端。
 * <p>代码、链接与 {@code print} 的输出都跟着标准方块实体同步走，不重复在这里传。
 */
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
