package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 一 tick 内所有内容有变化的显示屏，合并成一个包。
 * <p>各格各自发一个包时，孤立的那几次发包每次都要唤醒网络线程，一次就是几十微秒。
 */
public record DisplayBatchPayload(List<Entry> entries) implements CustomPacketPayload {
	/** 一条：哪一格、这一批命令。 */
	public record Entry(BlockPos pos, List<DrawCmd> commands) {}
	public static final Type<DisplayBatchPayload> TYPE = new Type<>(getMLogRes("display_batch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		Entry::pos,
		DrawCmd.LIST_CODEC,
		Entry::commands,
		Entry::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, DisplayBatchPayload> STREAM_CODEC = StreamCodec.composite(
		ENTRY_CODEC.apply(
			ByteBufCodecs.list(4096)), DisplayBatchPayload::entries, DisplayBatchPayload::new
	);
	@Override
	public Type<DisplayBatchPayload> type() {
		return TYPE;
	}
}
