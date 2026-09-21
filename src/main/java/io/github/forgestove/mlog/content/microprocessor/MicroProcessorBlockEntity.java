package io.github.forgestove.mlog.content.microprocessor;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.core.rule.MLogRules;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LExecutor.PrintI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 微型逻辑处理器。每 tick 执行若干条逻辑指令，可链接周围方块并通过 {@code sensor} 读取。 */
public class MicroProcessorBlockEntity extends BlockEntity implements MLogSenseable, MenuProvider {
	public static final int INSTRUCTIONS_PER_TICK = 6;
	/**
	 * 世界处理器每 tick 执行的指令数，为普通处理器的 4 倍。
	 * <p>对应 Mindustry 中世界处理器与普通处理器的 8:2 比例。
	 */
	public static final int WORLD_INSTRUCTIONS_PER_TICK = INSTRUCTIONS_PER_TICK * 4;
	/** 变量类型 ID，用于变量表着色和类型名显示，对应 Mindustry 的 {@code typeName}。 */
	public static final int TYPE_NUMBER = 0, TYPE_NULL = 1, TYPE_STRING = 2, TYPE_BLOCK = 3, TYPE_ITEM = 4, TYPE_LINK = 5, TYPE_ENUM = 6,
		TYPE_FLUID = 7, TYPE_UNIT = 8, TYPE_BUILDING = 9, TYPE_OBJECT = 10;
	private static final String NBT_CODE = "code", NBT_LINKS = "links", NBT_OFFSET = "offset", NBT_NAME = "name";
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	/**
	 * 最近一次 {@code printflush} 发送给显示链接器的文本，由 Create 兼容层写入。
	 * <p>该字段不存档、不同步；重进世界后由下一次 {@code printflush} 重建。
	 */
	private String displayText = "";
	private @Nullable LExecutor executor;
	private CompoundTag varSnapshot = new CompoundTag();
	public MicroProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MICRO_PROCESSOR.get(), pos, state);
	}
	public static void tick(Level level, BlockPos ignoredPos, BlockState ignoredState, MicroProcessorBlockEntity be) {
		GlobalVars.update(level);
		be.runLogic();
	}
	private void runLogic() {
		refreshLinks();
		if (disabled()) return;
		var exec = executor();
		if (exec == null || !exec.initialized()) return;
		exec.level = level;
		exec.selfPos = getBlockPos();
		for (var i = 0; i < (int) exec.ipt.numval; i++) {
			exec.runOnce();
			if (!exec.yield) continue;
			exec.yield = false;
			break;
		}
	}
	/** @return 当前处理器是否被 {@code /mlog gamerule} 禁用。 */
	private boolean disabled() {
		var server = level == null ? null : level.getServer();
		if (server == null) return false;
		return MLogRules.get(server).get(privileged() ? Rule.disableWorldProcessor : Rule.disableMicroProcessor);
	}
	/**
	 * 检查链接目标方块。若方块类型变化，则更新链接名，但保持链接顺序。
	 * <p>链接名已包含方块类型前缀，无需额外缓存。目标位置未加载时保留旧名，以支持方块被拆除后重新放置。
	 */
	private void refreshLinks() {
		if (level == null || links.isEmpty()) return;
		var changed = false;
		var origin = getBlockPos();
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			var target = link.absolute(origin);
			if (!level.isLoaded(target)) continue;
			var block = level.getBlockState(target).getBlock();
			if (block == Blocks.AIR || link.name().startsWith(linkBaseName(block))) continue;
			links.set(i, new LogicLink(link.offset(), nextLinkName(block)));
			changed = true;
		}
		// 链接名变化后需重新编译，使代码中的变量名绑定到新链接；链接位置与代码未变，保留运行状态。
		if (!changed) return;
		rebuild(true);
		sync();
	}
	/** @return 执行器；首次访问时编译代码。 */
	private @Nullable LExecutor executor() {
		if (executor == null) rebuild(false);
		return executor;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
		rebuild(false);
		sync();
	}
	public void rebuild(boolean keep) {
		if (level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, getBlockPos());
		var previous = keep && executor != null ? executor.vars : null;
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, getBlockPos(), instructionsPerTick(), links, privileged()));
		if (previous == null) return;
		for (var var : previous) {
			if (var.constant) continue;
			for (var dest : executor.vars) {
				if (!dest.name.equals(var.name) || dest.constant) continue;
				dest.set(var);
				break;
			}
		}
	}
	/** 标记为已更改并同步到客户端，用于刷新悬浮文本。 */
	private void sync() {
		setChanged();
		if (level == null || level.isClientSide) return;
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}
	/** @return 本处理器每 tick 执行的指令数。 */
	private int instructionsPerTick() {
		return privileged() ? WORLD_INSTRUCTIONS_PER_TICK : INSTRUCTIONS_PER_TICK;
	}
	/**
	 * @return 是否为世界处理器。两种处理器共用同一方块实体类型，特权取决于当前方块。
	 * 	<p>客户端也依赖此方法过滤特权语句。
	 */
	public boolean privileged() {
		return getBlockState().getBlock() instanceof WorldProcessorBlock;
	}
	public List<LogicLink> getLinks() {
		return links;
	}
	/** @return 最近一次发送给显示链接器的文本；从未发送时为空串。 */
	public String getDisplayText() {
		return displayText;
	}
	/** 记录本次发送给显示链接器的文本。 */
	public void setDisplayText(String text) {
		displayText = text;
	}
	/**
	 * 建立链接。
	 * <p>失败原因仅服务端可知，因此返回语言键，由 {@code MLogNetwork} 转发给玩家。
	 *
	 * @return 失败原因的语言键；成功返回 {@code null}
	 */
	public @Nullable Component addLink(BlockPos target) {
		if (level == null) return Component.translatable("gui.mlog.link.failed");
		// 特权方块（如世界处理器、世界内存元）仅允许特权处理器连接，对应 Mindustry 的 LogicBlock#validLink。
		// 此类方块实现 GameMasterBlock。
		if (!privileged() && level.getBlockState(target).getBlock() instanceof GameMasterBlock)
			return Component.translatable("gui.mlog.link.denied");
		if (links.size() >= LogicLink.MAX_LINKS) return Component.translatable("gui.mlog.link.full");
		if (!inRange(target)) return Component.translatable("gui.mlog.link.far");
		var offset = target.subtract(getBlockPos());
		if (links.stream().anyMatch(link -> link.offset().equals(offset))) return Component.translatable("gui.mlog.link.exists");
		links.add(new LogicLink(offset, nextLinkName(level.getBlockState(target).getBlock())));
		rebuild(false);
		sync();
		return null;
	}
	/**
	 * @return 目标是否在连接范围内。
	 * 	<p>范围为立方体：三轴偏移均不超过 {@link LogicLink#RANGE}。若按球形判定，对角方块会被误判为越界。
	 */
	private boolean inRange(BlockPos target) {
		var origin = getBlockPos();
		return Math.abs(target.getX() - origin.getX()) <= LogicLink.RANGE
			&& Math.abs(target.getY() - origin.getY()) <= LogicLink.RANGE
			&& Math.abs(target.getZ() - origin.getZ()) <= LogicLink.RANGE;
	}
	/**
	 * 按方块类型生成未占用的链接名，对应 Mindustry 的 {@code findLinkName}。
	 * <p>同类链接使用最小可用编号，因此删除中间链接后，新链接会补上空缺编号。
	 */
	private String nextLinkName(Block block) {
		var base = linkBaseName(block);
		var taken = new HashSet<Integer>();
		var max = 1;
		for (var link : links) {
			if (!link.name().startsWith(base)) continue;
			try {
				var value = Integer.parseInt(link.name().substring(base.length()));
				taken.add(value);
				max = Math.max(value, max);
			} catch (NumberFormatException ignored) {
				// 后缀非数字，不视为占用编号。
			}
		}
		for (var i = 1; i < max + 2; i++) if (!taken.contains(i)) return base + i;
		return base + 1;
	}
	/**
	 * @return 链接名前缀，对应 Mindustry 的 {@code getLinkName}：取方块注册名最后一段。
	 * 	<p>Mindustry 使用连字符，Minecraft 注册名使用下划线。
	 */
	private static String linkBaseName(Block block) {
		var path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		var at = path.lastIndexOf('_');
		return at < 0 ? path : path.substring(at + 1);
	}
	public @Nullable Component removeLink(BlockPos target) {
		var offset = target.subtract(getBlockPos());
		if (!links.removeIf(link -> link.offset().equals(offset))) return Component.translatable("gui.mlog.link.missing");
		rebuild(false);
		sync();
		return null;
	}
	/**
	 * 客户端接收服务端推送的变量快照。
	 * <p>链接随标准方块实体同步（{@code getUpdateTag}）传输，此包仅包含标准同步未覆盖的数据。
	 */
	public void applyVars(CompoundTag vars) {
		varSnapshot = vars;
	}
	/** @return 变量名到“显示文本 + 类型”的快照，仅客户端有值。 */
	public CompoundTag getVarSnapshot() {
		return varSnapshot;
	}
	/** @return 变量快照。运行中的变量不在标准同步中，因此需单独推送。 */
	public CompoundTag buildVarSnapshot() {
		var vars = new CompoundTag();
		if (executor == null) return vars;
		for (var var : executor.vars) {
			// 常量不加入变量表，对应 Mindustry 的 if(s.constant) continue。
			if (var.constant) continue;
			var entry = new CompoundTag();
			// 与 print 共用格式化逻辑，确保显示一致。
			entry.putString("v", PrintI.format(executor, var));
			entry.putInt("t", varType(var));
			vars.put(var.name, entry);
		}
		return vars;
	}
	private static int varType(LVar var) {
		if (!var.isobj) return TYPE_NUMBER;
		return switch (var.objval) {
			case null -> TYPE_NULL;
			case Block ignored -> TYPE_BLOCK;
			case Item ignored -> TYPE_ITEM;
			case Fluid ignored -> TYPE_FLUID;
			// 单位实体与单位类型归为同一类型：{@code lookup unit} 返回类型，{@code query} 返回实体。
			case EntityType<?> ignored -> TYPE_UNIT;
			case Entity ignored -> TYPE_UNIT;
			// {@code query} 返回的建筑以坐标存储。
			case BlockPos ignored -> TYPE_BUILDING;
			case LogicLink ignored -> TYPE_LINK;
			case Enum<?> ignored -> TYPE_ENUM;
			// 无法识别的对象归类为对象，对应 Mindustry 的 typeObject。
			default -> TYPE_OBJECT;
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
	/**
	 * {@code read} 的实现：位置为字符串时读取本处理器变量池中的同名变量；为数字时按索引获取链接。
	 * <p>客户端不编译执行器（{@code loadAdditional} 提前返回），因此先判空。
	 * <p>直接访问字段而非调用 {@code executor()}，避免触发重编译并清除红石充能记录。
	 * <p>世界处理器的变量仅特权处理器可读，对应 Mindustry 的 {@code LogicBuild#readable}。
	 */
	@Override
	public boolean read(LVar position, LVar output, boolean callerPrivileged) {
		if (executor == null) return false;
		if (privileged() && !callerPrivileged) return false;
		if (position.obj() instanceof String name) {
			var var = executor.optionalVar(name);
			if (var == null) return false;
			// 必须进行值拷贝，否则两个处理器的变量池会共享同一实例。
			output.set(var);
			return true;
		}
		var index = (int) position.num();
		output.setobj(index >= 0 && index < executor.links.length ? executor.links[index] : null);
		return true;
	}
	/**
	 * {@code write} 的实现：仅处理字符串位置（变量名）；数字位置不做操作，该分支用于内存方块（见 {@code MemoryBlockEntity}）。
	 * <p>世界处理器的变量仅特权处理器可写，规则同 {@link #read}。
	 */
	@Override
	public boolean write(LVar position, LVar value, boolean callerPrivileged) {
		if (executor == null || !(position.obj() instanceof String name)) return false;
		if (privileged() && !callerPrivileged) return false;
		var var = executor.optionalVar(name);
		// 常量不可写：true / false / null 与链接常量在所有处理器间共享实例。
		if (var == null || var.constant) return false;
		var.set(value);
		return true;
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString(NBT_CODE, code);
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
		var linkList = tag.getList(NBT_LINKS, Tag.TAG_COMPOUND);
		links.clear();
		for (var i = 0; i < linkList.size(); i++) {
			var entry = linkList.getCompound(i);
			links.add(new LogicLink(BlockPos.of(entry.getLong(NBT_OFFSET)), entry.getString(NBT_NAME)));
		}
		// 客户端仅负责渲染，无需执行器。
		if (level != null && level.isClientSide) return;
		rebuild(false);
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