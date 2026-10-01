package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.compat.sable.SableSubLevels;
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
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;
/** 逻辑处理器。每 tick 执行若干条逻辑指令，可链接周围方块并通过 {@code sensor} 读取。 */
public abstract class AbstractProcessorBlockEntity extends BlockEntity implements MLogSenseable, Privileged, MenuProvider {
	/**
	 * 指令预算最多积压的速率倍数。
	 * <p>未执行的刻会累积起来补跑，单次补偿不超过该倍数；跑得越快，补跑时一刻执行的条数越多。
	 */
	public static final int MAX_INSTRUCTION_SCALE = 5;
	/** 变量类型 ID，用于变量表着色和类型名显示。 */
	public static final int TYPE_NUMBER = 0, TYPE_NULL = 1, TYPE_STRING = 2, TYPE_BLOCK = 3, TYPE_ITEM = 4, TYPE_LINK = 5, TYPE_ENUM = 6,
		TYPE_FLUID = 7, TYPE_UNIT = 8, TYPE_BUILDING = 9, TYPE_OBJECT = 10;
	/** {@code offset} 为位置键改名前所用键名，用于读取旧存档。 */
	private static final String NBT_CODE = "code", NBT_LINKS = "links", NBT_POS = "pos", NBT_OFFSET = "offset", NBT_NAME = "name",
		NBT_OUTSIDE = "outside", NBT_VALID = "valid";
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	/**
	 * 最近一次 {@code printflush} 发送给显示链接器的文本，由 Create 兼容层写入。
	 * <p>该字段不存档、不同步；重进世界后由下一次 {@code printflush} 重建。
	 */
	private String displayText = "";
	private @Nullable LExecutor executor;
	private CompoundTag varSnapshot = new CompoundTag();
	/** 指令预算的余数：跑不完的累积下来，留给之后的刻补跑。 */
	private int budget;
	/** 上次累积预算的游戏刻；负数表示尚未执行过，首次按一刻计。 */
	private long lastTick = -1;
	/** 上次刷新时各链接指向的方块类型，与 {@link #links} 按下标对应；仅用于跳过链接名的重算。 */
	private Block[] linkBlocks = {};
	/** 本次方块更新是否只带链接：链接变化时代码未变，不必把整段代码重发一遍。 */
	private boolean linksOnly;
	/** @param type 本处理器所属的方块实体类型，两种处理器各挂各的。 */
	protected AbstractProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
	public static void tick(Level level, BlockPos ignoredPos, BlockState ignoredState, AbstractProcessorBlockEntity be) {
		GlobalVars.update(level);
		be.updateTile(level);
	}
	/** @param level 本刻的层级，由 ticker 传进来；方块实体自己的 {@code level} 字段是可空的。 */
	private void updateTile(Level level) {
		refreshLinks();
		if (disabled()) return;
		var exec = executor();
		if (exec == null || !exec.initialized()) return;
		exec.level = level;
		exec.selfPos = getBlockPos();
		// 每刻按速率累积预算、每执行一条扣一条。未执行的刻一并累积（最多 MAX_INSTRUCTION_SCALE 倍），
		// 使错过的刻之后能补跑，平均速率仍为一个 ipt
		var ipt = (int) exec.ipt.numval;
		var now = level.getGameTime();
		var elapsed = lastTick < 0 ? 1 : Math.min(now - lastTick, MAX_INSTRUCTION_SCALE);
		lastTick = now;
		budget = Math.min(budget + (int) elapsed * ipt, MAX_INSTRUCTION_SCALE * ipt);
		while (budget > 0) {
			exec.runOnce();
			if (!exec.yield) {
				budget--;
				continue;
			}
			exec.yield = false;
			break;
		}
	}
	/**
	 * 刷新每条链接的状态：目标方块类型变化则更新链接名，超出连接范围则标记失效，链接顺序不变。
	 * <p>链接名已包含方块类型前缀，无需额外缓存。目标位置未加载时保留旧名，以支持方块被拆除后重新放置。
	 */
	private void refreshLinks() {
		if (level == null || links.isEmpty()) return;
		var origin = getBlockPos();
		// 与链接表按下标对齐；错位只会让名字多算一次，不影响结果
		if (linkBlocks.length != links.size()) linkBlocks = Arrays.copyOf(linkBlocks, links.size());
		var changed = false;
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			var target = link.absolute(origin);
			var valid = inRange(target, link.outside());
			var name = link.name();
			if (level.isLoaded(target)) {
				// 方块类型变了只换名字：链接按偏移解析，运行中的代码不受影响，不必重编译
				var block = level.getBlockState(target).getBlock();
				// 类型没变则名字必然仍旧匹配，无须再查注册表
				if (block != linkBlocks[i] && block != Blocks.AIR) {
					linkBlocks[i] = block;
					if (!name.startsWith(getLinkName(block))) name = findLinkName(block);
				}
			}
			if (valid == link.valid() && name.equals(link.name())) continue;
			links.set(i, new LogicLink(link.pos(), name, link.outside(), valid));
			changed = true;
		}
		if (!changed) return;
		// 同步执行器内的链接名单：@links 计数、getlink 取值与按名的链接变量均由该名单得出
		if (executor != null) executor.updateLinks(links);
		// 链接标记按这份名单绘制，变更后须通知客户端
		sync(false);
	}
	/** @return 当前处理器是否被 {@code /mlog gamerule} 禁用。 */
	private boolean disabled() {
		var server = level == null ? null : level.getServer();
		return server != null && MLogRules.get(server).get(rule());
	}
	/** @return 执行器；首次访问时编译代码。 */
	private @Nullable LExecutor executor() {
		if (executor == null) updateCode();
		return executor;
	}
	/**
	 * @return 目标是否在连接范围内。
	 * 	<p>范围为立方体：三轴偏移均不超过 {@link LogicLink#RANGE}。若按球形判定，对角方块会被误判为越界。
	 * 	<p>{@code outside} 时目标位于其他空间，须先换算至处理器所在坐标系，三轴偏移方可比较。
	 * 	<p>世界处理器不受范围限制，覆写为恒真。
	 */
	protected boolean inRange(BlockPos target, boolean outside) {
		var origin = getBlockPos();
		// 同空间时坐标差可直接比较，无须构造 Vec3
		if (!outside) return Math.abs(target.getX() - origin.getX()) <= LogicLink.RANGE
			&& Math.abs(target.getY() - origin.getY()) <= LogicLink.RANGE
			&& Math.abs(target.getZ() - origin.getZ()) <= LogicLink.RANGE;
		// 跨空间时坐标差不可比，先换算至处理器所在坐标系
		var in = SableSubLevels.relativeTo(level, origin, Vec3.atLowerCornerOf(target));
		return Math.abs(in.x - origin.getX()) <= LogicLink.RANGE
			&& Math.abs(in.y - origin.getY()) <= LogicLink.RANGE
			&& Math.abs(in.z - origin.getZ()) <= LogicLink.RANGE;
	}
	/**
	 * @return 链接名前缀：取方块注册名最后一段。
	 */
	private static String getLinkName(Block block) {
		var path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		var at = path.lastIndexOf('_');
		return at < 0 ? path : path.substring(at + 1);
	}
	/**
	 * 按方块类型生成未占用的链接名。
	 * <p>同类链接使用最小可用编号，因此删除中间链接后，新链接会补上空缺编号。
	 */
	private String findLinkName(Block block) {
		var base = getLinkName(block);
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
	 * 标记为已更改并同步到客户端。
	 *
	 * @param withCode 是否把代码一并发出。只有代码本身变过才需要；链接变化的频率可能很高，带上整段代码纯属浪费。
	 *                 <p>该标志只在本次同步构造数据包期间有效，存档路径不受影响。
	 */
	private void sync(boolean withCode) {
		setChanged();
		if (level == null || level.isClientSide) return;
		linksOnly = !withCode;
		try {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		} finally {
			linksOnly = false;
		}
	}
	/** @return 管辖本处理器的 {@code /mlog gamerule} 规则。 */
	protected abstract Rule rule();
	/** 按当前代码重新编译，变量状态全部重建。 */
	private void updateCode() {
		clearRedstone();
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, getBlockPos(), instructionsPerTick(), links, privileged()));
	}
	/** 清除本处理器留下的虚拟红石源。程序重编或链接集合变化后，旧登记可能指向已不再是目标的方块。 */
	private void clearRedstone() {
		if (level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, getBlockPos());
	}
	/** @return 本处理器每 tick 最多执行的指令数，也是 {@code @ipt} 的上限。 */
	protected abstract int instructionsPerTick();
	public String getCode() {
		return code;
	}
	public void updateCode(String code) {
		this.code = code;
		updateCode();
		sync();
	}
	/** 标记为已更改并同步到客户端，用于刷新悬浮文本。 */
	private void sync() {
		sync(true);
	}
	public List<LogicLink> getLinks() {
		return links;
	}
	/** 按 {@code mapper} 重算所有链接的相对偏移；跨空间链接存绝对坐标，跳过。 */
	public void transformLinks(UnaryOperator<BlockPos> mapper) {
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			if (link.outside()) continue;
			links.set(i, new LogicLink(mapper.apply(link.pos()), link.name(), false, link.valid()));
		}
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
		if (!linkable(level.getBlockState(target).getBlock())) return Component.translatable("gui.mlog.link.denied");
		// 跨空间时偏移无效，改存目标所在空间内的绝对坐标；空间由服务端判定，不采信客户端
		var outside = !SableSubLevels.sameSpace(level, getBlockPos(), target);
		if (!inRange(target, outside)) return Component.translatable("gui.mlog.link.far");
		var pos = outside ? target : target.subtract(getBlockPos());
		if (links.stream().anyMatch(link -> link.outside() == outside && link.pos().equals(pos)))
			return Component.translatable("gui.mlog.link.exists");
		links.add(new LogicLink(pos, findLinkName(level.getBlockState(target).getBlock()), outside, true));
		resetLinkBlocks();
		clearRedstone();
		// 链接集合变更就地重绑，不必重编译
		if (executor != null) executor.updateLinks(links);
		sync(false);
		return null;
	}
	/**
	 * @return 本处理器能否连接该方块。
	 * 	<p>默认不允许连特权方块（实现 {@code GameMasterBlock} 的那些）：内存元、世界处理器一类只有特权处理器能接。
	 */
	protected boolean linkable(Block target) {
		return !(target instanceof GameMasterBlock);
	}
	/** 链接表变动后清空类型缓存：下标不再一一对应，下次刷新按新位置重认一遍。 */
	private void resetLinkBlocks() {
		linkBlocks = new Block[links.size()];
	}
	public @Nullable Component removeLink(BlockPos target) {
		// 判定口径与建链一致：空间与位置均须吻合
		var outside = !SableSubLevels.sameSpace(level, getBlockPos(), target);
		var pos = outside ? target : target.subtract(getBlockPos());
		if (!links.removeIf(link -> link.outside() == outside && link.pos().equals(pos)))
			return Component.translatable("gui.mlog.link.missing");
		resetLinkBlocks();
		clearRedstone();
		if (executor != null) executor.updateLinks(links);
		sync(false);
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
			// 常量不加入变量表。
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
			// 无法识别的对象归类为对象。
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
	 * <p>世界处理器的变量仅特权处理器可读。
	 */
	@Override
	public boolean read(LVar position, LVar output, boolean callerPrivileged) {
		if (executor == null) return false;
		if (!accessAllowed(callerPrivileged)) return false;
		if (position.obj() instanceof String name) {
			var var = executor.optionalVar(name);
			if (var == null) return false;
			// 须进行值拷贝，否则两个处理器的变量池会共享同一实例。
			output.set(var);
			return true;
		}
		var index = (int) position.num();
		output.setobj(index >= 0 && index < executor.links.length ? executor.links[index] : null);
		return true;
	}
	/**
	 * @return 本次按名读写是否被允许。
	 * 	<p>世界处理器的变量池仅特权调用方可碰，普通处理器不设限。
	 */
	protected abstract boolean accessAllowed(boolean callerPrivileged);
	/**
	 * {@code write} 的实现：仅处理字符串位置（变量名）；数字位置不做操作，该分支用于内存方块（见 {@code AbstractMemoryBlockEntity}）。
	 * <p>世界处理器的变量仅特权处理器可写，规则同 {@link #read}。
	 */
	@Override
	public boolean write(LVar position, LVar value, boolean callerPrivileged) {
		if (executor == null || !(position.obj() instanceof String name)) return false;
		if (!accessAllowed(callerPrivileged)) return false;
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
		tag.put(NBT_LINKS, linksTag());
	}
	/** @return 链接表序列化成的标签，存档与同步共用。 */
	private ListTag linksTag() {
		var linkList = new ListTag();
		for (var link : links) {
			var entry = new CompoundTag();
			entry.putLong(NBT_POS, link.pos().asLong());
			entry.putString(NBT_NAME, link.name());
			if (link.outside()) entry.putBoolean(NBT_OUTSIDE, true);
			entry.putBoolean(NBT_VALID, link.valid());
			linkList.add(entry);
		}
		return linkList;
	}
	@Override
	protected void loadAdditional(CompoundTag tag, Provider registries) {
		super.loadAdditional(tag, registries);
		// 链接更新包不带代码，缺键时保留已有的那份
		if (tag.contains(NBT_CODE)) code = tag.getString(NBT_CODE);
		var linkList = tag.getList(NBT_LINKS, Tag.TAG_COMPOUND);
		links.clear();
		for (var i = 0; i < linkList.size(); i++) {
			var entry = linkList.getCompound(i);
			// 旧存档仅存有 offset 键，且无跨空间链接
			var pos = entry.getLong(entry.contains(NBT_POS) ? NBT_POS : NBT_OFFSET);
			// 无 valid 键的旧存档按有效处理，首 tick 刷新会覆盖
			var valid = !entry.contains(NBT_VALID) || entry.getBoolean(NBT_VALID);
			links.add(new LogicLink(BlockPos.of(pos), entry.getString(NBT_NAME), entry.getBoolean(NBT_OUTSIDE), valid));
		}
		resetLinkBlocks();
		// 客户端仅负责渲染，无需执行器。
		if (level != null && level.isClientSide) return;
		updateCode();
	}
	/**
	 * 同步给客户端的数据。
	 * <p>进视野时的首次同步与之后的每次更新都走这里，所以不能一概不带代码——{@link #linksOnly} 只在链接变化那一趟置位。
	 */
	@Override
	public CompoundTag getUpdateTag(Provider registries) {
		var tag = new CompoundTag();
		if (!linksOnly) tag.putString(NBT_CODE, code);
		tag.put(NBT_LINKS, linksTag());
		return tag;
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