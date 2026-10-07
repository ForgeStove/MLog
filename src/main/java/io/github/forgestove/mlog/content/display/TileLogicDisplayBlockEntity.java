package io.github.forgestove.mlog.content.display;
import io.github.forgestove.mlog.client.render.DisplayRenderer;
import io.github.forgestove.mlog.core.net.DisplayPayload;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
/** 逻辑显示单元。整组共用一份画布，每格各自绘制本格那一块。 */
public class TileLogicDisplayBlockEntity extends BlockEntity implements MLogSenseable, Privileged, LDrawable {
	/** 一帧的积压上限，超出部分丢弃。 */
	public static final int MAX_COMMANDS = 1024;
	/** 内容未变时的补发间隔：客户端画布跨帧保留，重复绘制只是同一份结果，故隔一段时间补发一次以覆盖画布重建。 */
	private static final int RESEND_INTERVAL = 20;
	/** 内容指纹的每批初值。 */
	private static final long HASH_SEED = 1;
	/** 显示屏读数的属性名。 */
	private static final String DISPLAY_WIDTH = "displayWidth", DISPLAY_HEIGHT = "displayHeight";
	private static final String BUFFER_SIZE = "bufferSize", OPERATIONS = "operations";
	/** 已 flush、尚未被取走的命令；服务端每 tick 发出后清空，客户端由渲染端取走。 */
	private final List<DrawCmd> pending = new ArrayList<>();
	/** 本批命令的内容指纹，随 {@link #pending} 一同重新起算。 */
	private long hash = HASH_SEED;
	/** 上一次实际发出的命令，与 {@link #pending} 条数相同、指纹相同即内容未变，无须重发。 */
	private List<DrawCmd> last = List.of();
	private long lastHash = HASH_SEED;
	/** 内容与上次相同的连续 tick 数。 */
	private int idle;
	/** 收到的 {@code drawflush} 次数。 */
	private int operations;
	public TileLogicDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.TILE_LOGIC_DISPLAY.get(), pos, state);
	}
	public static void tick(Level ignoredLevel, BlockPos ignoredPos, BlockState ignoredState, TileLogicDisplayBlockEntity be) {
		be.send();
	}
	/** 把已积压的命令交给本 tick 的合并发送；内容与上次相同则按补发间隔跳过。 */
	private void send() {
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (pending.isEmpty()) {
			// 停笔时也按补发间隔重发：这一批一直留在 last 里，画布被重建的客户端只能靠它拿到内容
			if (!last.isEmpty() && ++idle >= RESEND_INTERVAL) {
				idle = 0;
				resend(serverLevel);
			}
			return;
		}
		// 内容未变时不必每 tick 重发；比条数与指纹即可，逐条比整批要在冷内存上走一遍上一批，一批几十微秒
		if (pending.size() == last.size() && hash == lastHash && ++idle < RESEND_INTERVAL) {
			clear();
			return;
		}
		last = List.copyOf(pending);
		lastHash = hash;
		idle = 0;
		resend(serverLevel);
		clear();
	}
	/** 把上一次发出的那批排进本 tick 的合并发送；各格单独发包会各自唤醒一次网络线程。 */
	private void resend(ServerLevel serverLevel) {
		DisplaySender.enqueue(serverLevel, getBlockPos(), last);
	}
	/** 把上一次发出的那批重发给该玩家：客户端画布刚建出来时靠它即刻拿到内容，无须等补发间隔。 */
	public void resendTo(ServerPlayer player) {
		if (!(level instanceof ServerLevel) || last.isEmpty()) return;
		PacketDistributor.sendToPlayer(player, new DisplayPayload(getBlockPos(), last));
	}
	/** @return 本格积压的命令；取走即清空。 */
	public List<DrawCmd> drain() {
		if (pending.isEmpty()) return List.of();
		var drained = List.copyOf(pending);
		clear();
		return drained;
	}
	/** 清空积压，让内容指纹重新起算。 */
	private void clear() {
		pending.clear();
		hash = HASH_SEED;
	}
	/** 追加一批绘制命令；超出上限的部分丢弃。 */
	@Override
	public void draw(List<DrawCmd> buffer) {
		operations++;
		for (var command : buffer) {
			if (pending.size() >= MAX_COMMANDS) break;
			pending.add(command);
			// 顺带算指纹，与追加共用一次遍历；读的是刚构造出来的对象，还在缓存里
			hash = hash * 31 + fingerprint(command);
		}
	}
	/** @return 一条命令的内容指纹；碰撞只会把这次重发延后到补发间隔，不会画出错误内容。 */
	private static long fingerprint(DrawCmd command) {
		long h = command.type().ordinal();
		h = h * 31 + Double.doubleToRawLongBits(command.x());
		h = h * 31 + Double.doubleToRawLongBits(command.y());
		h = h * 31 + Double.doubleToRawLongBits(command.p1());
		h = h * 31 + Double.doubleToRawLongBits(command.p2());
		h = h * 31 + Double.doubleToRawLongBits(command.p3());
		h = h * 31 + Double.doubleToRawLongBits(command.p4());
		return h * 31 + command.text().hashCode();
	}
	/**
	 * @return 本条命令是否被允许。
	 * 	<p>特权显示单元（{@link Privileged#privileged()} 为真的）仅接受特权处理器的命令；
	 * 	现在的显示屏方块都不是特权方块，这个判断留给将来的世界显示屏。
	 */
	@Override
	public boolean drawable(LExecutor exec) {
		return exec.privileged || !privileged();
	}
	/** 实际被拆除时丢弃画布；区块卸载亦经此调用，故以方块实体表是否仍含自身区分。 */
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
		return level == null ? new DisplayGroup(getBlockPos(), 1, 1, DisplayGroup.single(), true) : DisplayGroup.of(level, getBlockPos());
	}
	@Override
	public Object senseObject(String access) {
		return level == null ? NO_SENSED : MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
}
