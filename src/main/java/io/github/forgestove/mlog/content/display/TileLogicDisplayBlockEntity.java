package io.github.forgestove.mlog.content.display;
import io.github.forgestove.mlog.client.render.DisplayRenderer;
import io.github.forgestove.mlog.core.net.DisplayPayload;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
/** 整组共用一份画布，每格只绘制自身区域。 */
public class TileLogicDisplayBlockEntity extends BlockEntity implements MLogSenseable, Privileged, LDrawable {
	/** 一帧的积压上限，超出部分丢弃。 */
	public static final int MAX_COMMANDS = 1024;
	/** 内容未变时的补发间隔，用于覆盖客户端画布重建。 */
	private static final int RESEND_INTERVAL = 20;
	private static final long HASH_SEED = 1;
	/** 显示屏读数的属性名。 */
	private static final String DISPLAY_WIDTH = "displayWidth", DISPLAY_HEIGHT = "displayHeight";
	private static final String BUFFER_SIZE = "bufferSize", OPERATIONS = "operations";
	/** 已 flush、待取走的命令；服务端发出后清空，客户端由渲染端取走。 */
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
	/** 碰撞只会把重发延后到补发间隔，不会画出错误内容。 */
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
	private void send() {
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (pending.isEmpty()) {
			// 无新命令时仍按补发间隔重发：last 一直保留该批，供画布重建的客户端取用
			if (!last.isEmpty() && ++idle >= RESEND_INTERVAL) {
				idle = 0;
				resend(serverLevel);
			}
			return;
		}
		// 内容未变时无须每 tick 重发：比条数与指纹即可
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
	/** 各格单独发包会各自唤醒一次网络线程，故并入本 tick 的合并发送。 */
	private void resend(ServerLevel serverLevel) {
		DisplaySender.enqueue(serverLevel, getBlockPos(), last);
	}
	/** 把上一次发出的那批重发给该玩家，供客户端画布新建时立即取得内容。 */
	public void resendTo(ServerPlayer player) {
		if (!(level instanceof ServerLevel) || last.isEmpty()) return;
		PacketDistributor.sendToPlayer(player, new DisplayPayload(getBlockPos(), last));
	}
	/** 取走即清空。 */
	public List<DrawCmd> drain() {
		if (pending.isEmpty()) return List.of();
		var drained = List.copyOf(pending);
		clear();
		return drained;
	}
	/** 内容指纹随之重新起算。 */
	private void clear() {
		pending.clear();
		hash = HASH_SEED;
	}
	/** 特权显示单元（{@link Privileged#privileged()} 为真）仅接受特权处理器的命令。 */
	@Override
	public boolean drawable(LExecutor exec) {
		return exec.privileged || !privileged();
	}
	/** 超出上限的部分丢弃，本批余下的命令一并舍弃。 */
	@Override
	public void draw(List<DrawCmd> buffer) {
		operations++;
		for (var command : buffer) {
			if (pending.size() >= MAX_COMMANDS) break;
			pending.add(command);
			// 指纹与追加共用一次遍历，读的是刚构造的对象
			hash = hash * 31 + fingerprint(command);
		}
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
	@Override
	public Object senseObject(String access) {
		return level == null ? NO_SENSED : MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
	private DisplayGroup group() {
		return level == null ? new DisplayGroup(getBlockPos(), 1, 1, DisplayGroup.single(), true) : DisplayGroup.of(level, getBlockPos());
	}
}
