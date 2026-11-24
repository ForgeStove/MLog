package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.compat.sable.*;
import io.github.forgestove.mlog.core.rule.MLogRules;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LExecutor.PrintI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentMap.Builder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;
public abstract class AbstractProcessorBlockEntity extends BlockEntity implements Senseable, Privileged, MenuProvider {
	/**
	 * 指令预算最多积压的速率倍数。
	 * 未执行的刻会累积，单次补偿不超过该倍数。
	 */
	public static final int MAX_INSTRUCTION_SCALE = 5;
	private static final String NBT_CODE = "code";
	private static final String NBT_LINKS = "links";
	private static final String NBT_POS = "pos";
	private static final String NBT_NAME = "name";
	private static final String NBT_OUTSIDE = "outside";
	private static final String NBT_VALID = "valid";
	private static final String NBT_TAG = "tag";
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	/** 世界处理器的显示名，仅特权处理器使用。 */
	private String tag = "";
	/**
	 * 最近一次 {@code printflush} 发给显示链接器的文本，由 Create 兼容层写入。
	 * 不存档、不同步。
	 */
	private String displayText = "";
	private @Nullable LExecutor executor;
	private CompoundTag varSnapshot = new CompoundTag();
	/** 指令预算的余数，未执行完的累积到后续刻。 */
	private int budget;
	/** 上次累积预算的游戏刻，负数表示尚未执行过。 */
	private long lastTick = -1;
	/** 各链接指向的方块类型，与 {@link #links} 按下标对应，仅用于跳过链接名的重算。 */
	private Block[] linkBlocks = {};
	/** 本次方块更新是否只带链接，不必重发代码。 */
	private boolean linksOnly;
	protected AbstractProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
	public static void tick(Level level, BlockPos ignoredPos, BlockState ignoredState, AbstractProcessorBlockEntity be) {
		be.updateTile(level);
	}
	private static String getLinkName(Block block) {
		var path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		var at = path.lastIndexOf('_');
		return at < 0 ? path : path.substring(at + 1);
	}
	private static VarType kind(LVar var) {
		return var.isobj ? VarType.of(var.objval) : VarType.NUMBER;
	}
	/**
	 * 变量表只列出用户变量与 {@code @counter}。
	 * 其余 {@code @} 运行期变量不列出。
	 */
	private static boolean hiddenVar(LVar var) {
		return var.constant || var.name.startsWith("@") && !"@counter".equals(var.name);
	}
	/** 登记到链接改写表，物理结构搬运后据此找出受影响的处理器。 */
	@Override
	public void onLoad() {
		super.onLoad();
		SableLinks.track(this);
	}
	private void updateTile(Level level) {
		if (level.isClientSide) return;
		MLogUnits.expire(level);
		var exec = executor();
		// 没有代码的处理器不执行指令，链接名等编译前再刷新
		if (exec == null || !exec.initialized()) return;
		refreshLinks();
		if (disabled()) return;
		GlobalVars.update(level);
		exec.level = level;
		exec.selfPos = getBlockPos();
		// 每刻按速率累积预算，每执行一条扣一条。
		// 漏掉的刻一并累积，上限为 MAX_INSTRUCTION_SCALE 倍。
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
	/** 是否被游戏规则禁用。 */
	private boolean disabled() {
		var server = level == null ? null : level.getServer();
		return server != null && MLogRules.get(server).get(rule());
	}
	private @Nullable LExecutor executor() {
		if (executor == null) updateCode();
		return executor;
	}
	protected abstract Rule rule();
	public String getCode() {
		return code;
	}
	public String getTag() {
		return tag;
	}
	/**
	 * 世界处理器的显示名。
	 * 仅特权处理器可用。
	 */
	public void setTag(String tag) {
		this.tag = tag;
		sync();
	}
	public void updateCode(String code) {
		this.code = code;
		updateCode();
		sync();
	}
	private void updateCode() {
		MLogUnits.releaseOwned(getBlockPos());
		clearRedstone();
		// 编译需要链接名，空闲期跳过的刷新在此补做一次。
		refreshLinks();
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, getBlockPos(), instructionsPerTick(), iptLimit(), links, privileged()));
	}
	private void sync() {
		sync(true);
	}
	/** 重编或链接变化后，旧的红石登记可能已指向非目标方块。 */
	private void clearRedstone() {
		if (level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, getBlockPos());
	}
	/**
	 * 刷新每条链接，顺序不变。
	 * 类型变化时更新链接名，目标未加载时保留旧名，以支持拆除后重新放置。
	 */
	public void refreshLinks() {
		if (level == null || links.isEmpty()) return;
		var origin = getBlockPos();
		// 与链接表按下标对齐，错位只会让名字多算一次。
		if (linkBlocks.length != links.size()) linkBlocks = Arrays.copyOf(linkBlocks, links.size());
		var changed = false;
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			var target = link.absolute(origin);
			var valid = inRange(target, link.outside());
			var name = link.name();
			if (level.isLoaded(target)) {
				// 只换名字，链接按偏移解析，运行中的代码不受影响。
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
		// 执行器内的链接名单决定 @links 计数、getlink 取值与按名的链接变量
		if (executor != null) executor.updateLinks(links);
		// 链接标记按这份名单绘制，变更后须通知客户端
		sync(false);
	}
	/** 也是 {@code @ipt} 的初值。 */
	protected abstract int instructionsPerTick();
	/** {@code setrate} 的上限，特权处理器可覆写得更高。 */
	protected int iptLimit() {
		return instructionsPerTick();
	}
	/**
	 * 标记为已更改并同步到客户端。
	 * {@code withCode} 为真表示代码本身变过，仅在构造本次数据包期间有效。
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
	/**
	 * 范围为立方体，三轴偏移均不超过 {@link AbstractProcessorBlock#linkRange()}。
	 * {@code outside} 为真时须换算至处理器坐标系。
	 */
	protected boolean inRange(BlockPos target, boolean outside) {
		var origin = getBlockPos();
		var range = linkRange();
		if (range == null) return true;
		// 同空间时坐标差可直接比较，无须构造 Vec3
		if (!outside) return Math.abs(target.getX() - origin.getX()) <= range
			&& Math.abs(target.getY() - origin.getY()) <= range
			&& Math.abs(target.getZ() - origin.getZ()) <= range;
		// 跨空间时坐标差不可比，先换算至处理器所在坐标系
		var in = SableSubLevels.relativeTo(level, origin, Vec3.atLowerCornerOf(target));
		return Math.abs(in.x - origin.getX()) <= range
			&& Math.abs(in.y - origin.getY()) <= range
			&& Math.abs(in.z - origin.getZ()) <= range;
	}
	/**
	 * 按方块类型生成未占用的链接名。
	 * 取最小可用编号，删除中间链接后新链接会补上空缺。
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
	/** 方块不是处理器时按 0 算，使任何目标都落在连接范围之外。 */
	private @Nullable Integer linkRange() {
		if (!(getBlockState().getBlock() instanceof AbstractProcessorBlock block)) return 0;
		return block.linkRange();
	}
	public List<LogicLink> getLinks() {
		return links;
	}
	/**
	 * 物理结构组装或拆解搬走方块后，把链接改写到新坐标。
	 * {@code oldPos} 为搬运前处理器自身的位置，未搬走时与当前坐标相同。
	 * 目标被搬走时须重算该链接；处理器自身被搬走时所有链接的含义都变了，也须全部重算。
	 * 重算后按新的空间关系决定存相对偏移还是绝对坐标。
	 */
	public void remapMovedLinks(BlockPos oldPos, Set<BlockPos> moved, UnaryOperator<BlockPos> toNew) {
		if (links.isEmpty() || level == null) return;
		var newPos = getBlockPos();
		var selfMoved = !oldPos.equals(newPos);
		var changed = false;
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			var oldTarget = link.absolute(oldPos);
			var targetMoved = moved.contains(oldTarget);
			if (!selfMoved && !targetMoved) continue;
			var target = targetMoved ? toNew.apply(oldTarget) : oldTarget;
			// 两者都在搬运后重新判空间，原先的 outside 可能已不适用
			var updated = SableSubLevels.sameSpace(level, newPos, target) ? new LogicLink(
				target.subtract(newPos),
				link.name(),
				false,
				link.valid()
			) : new LogicLink(target, link.name(), true, link.valid());
			if (updated.equals(link)) continue;
			links.set(i, updated);
			changed = true;
		}
		if (!changed) return;
		resetLinkBlocks();
		// 坐标变了但名字与有效标记未必变，refreshLinks 不一定重绑，故显式通知执行器
		if (executor != null) executor.updateLinks(links);
		refreshLinks();
		// 新坐标同步给客户端
		sync(false);
	}
	/**
	 * 按 {@code mapper} 重算所有链接的相对偏移。
	 * 跨空间链接存绝对坐标，跳过。
	 */
	public void transformLinks(UnaryOperator<BlockPos> mapper) {
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			if (link.outside()) continue;
			links.set(i, new LogicLink(mapper.apply(link.pos()), link.name(), false, link.valid()));
		}
	}
	/**
	 * 供显示链接器读取，内容来自最近一次 {@code printflush}。
	 * 从未发送时为空串。
	 */
	public String getDisplayText() {
		return displayText;
	}
	public void setDisplayText(String text) {
		displayText = text;
	}
	/**
	 * 建立链接。
	 * 失败原因仅服务端可知，故返回语言键交给 {@code MLogNetwork} 转发，成功时返回 {@code null}。
	 */
	public @Nullable Component addLink(BlockPos target) {
		if (level == null) return Component.translatable("mlog.gui.link.failed");
		if (!linkable(level.getBlockState(target).getBlock())) return Component.translatable("mlog.gui.link.denied");
		// 跨空间时偏移无效，改存绝对坐标。
		// 空间由服务端判定，不采信客户端。
		var outside = !SableSubLevels.sameSpace(level, getBlockPos(), target);
		if (!inRange(target, outside)) return Component.translatable("mlog.gui.link.far");
		var pos = outside ? target : target.subtract(getBlockPos());
		// 按解析后的绝对坐标判重，处理器换过空间后 outside 标记可能与当前位置不符。
		if (links.stream().anyMatch(link -> link.absolute(getBlockPos()).equals(target)))
			return Component.translatable("mlog.gui.link.exists");
		links.add(new LogicLink(pos, findLinkName(level.getBlockState(target).getBlock()), outside, true));
		resetLinkBlocks();
		clearRedstone();
		// 链接集合变更时在原处重绑，不必重新编译。
		if (executor != null) executor.updateLinks(links);
		sync(false);
		return null;
	}
	protected boolean linkable(Block target) {
		return privileged() || !(target instanceof GameMasterBlock);
	}
	/** 下标不再一一对应，下次刷新按新位置重新识别。 */
	private void resetLinkBlocks() {
		linkBlocks = new Block[links.size()];
	}
	public @Nullable Component removeLink(BlockPos target) {
		// 按解析后的绝对坐标匹配，与客户端 isLinked 的判定一致。
		// 存入时的 outside 可能已因空间变换而过期。
		if (!links.removeIf(link -> link.absolute(getBlockPos()).equals(target))) return Component.translatable("mlog.gui.link.missing");
		resetLinkBlocks();
		clearRedstone();
		if (executor != null) executor.updateLinks(links);
		sync(false);
		return null;
	}
	/**
	 * 客户端接收服务端推送的变量快照。
	 * 链接走标准方块实体同步，此包只带标准同步未覆盖的数据。
	 */
	public void applyVars(CompoundTag vars) {
		varSnapshot = vars;
	}
	/** 仅客户端有值。 */
	public CompoundTag getVarSnapshot() {
		return varSnapshot;
	}
	/** 运行中的变量不在标准同步中，故单独推送。 */
	public CompoundTag buildVarSnapshot() {
		var vars = new CompoundTag();
		if (executor == null) return vars;
		for (var var : executor.vars) {
			if (hiddenVar(var)) continue;
			var entry = new CompoundTag();
			// 与 print 共用格式化逻辑，显示结果才一致。
			entry.putString("v", PrintI.format(executor, var));
			entry.putByte("t", kind(var).id());
			vars.put(var.name, entry);
		}
		return vars;
	}
	/**
	 * 变量表的比较键，顺序与 {@link #buildVarSnapshot} 一致。
	 * 界面用它判断有无变化，未变则不构造快照。
	 */
	public List<Object> buildVarKeys() {
		if (executor == null) return List.of();
		var keys = new ArrayList<>();
		for (var var : executor.vars) {
			if (hiddenVar(var)) continue;
			keys.add(PrintI.changeKey(executor, var));
		}
		return keys;
	}
	@Override
	public double sense(String access) {
		if (level == null) return 0;
		return Senseables.generic(level, getBlockPos()).sense(access);
	}
	@Override
	public Object senseObject(String access) {
		if (level == null) return NO_SENSED;
		return Senseables.generic(level, getBlockPos()).senseObject(access);
	}
	/**
	 * {@code read} 在字符串位置读同名变量，在数字位置按索引取链接。
	 * 直接访问字段以免触发重编译。
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
	@Override
	public boolean write(LVar position, LVar value, boolean callerPrivileged) {
		if (executor == null || !(position.obj() instanceof String name)) return false;
		if (!accessAllowed(callerPrivileged)) return false;
		var var = executor.optionalVar(name);
		// 常量在所有处理器间共享实例，不可写。
		if (var == null || var.constant) return false;
		var.set(value);
		return true;
	}
	protected boolean accessAllowed(boolean callerPrivileged) {
		return !privileged() || callerPrivileged;
	}
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
		if (tag.contains(NBT_CODE)) code = tag.getString(NBT_CODE);
		if (tag.contains(NBT_TAG)) this.tag = tag.getString(NBT_TAG);
		var linkList = tag.getList(NBT_LINKS, Tag.TAG_COMPOUND);
		links.clear();
		for (var i = 0; i < linkList.size(); i++) {
			var entry = linkList.getCompound(i);
			var pos = entry.getLong(NBT_POS);
			links.add(new LogicLink(
				BlockPos.of(pos),
				entry.getString(NBT_NAME),
				entry.getBoolean(NBT_OUTSIDE),
				entry.getBoolean(NBT_VALID)
			));
		}
		resetLinkBlocks();
		if (level != null && level.isClientSide) return;
		updateCode();
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString(NBT_CODE, code);
		tag.putString(NBT_TAG, this.tag);
		tag.put(NBT_LINKS, linksTag());
	}
	/**
	 * 创造模式取下时调用。
	 * 空处理器不写方块实体数据，否则与刚合成的物品无法堆叠。
	 */
	@Override
	public void saveToItem(ItemStack stack, Provider registries) {
		if (isEmptyState()) return;
		super.saveToItem(stack, registries);
	}
	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
	/**
	 * 首次进视野与每次更新都会调用此方法，故不能一概不带代码。
	 * {@link #linksOnly} 只在链接变化时置位。
	 */
	@Override
	public CompoundTag getUpdateTag(Provider registries) {
		var tag = new CompoundTag();
		if (!linksOnly) tag.putString(NBT_CODE, code);
		tag.putString(NBT_TAG, this.tag);
		tag.put(NBT_LINKS, linksTag());
		return tag;
	}
	@Override
	public void setRemoved() {
		SableLinks.untrack(this);
		MLogUnits.releaseOwned(getBlockPos());
		super.setRemoved();
	}
	/** 提供完整的方块实体数据，供掉落物复制后原样还原。 */
	@Override
	protected void collectImplicitComponents(Builder components) {
		super.collectImplicitComponents(components);
		// 名字只取方块实体里的 tag，以覆盖放回世界时带进来的旧 custom_name，空名即删除。
		components.set(DataComponents.CUSTOM_NAME, tag.isEmpty() ? null : Component.literal(tag));
		// 空的处理器与刚合成的物品一致，不带方块实体数据，掉落物才能直接堆叠。
		if (level == null || isEmptyState()) {
			components.set(DataComponents.BLOCK_ENTITY_DATA, null);
			// 放置时带进来的光效标记一并清掉，避免清空过的处理器仍然发光。
			components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null);
			return;
		}
		var saved = saveCustomOnly(level.registryAccess());
		addEntityType(saved, getType());
		components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(saved));
		// 带有存档的处理器物品显示附魔光效。
		components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
	}
	/** 没有代码、名字与链接时与刚合成的物品等价，存档与掉落都不需要带组件。 */
	public boolean isEmptyState() {
		return code.isEmpty() && tag.isEmpty() && links.isEmpty();
	}
	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new ProcessorMenu(id, inventory, worldPosition);
	}
}
