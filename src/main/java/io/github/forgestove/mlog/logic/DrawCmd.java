package io.github.forgestove.mlog.logic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
/**
 * 一条绘制命令。
 * <p>{@code print} 的文本随命令一起带走，字形尺寸只有客户端掌握，展开成逐字符的命令只能在渲染时做。
 * <p>传输用紧凑二进制：1 字节类型 + 6 个单精度分量 + 变长文本，一条约 25 字节。坐标降为单精度：
 * 画布只有几百像素，够用且省一半体积。
 */
public record DrawCmd(GraphicsType type, double x, double y, double p1, double p2, double p3, double p4, String text) {
	/** 一批命令的条数上限，防止坏包按长度撑爆内存。 */
	private static final int MAX_BATCH = 1024;
	/** 一批命令的编解码，长度在前。 */
	public static final StreamCodec<FriendlyByteBuf, List<DrawCmd>> LIST_CODEC = StreamCodec.of(DrawCmd::writeAll, DrawCmd::readAll);
	public DrawCmd(GraphicsType type, double x, double y, double p1, double p2, double p3, double p4) {
		this(type, x, y, p1, p2, p3, p4, "");
	}
	/** 写一条命令。 */
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		buf.writeFloat((float) x);
		buf.writeFloat((float) y);
		buf.writeFloat((float) p1);
		buf.writeFloat((float) p2);
		buf.writeFloat((float) p3);
		buf.writeFloat((float) p4);
		buf.writeUtf(text);
	}
	/** @return 读回的命令；类型名不认识时返回 {@code null}，字节数照常跳过。 */
	public static @Nullable DrawCmd read(FriendlyByteBuf buf) {
		var ordinal = buf.readByte();
		var x = buf.readFloat();
		var y = buf.readFloat();
		var p1 = buf.readFloat();
		var p2 = buf.readFloat();
		var p3 = buf.readFloat();
		var p4 = buf.readFloat();
		var text = buf.readUtf();
		var types = GraphicsType.values();
		return ordinal < 0 || ordinal >= types.length ? null : new DrawCmd(types[ordinal], x, y, p1, p2, p3, p4, text);
	}
	private static void writeAll(FriendlyByteBuf buf, List<DrawCmd> commands) {
		buf.writeVarInt(commands.size());
		for (var command : commands) command.write(buf);
	}
	private static List<DrawCmd> readAll(FriendlyByteBuf buf) {
		var size = Math.min(buf.readVarInt(), MAX_BATCH);
		var commands = new ArrayList<DrawCmd>(size);
		for (var i = 0; i < size; i++) {
			var command = read(buf);
			if (command != null) commands.add(command);
		}
		return commands;
	}
}
