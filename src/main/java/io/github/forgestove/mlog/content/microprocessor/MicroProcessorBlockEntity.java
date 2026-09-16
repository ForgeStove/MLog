package io.github.forgestove.mlog.content.microprocessor;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.*;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 微型逻辑处理器。每 tick 执行若干条逻辑指令，可链接周围方块并用 {@code sensor} 读取。 */
public class MicroProcessorBlockEntity extends BlockEntity implements MLogSenseable, MenuProvider {
	/** 每 tick 执行的指令数，对齐 Mindustry 的 micro-processor。 */
	public static final int INSTRUCTIONS_PER_TICK = 2;
	/** 变量类型，供变量表着色与显示类型名，对齐 Mindustry 的 {@code typeName}。 */
	public static final int TYPE_NUMBER = 0, TYPE_NULL = 1, TYPE_STRING = 2, TYPE_BLOCK = 3, TYPE_ITEM = 4, TYPE_LINK = 5, TYPE_ENUM = 6;
	private static final String NBT_CODE = "code", NBT_LINKS = "links", NBT_OFFSET = "offset", NBT_NAME = "name", NBT_DISPLAY = "display";
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	private @Nullable LExecutor executor;
	private String displayText = "";
	private CompoundTag varSnapshot = new CompoundTag();
	public MicroProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MICRO_PROCESSOR.get(), pos, state);
	}
	public static void tick(Level level, BlockPos pos, BlockState state, MicroProcessorBlockEntity be) {
		GlobalVars.update(level);
		be.runLogic();
	}
	/** 执行本 tick 的指令，{@code print} 的输出有变化时同步给客户端。 */
	private void runLogic() {
		var exec = executor();
		if (exec == null || !exec.initialized()) return;
		exec.level = level;
		exec.selfPos = getBlockPos();
		for (var i = 0; i < INSTRUCTIONS_PER_TICK; i++) {
			exec.runOnce();
			if (exec.yield) {
				exec.yield = false;
				break;
			}
		}
		// 代码循环执行时，缓冲区并非每 tick 都有内容，空输出要保持上一次的显示，否则会闪
		var text = exec.drainText();
		if (text.isEmpty() || text.equals(displayText)) return;
		displayText = text;
		sync();
	}
	/** @return 执行器，首次访问时编译代码。 */
	private @Nullable LExecutor executor() {
		if (executor == null) rebuild();
		return executor;
	}
	/** 标脏存盘并推给客户端，用于刷新悬浮文字。 */
	private void sync() {
		setChanged();
		if (level == null || level.isClientSide) return;
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}
	/** 重新编译代码与链接。 */
	public void rebuild() {
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, INSTRUCTIONS_PER_TICK, links));
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
		rebuild();
		sync();
	}
	public List<LogicLink> getLinks() {
		return links;
	}
	/** 建立链接。越界、重复、超上限或目标为空则返回 {@code false}。 */
	public boolean addLink(BlockPos target) {
		if (level == null || links.size() >= LogicLink.MAX_LINKS) return false;
		if (getBlockPos().distSqr(target) > (double) LogicLink.RANGE * LogicLink.RANGE) return false;
		var offset = target.subtract(getBlockPos());
		if (links.stream().anyMatch(link -> link.offset().equals(offset))) return false;
		if (level.getBlockState(target).isAir()) return false;
		links.add(new LogicLink(offset, nextLinkName()));
		rebuild();
		sync();
		return true;
	}
	/** 取第一个没被占用的 {@code blockN} 名字，删掉中间某条链接后也不会撞名。 */
	private String nextLinkName() {
		for (var i = 1; ; i++) {
			var name = "block" + i;
			if (links.stream().noneMatch(link -> link.name().equals(name))) return name;
		}
	}
	public boolean removeLink(BlockPos target) {
		var offset = target.subtract(getBlockPos());
		if (!links.removeIf(link -> link.offset().equals(offset))) return false;
		rebuild();
		sync();
		return true;
	}
	public String getDisplayText() {
		return displayText;
	}
	/**
	 * 客户端：接住服务端推来的变量快照。
	 * <p>链接与显示文本不在这里传——它们跟着标准方块实体同步走（{@code getUpdateTag}），
	 * 这个包只需要带标准同步不包含的东西。
	 */
	public void applyVars(CompoundTag vars) {
		varSnapshot = vars;
	}
	/** @return 变量名到「显示文本 + 类型」的快照，仅在客户端有值。 */
	public CompoundTag getVarSnapshot() {
		return varSnapshot;
	}
	/** @return 变量快照。这是唯一需要单独推的数据，标准同步不带运行中的变量。 */
	public CompoundTag buildVarSnapshot() {
		var vars = new CompoundTag();
		if (executor == null) return vars;
		for (var var : executor.vars) {
			// 常量不进变量表，对应 Mindustry 的 if(s.constant) continue
			if (var.constant) continue;
			var entry = new CompoundTag();
			// 和 print 共用同一份格式化，两处显示才会一致
			entry.putString("v", LExecutor.PrintI.format(executor, var));
			entry.putInt("t", varType(var));
			vars.put(var.name, entry);
		}
		return vars;
	}
	private static int varType(LVar var) {
		if (!var.isobj) return TYPE_NUMBER;
		return switch (var.objval) {
			case null -> TYPE_NULL;
			case String ignored -> TYPE_STRING;
			case Block ignored -> TYPE_BLOCK;
			case Item ignored -> TYPE_ITEM;
			case LogicLink ignored -> TYPE_LINK;
			case Enum<?> ignored -> TYPE_ENUM;
			default -> TYPE_STRING;
		};
	}
	@Override
	public double sense(String access) {
		if (level == null) return 0;
		return MLogSenseables.generic(level, getBlockPos()).sense(access);
	}
	@Override
	public Object senseObject(String access) {
		if (level == null) return NO_SENSED;
		return MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString(NBT_CODE, code);
		// displayText 必须一起存：getUpdateTag 就是拿这份数据，不带它客户端永远收不到 print 的输出
		tag.putString(NBT_DISPLAY, displayText);
		var linkList = new ListTag();
		for (var link : links) {
			var entry = new CompoundTag();
			entry.putLong(NBT_OFFSET, link.offset().asLong());
			entry.putString(NBT_NAME, link.name());
			linkList.add(entry);
		}
		tag.put(NBT_LINKS, linkList);
	}
	@Override
	protected void loadAdditional(CompoundTag tag, Provider registries) {
		super.loadAdditional(tag, registries);
		code = tag.getString(NBT_CODE);
		displayText = tag.getString(NBT_DISPLAY);
		var linkList = tag.getList(NBT_LINKS, Tag.TAG_COMPOUND);
		links.clear();
		for (var i = 0; i < linkList.size(); i++) {
			var entry = linkList.getCompound(i);
			links.add(new LogicLink(BlockPos.of(entry.getLong(NBT_OFFSET)), entry.getString(NBT_NAME)));
		}
		// 客户端只负责渲染，不需要执行器
		if (level != null && level.isClientSide) return;
		rebuild();
	}
	@Override
	public CompoundTag getUpdateTag(Provider registries) {
		return saveWithoutMetadata(registries);
	}
	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new MicroProcessorMenu(id, inventory, worldPosition);
	}
}
