package io.github.forgestove.mlog.content.display;
import io.github.forgestove.mlog.client.render.DisplayRenderer;
import io.github.forgestove.mlog.core.net.DisplayPayload;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
/** 逻辑显示单元。整组共用一份画布，每格各自绘制本格那一块。 */
public class TileLogicDisplayBlockEntity extends BlockEntity implements MLogSenseable, LDrawable {
	/** 一帧的积压上限，超出部分丢弃。 */
	public static final int MAX_COMMANDS = 1024;
	/** 显示屏读数的属性名。 */
	private static final String DISPLAY_WIDTH = "displayWidth", DISPLAY_HEIGHT = "displayHeight";
	private static final String BUFFER_SIZE = "bufferSize", OPERATIONS = "operations";
	/** 已 flush、尚未被取走的命令；服务端每 tick 发出后清空，客户端由渲染端取走。 */
	private final List<DrawCmd> pending = new ArrayList<>();
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
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (pending.isEmpty()) return;
		PacketDistributor.sendToPlayersTrackingChunk(
			serverLevel,
			new ChunkPos(getBlockPos()),
			new DisplayPayload(getBlockPos(), List.copyOf(pending))
		);
		pending.clear();
	}
	/** @return 本格积压的命令；取走即清空。 */
	public List<DrawCmd> drain() {
		if (pending.isEmpty()) return List.of();
		var drained = List.copyOf(pending);
		pending.clear();
		return drained;
	}
	/** 追加一批绘制命令；超出上限的部分丢弃。 */
	@Override
	public void draw(List<DrawCmd> buffer) {
		operations++;
		for (var command : buffer) {
			if (pending.size() >= MAX_COMMANDS) break;
			pending.add(command);
		}
	}
	/** @return 本条命令是否被允许；特权显示单元仅接受特权处理器的命令。 */
	@Override
	public boolean drawable(LExecutor exec) {
		return exec.privileged || !privileged();
	}
	/** @return 本方块是否为特权方块，判据同 {@code MicroProcessorBlockEntity#privileged()}。 */
	private boolean privileged() {
		return getBlockState().getBlock() instanceof GameMasterBlock;
	}
	/** 真被拆除时丢弃画布；区块卸载亦经此调用，故以方块实体表是否仍含自身区分。 */
	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide && level.getBlockEntity(getBlockPos()) != this)
			DisplayRenderer.invalidate(level, getBlockPos());
	}
	@Override
	public double sense(String access) {
		return switch (access) {
			case DISPLAY_WIDTH -> group().canvasWidth();
			case DISPLAY_HEIGHT -> group().canvasHeight();
				// 报尚未取走的条数；队列每帧排空，读数正常为 0
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
