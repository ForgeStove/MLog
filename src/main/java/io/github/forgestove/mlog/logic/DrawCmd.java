package io.github.forgestove.mlog.logic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
/**
 * 一条绘制命令。
 * <p>{@code print} 的文本随命令一起带走，字形尺寸只有客户端掌握，展开成逐字符的命令只能在渲染时做。
 * <p>传输用紧凑二进制：1 字节类型 + 6 个单精度分量 + 变长文本，单条约 25 字节。坐标降为单精度：
 * 画布仅几百像素，精度足够且体积减半。
 */
public record DrawCmd(GraphicsType type, double x, double y, double p1, double p2, double p3, double p4, String text) {
	/** 一批命令的条数上限，防止坏包按长度撑爆内存。 */
	private static final int MAX_BATCH = 1024;
	/** 分量的个数，依次为 {@code x, y, p1..p4}。 */
	private static final int FIELDS = 6;
	/** 一批命令的编解码，长度在前。 */
	public static final StreamCodec<FriendlyByteBuf, List<DrawCmd>> LIST_CODEC = StreamCodec.of(DrawCmd::writeAll, DrawCmd::readAll);
	public DrawCmd(GraphicsType type, double x, double y, double p1, double p2, double p3, double p4) {
		this(type, x, y, p1, p2, p3, p4, "");
	}
	/** 写一条命令。 */
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		// 只写本类型用得到的字段，其余分量不写：绘图指令大多只用前几个
		switch (type) {
			case reset -> {}
			case rotate -> buf.writeFloat((float) p1);
			case stroke -> write(buf, 1);
			case translate, scale -> write(buf, 2);
			case clear, print -> write(buf, 3);
			case color, line, rect, lineRect -> write(buf, 4);
			case poly, linePoly -> write(buf, 5);
			// col 仅在指令层存在，显示端不会收到；与写满六个的那组同列，以免漏掉分支
			case triangle, image, col -> write(buf, 6);
		}
		buf.writeUtf(text);
	}
	/** 自 {@code x} 起连续写 {@code count} 个分量。 */
	private void write(FriendlyByteBuf buf, int count) {
		for (var i = 0; i < count; i++) buf.writeFloat((float) component(i));
	}
	/** @return 下标 0~5 对应的分量。 */
	private double component(int index) {
		return switch (index) {
			case 0 -> x;
			case 1 -> y;
			case 2 -> p1;
			case 3 -> p2;
			case 4 -> p3;
			default -> p4;
		};
	}
	/**
	 * @return 读回的命令；类型不认识时返回 {@code null}
	 * 	<p>字段数由类型决定，故类型不认识时无法定位后续命令的起点，调用方须中止这一批
	 */
	public static @Nullable DrawCmd read(FriendlyByteBuf buf) {
		var ordinal = buf.readByte();
		var types = GraphicsType.values();
		if (ordinal < 0 || ordinal >= types.length) return null;
		var type = types[ordinal];
		var values = new float[FIELDS];
		switch (type) {
			case reset -> {}
			case rotate -> values[2] = buf.readFloat();
			case stroke -> read(buf, values, 1);
			case translate, scale -> read(buf, values, 2);
			case clear, print -> read(buf, values, 3);
			case color, line, rect, lineRect -> read(buf, values, 4);
			case poly, linePoly -> read(buf, values, 5);
			case triangle, image, col -> read(buf, values, 6);
		}
		return new DrawCmd(type, values[0], values[1], values[2], values[3], values[4], values[5], buf.readUtf());
	}
	/** 自 {@code x} 起连续读 {@code count} 个分量。 */
	private static void read(FriendlyByteBuf buf, float[] values, int count) {
		for (var i = 0; i < count; i++) values[i] = buf.readFloat();
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
			if (command == null) return commands;
			commands.add(command);
		}
		return commands;
	}
}
