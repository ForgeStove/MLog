package io.github.forgestove.mlog.content.display;
import io.github.forgestove.mlog.core.net.DisplayPayload;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
/** 逻辑显示单元。整组共用一份画布，由起算格负责算出范围并一次画完，其余各格只是占位。 */
public class TileLogicDisplayBlockEntity extends BlockEntity implements MLogSenseable, LDrawable {
	/** 命令序列上限，超出部分丢弃；与 MDT 的 {@code maxDisplayBuffer} 同值。 */
	public static final int MAX_COMMANDS = 1024;
	/** 显示屏读数的属性名，对应 MDT 的 {@code displayWidth} 等。 */
	private static final String DISPLAY_WIDTH = "displayWidth", DISPLAY_HEIGHT = "displayHeight";
	private static final String BUFFER_SIZE = "bufferSize", OPERATIONS = "operations";
	/** 自上次清屏以来的绘制命令。 */
	private final List<DrawCmd> commands = new ArrayList<>();
	/** 尚未发往客户端的命令，仅服务端非空。 */
	private final List<DrawCmd> pending = new ArrayList<>();
	/** 命令序列的版本号，每变一次自增；渲染端据此判断画布要不要重画。 */
	private int revision;
	/** 收到的 {@code drawflush} 次数。 */
	private int operations;
	public TileLogicDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.TILE_LOGIC_DISPLAY.get(), pos, state);
	}
	public static void tick(Level ignoredLevel, BlockPos ignoredPos, BlockState ignoredState, TileLogicDisplayBlockEntity be) {
		be.send();
	}
	/** 把攒下的命令发给看得见这块显示屏的玩家。 */
	private void send() {
		if (pending.isEmpty()) return;
		if (level instanceof ServerLevel serverLevel) PacketDistributor.sendToPlayersTrackingChunk(
			serverLevel,
			new ChunkPos(getBlockPos()),
			new DisplayPayload(getBlockPos(), List.copyOf(pending), false)
		);
		pending.clear();
	}
	/**
	 * 区块送到客户端之后补一份全量。
	 * <p>命令不随区块包走，客户端是空屏起步；这条事件排在区块包之后，方块实体已经建好了。
	 */
	public static void sync(Sent event) {
		for (var be : event.getChunk().getBlockEntities().values())
			if (be instanceof TileLogicDisplayBlockEntity display) display.sendFull(event.getPlayer());
	}
	/** 把整份命令单独发给一个玩家；没有命令可发就跳过。 */
	private void sendFull(ServerPlayer player) {
		if (commands.isEmpty()) return;
		PacketDistributor.sendToPlayer(player, new DisplayPayload(getBlockPos(), List.copyOf(commands), true));
	}
	/** @return 本格收到的命令。 */
	public List<DrawCmd> getCommands() {
		return commands;
	}
	/** @return 命令序列的版本号。 */
	public int getRevision() {
		return revision;
	}
	/** 接收服务端发来的命令；{@code replace} 为真时整份替换。 */
	public void apply(List<DrawCmd> buffer, boolean replace) {
		if (replace) commands.clear();
		draw(buffer);
	}
	/**
	 * 追加一批绘制命令。
	 * <p>命令就留在收到它的那一格上，不往起算格挪：渲染端按扫描顺序把组内各格拼起来，
	 * 这样整组形状变了、起算格换了，命令也不会跟着丢。清屏之后旧的命令已经没有意义，先丢掉。
	 */
	@Override
	public void draw(List<DrawCmd> buffer) {
		operations++;
		for (var command : buffer) {
			if (command.type() == GraphicsType.clear) commands.clear();
			if (commands.size() >= MAX_COMMANDS) break;
			commands.add(command);
			// 客户端收下的命令不必再回发
			if (level == null || !level.isClientSide) pending.add(command);
		}
		revision++;
	}
	@Override
	public double sense(String access) {
		return switch (access) {
			case DISPLAY_WIDTH -> group().canvasWidth();
			case DISPLAY_HEIGHT -> group().canvasHeight();
			// 报「尚未处理的」而不是攒下的总数：MDT 的队列每帧排空，读数正常就是 0，
			// 拿它当阈值用的程序在那边才成立
			case BUFFER_SIZE -> pending.size();
			case OPERATIONS -> operations;
			default -> level == null ? 0 : MLogSenseables.generic(level, getBlockPos()).sense(access);
		};
	}
	/** @return 本格所在的那一组；无世界时按单格算，与渲染端同口径。 */
	private DisplayGroup group() {
		return level == null ? new DisplayGroup(getBlockPos(), 1, 1, 1, true) : DisplayGroup.of(level, getBlockPos());
	}
	@Override
	public Object senseObject(String access) {
		return level == null ? NO_SENSED : MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
}
