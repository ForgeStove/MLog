package io.github.forgestove.mlog.content.microprocessor;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
/** 微型逻辑处理器。每 tick 执行若干条逻辑指令，可链接周围方块并用 {@code sensor} 读取。 */
public class MicroProcessorBlockEntity extends BlockEntity implements MLogSenseable, MenuProvider {
	/**
	 * 每 tick 执行的指令数。
	 * <p>对齐的是「每秒多少条」而不是「每 tick 多少条」：Mindustry 跑 60 TPS、micro-processor
	 * 每 tick 两条（120 条/秒），MC 只有 20 TPS，取六条才追得上同样的速度。
	 */
	public static final int INSTRUCTIONS_PER_TICK = 6;
	/** 变量类型，供变量表着色与显示类型名，对齐 Mindustry 的 {@code typeName}。 */
	public static final int TYPE_NUMBER = 0, TYPE_NULL = 1, TYPE_STRING = 2, TYPE_BLOCK = 3, TYPE_ITEM = 4, TYPE_LINK = 5, TYPE_ENUM = 6,
		TYPE_FLUID = 7, TYPE_UNIT = 8, TYPE_BUILDING = 9, TYPE_OBJECT = 10;
	private static final String NBT_CODE = "code", NBT_LINKS = "links", NBT_OFFSET = "offset", NBT_NAME = "name";
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	private @Nullable LExecutor executor;
	private CompoundTag varSnapshot = new CompoundTag();
	public MicroProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MICRO_PROCESSOR.get(), pos, state);
	}
	public static void tick(Level level, BlockPos ignoredPos, BlockState ignoredState, MicroProcessorBlockEntity be) {
		GlobalVars.update(level);
		be.runLogic();
	}
	/** 执行本 tick 的指令。 */
	private void runLogic() {
		refreshLinks();
		var exec = executor();
		if (exec == null || !exec.initialized()) return;
		exec.level = level;
		exec.selfPos = getBlockPos();
		// 条数由 @ipt 决定，setrate 能改它——这里每 tick 现读一次
		for (var i = 0; i < (int) exec.ipt.numval; i++) {
			exec.runOnce();
			if (exec.yield) {
				exec.yield = false;
				break;
			}
		}
	}
	/**
	 * 查一遍链接指向的方块：类型换掉的就地改名，链接表的顺序不动。
	 * <p>{@code lastBuild} 那套缓存不需要——名字里本来就带着方块类型，比对前缀就知道该不该改。
	 * 位置空着时留着旧名字：方块可能只是被拆了，回头还要放回去。
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
		// 改完名要重新编译，代码里的变量名才会绑到新链接上；链接位置和代码都没变，运行状态留着
		if (!changed) return;
		rebuild(true);
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
	/**
	 * @return 链接名的前缀，对齐 Mindustry 的 {@code getLinkName}：取方块名的最后一段。
	 * 	<p>那边的分隔符是连字符（{@code micro-processor}），MC 的注册名里换成下划线（{@code micro_processor}）。
	 */
	private static String linkBaseName(Block block) {
		var path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		var at = path.lastIndexOf('_');
		return at < 0 ? path : path.substring(at + 1);
	}
	/**
	 * 按方块类型取一个没被占用的链接名，对齐 Mindustry 的 {@code findLinkName}。
	 * <p>同类里取最小的空编号，所以删掉中间某条链接后，再联一个进来会补上那个号码。
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
				// 后缀不是数字，自然不算占用了某号
			}
		}
		for (var i = 1; i < max + 2; i++) if (!taken.contains(i)) return base + i;
		return base + 1;
	}
	/**
	 * @param keep 保留运行中的变量值。代码本身没变、只是链接改了名字时用，
	 *             免得换个名字就把程序状态清掉；常量与链接变量每次编译都会重建，不用留
	 */
	public void rebuild(boolean keep) {
		// 旧代码留下的红石登记一并作废：那条语句可能已经被删掉，不会再有人把它写回 0，
		// 留着就会一直控制着那个方块。新代码跑到那条语句时会重新登记。
		if (level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, getBlockPos());
		var previous = keep && executor != null ? executor.vars : null;
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, getBlockPos(), INSTRUCTIONS_PER_TICK, links));
		if (previous == null) return;
		for (var var : previous) {
			if (var.constant) continue;
			for (var dest : executor.vars)
				if (dest.name.equals(var.name) && !dest.constant) {
					dest.set(var);
					break;
				}
		}
	}
	/** 重新编译代码与链接。 */
	public void rebuild() {
		rebuild(false);
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
	/**
	 * 建立链接。
	 * <p>失败原因只有服务端知道，客户端那边看不到任何回执，所以这里把原因做成 lang key 带出去，
	 * 由 {@code MLogNetwork} 转告玩家——不然点了没反应，不知道是被拒了还是压根没点到。
	 *
	 * @return 失败原因的 lang key，成功返回 {@code null}
	 */
	public @Nullable String addLink(BlockPos target) {
		if (level == null) return "gui.mlog.link.failed";
		if (links.size() >= LogicLink.MAX_LINKS) return "gui.mlog.link.full";
		if (getBlockPos().distSqr(target) > (double) LogicLink.RANGE * LogicLink.RANGE) return "gui.mlog.link.far";
		var offset = target.subtract(getBlockPos());
		if (links.stream().anyMatch(link -> link.offset().equals(offset))) return "gui.mlog.link.exists";
		links.add(new LogicLink(offset, nextLinkName(level.getBlockState(target).getBlock())));
		rebuild();
		sync();
		return null;
	}
	public @Nullable String removeLink(BlockPos target) {
		var offset = target.subtract(getBlockPos());
		if (!links.removeIf(link -> link.offset().equals(offset))) return "gui.mlog.link.missing";
		rebuild();
		sync();
		return null;
	}
	/**
	 * 客户端：接住服务端推来的变量快照。
	 * <p>链接跟着标准方块实体同步走（{@code getUpdateTag}），这个包只需要带标准同步不包含的东西。
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
			// 单位与它的类型同属一档：{@code lookup unit} 查出来的是类型，{@code query} 查出来的是实体
			case EntityType<?> ignored -> TYPE_UNIT;
			case Entity ignored -> TYPE_UNIT;
			// query 查出来的建筑存的是坐标
			case BlockPos ignored -> TYPE_BUILDING;
			case LogicLink ignored -> TYPE_LINK;
			case Enum<?> ignored -> TYPE_ENUM;
			// 认不出来的对象就是「对象」，别冒充字符串，对齐 Mindustry 的 typeObject
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
	 * {@code read} 的落点：位置是名字时读本处理器变量池里的同名变量，是数字时按序号取一条链接。
	 * <p>客户端不编译执行器（{@code loadAdditional} 提前返回），所以先挡一下。
	 * <p>直接读字段而不用 {@code executor()}：那会触发重编译，顺带清掉本处理器的红石充能登记，
	 * 别人读一下变量不该有这样的副作用。
	 */
	@Override
	public boolean read(LVar position, LVar output) {
		if (executor == null) return false;
		if (position.obj() instanceof String name) {
			var var = executor.optionalVar(name);
			if (var == null) return false;
			// 必须是值拷贝：直接把对方的变量实例交出去，两个处理器的变量池就串在一起了
			output.set(var);
			return true;
		}
		var index = (int) position.num();
		output.setobj(index >= 0 && index < executor.links.length ? executor.links[index] : null);
		return true;
	}
	/** {@code write} 的落点：只认变量名，数字位置什么都不做——那个分支是留给内存方块的。 */
	@Override
	public boolean write(LVar position, LVar value) {
		if (executor == null || !(position.obj() instanceof String name)) return false;
		var var = executor.optionalVar(name);
		// 常量不能写：true / false / null 与链接常量在所有处理器之间是同一个实例，改一处等于改全部
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
