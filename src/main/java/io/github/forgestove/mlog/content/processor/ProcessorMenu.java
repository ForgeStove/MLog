package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.core.net.LogicVarsPayload;
import io.github.forgestove.mlog.core.register.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
public class ProcessorMenu extends AbstractContainerMenu {
	private static final int SYNC_INTERVAL = 5;
	private final BlockPos pos;
	private final Level level;
	private final Player owner;
	private int syncTimer;
	/** 上次推送出去的变量快照，内容未变则不再推送。 */
	private @Nullable CompoundTag lastVars;
	public ProcessorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
		this(id, inventory, buf.readBlockPos());
	}
	public ProcessorMenu(int id, Inventory inventory, BlockPos pos) {
		super(MLogMenus.PROCESSOR.get(), id);
		this.pos = pos;
		level = inventory.player.level();
		owner = inventory.player;
	}
	public BlockPos getPos() {
		return pos;
	}
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (level.isClientSide || --syncTimer > 0) return;
		syncTimer = SYNC_INTERVAL;
		if (!(owner instanceof ServerPlayer serverPlayer)) return;
		var be = getBlockEntity();
		if (be == null) return;
		var vars = be.buildVarSnapshot();
		// 快照没变就不发：构造成本远低于序列化与发包
		if (vars.equals(lastVars)) return;
		lastVars = vars;
		PacketDistributor.sendToPlayer(serverPlayer, new LogicVarsPayload(pos, vars));
	}
	public @Nullable AbstractProcessorBlockEntity getBlockEntity() {
		return level.getBlockEntity(pos) instanceof AbstractProcessorBlockEntity be ? be : null;
	}
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
	@Override
	public boolean stillValid(Player player) {
		var access = ContainerLevelAccess.create(level, pos);
		return stillValid(access, player, MLogBlocks.MICRO_PROCESSOR.get()) || stillValid(access, player,
			MLogBlocks.WORLD_PROCESSOR.get());
	}
}
