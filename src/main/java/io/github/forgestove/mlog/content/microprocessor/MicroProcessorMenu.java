package io.github.forgestove.mlog.content.microprocessor;
import io.github.forgestove.mlog.core.net.LogicVarsPayload;
import io.github.forgestove.mlog.core.register.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
/** 处理器界面的载体。没有物品槽，数据全部走网络包。 */
public class MicroProcessorMenu extends AbstractContainerMenu {
	/** 向正在查看的玩家推送状态的间隔（tick）。 */
	private static final int SYNC_INTERVAL = 5;
	private final BlockPos pos;
	private final Level level;
	private final Player owner;
	private int syncTimer;
	/** 客户端构造，坐标从打开界面时写入的附加数据里读。 */
	public MicroProcessorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
		this(id, inventory, buf.readBlockPos());
	}
	public MicroProcessorMenu(int id, Inventory inventory, BlockPos pos) {
		super(MLogMenus.MICRO_PROCESSOR.get(), id);
		this.pos = pos;
		level = inventory.player.level();
		owner = inventory.player;
	}
	public BlockPos getPos() {
		return pos;
	}
	/** 靠这个每 tick 被调用的钩子定期把变量快照推给客户端。 */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (level.isClientSide || --syncTimer > 0) return;
		syncTimer = SYNC_INTERVAL;
		if (!(owner instanceof ServerPlayer serverPlayer)) return;
		var be = getBlockEntity();
		if (be == null) return;
		PacketDistributor.sendToPlayer(serverPlayer, new LogicVarsPayload(pos, be.buildVarSnapshot()));
	}
	public @Nullable MicroProcessorBlockEntity getBlockEntity() {
		return level.getBlockEntity(pos) instanceof MicroProcessorBlockEntity be ? be : null;
	}
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
	@Override
	public boolean stillValid(Player player) {
		return stillValid(ContainerLevelAccess.create(level, pos), player, MLogBlocks.MICRO_PROCESSOR.get());
	}
}
