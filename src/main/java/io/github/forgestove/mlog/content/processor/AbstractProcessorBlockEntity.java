package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.compat.sable.*;
import io.github.forgestove.mlog.content.processor.behaviour.*;
import io.github.forgestove.mlog.core.rule.MLogRules;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LExecutor.PrintI;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
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
import java.util.function.*;
public abstract class AbstractProcessorBlockEntity extends BlockEntity implements Senseable, Privileged, MenuProvider {
	private static final String NBT_CODE = "code";
	private static final String NBT_LINKS = "links";
	private static final String NBT_POS = "pos";
	private static final String NBT_NAME = "name";
	private static final String NBT_OUTSIDE = "outside";
	private static final String NBT_VALID = "valid";
	private static final String NBT_TAG = "tag";
	private static final String NBT_BEHAVIOURS = "behaviours";
	private final Map<BehaviourType<?>, ProcessorBehaviour> behaviours = new Reference2ObjectArrayMap<>();
	private final List<LogicLink> links = new ArrayList<>();
	private String code = "";
	private String tag = "";
	private String displayText = "";
	private @Nullable LExecutor executor;
	private CompoundTag varSnapshot = new CompoundTag();
	private int budget;
	private long lastTick = -1;
	private Block[] linkBlocks = {};
	private boolean linksOnly;
	protected AbstractProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		var list = new ArrayList<ProcessorBehaviour>();
		addBehaviours(list);
		for (var behaviour : list) {
			behaviours.put(behaviour.getType(), behaviour);
			behaviour.initialize();
		}
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
	private static boolean hiddenVar(LVar var) {
		return var.constant || var.name.startsWith("@") && !"@counter".equals(var.name);
	}
	protected void addBehaviours(List<ProcessorBehaviour> list) {
		list.add(new EditButtonBehaviour(this));
	}
	@SuppressWarnings("unchecked")
	public <T extends ProcessorBehaviour> @Nullable T getBehaviour(BehaviourType<T> type) {
		return (T) behaviours.get(type);
	}
	public Collection<ProcessorBehaviour> getAllBehaviours() {
		return behaviours.values();
	}
	public void forEachBehaviour(Consumer<ProcessorBehaviour> action) {
		behaviours.values().forEach(action);
	}
	public void attachBehaviourLate(ProcessorBehaviour behaviour) {
		behaviours.put(behaviour.getType(), behaviour);
		behaviour.initialize();
	}
	public void removeBehaviour(BehaviourType<?> type) {
		var removed = behaviours.remove(type);
		if (removed != null) removed.unload();
	}
	@Override
	public void onLoad() {
		super.onLoad();
		SableLinks.track(this);
	}
	private void updateTile(Level level) {
		if (level.isClientSide) return;
		forEachBehaviour(ProcessorBehaviour::tick);
		MLogUnits.expire(level);
		var exec = executor();
		if (exec == null || !exec.initialized()) return;
		refreshLinks();
		if (disabled()) return;
		GlobalVars.update(level);
		exec.level = level;
		exec.selfPos = getBlockPos();
		var ipt = (int) exec.ipt.numval;
		var now = level.getGameTime();
		var maxInstructionScale = 5;
		var elapsed = lastTick < 0 ? 1 : Math.min(now - lastTick, maxInstructionScale);
		lastTick = now;
		budget = Math.min(budget + (int) elapsed * ipt, maxInstructionScale * ipt);
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
		refreshLinks();
		executor = new LExecutor();
		executor.level = level;
		executor.load(LAssembler.assemble(code, this, getBlockPos(), instructionsPerTick(), iptLimit(), links, privileged()));
	}
	private void sync() {
		sync(true);
	}
	private void clearRedstone() {
		if (level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, getBlockPos());
	}
	public void refreshLinks() {
		if (level == null || links.isEmpty()) return;
		var origin = getBlockPos();
		if (linkBlocks.length != links.size()) linkBlocks = Arrays.copyOf(linkBlocks, links.size());
		var changed = false;
		for (var i = 0; i < links.size(); i++) {
			var link = links.get(i);
			var target = link.absolute(origin);
			var valid = inRange(target, link.outside());
			var name = link.name();
			if (level.isLoaded(target)) {
				var block = level.getBlockState(target).getBlock();
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
		if (executor != null) executor.updateLinks(links);
		sync(false);
	}
	/** 也是 {@code @ipt} 的初值。 */
	protected abstract int instructionsPerTick();
	protected int iptLimit() {
		return instructionsPerTick();
	}
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
	protected boolean inRange(BlockPos target, boolean outside) {
		var origin = getBlockPos();
		var range = linkRange();
		if (range == null) return true;
		if (!outside) return Math.abs(target.getX() - origin.getX()) <= range
			&& Math.abs(target.getY() - origin.getY()) <= range
			&& Math.abs(target.getZ() - origin.getZ()) <= range;
		var in = SableSubLevels.relativeTo(level, origin, Vec3.atLowerCornerOf(target));
		return Math.abs(in.x - origin.getX()) <= range
			&& Math.abs(in.y - origin.getY()) <= range
			&& Math.abs(in.z - origin.getZ()) <= range;
	}
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
			} catch (NumberFormatException ignored) {}
		}
		for (var i = 1; i < max + 2; i++) if (!taken.contains(i)) return base + i;
		return base + 1;
	}
	private @Nullable Integer linkRange() {
		if (!(getBlockState().getBlock() instanceof AbstractProcessorBlock block)) return 0;
		return block.linkRange();
	}
	public List<LogicLink> getLinks() {
		return links;
	}
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
		if (executor != null) executor.updateLinks(links);
		refreshLinks();
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
	public String getDisplayText() {
		return displayText;
	}
	public void setDisplayText(String text) {
		displayText = text;
	}
	public @Nullable Component addLink(BlockPos target) {
		if (level == null) return Component.translatable("mlog.gui.link.failed");
		if (!linkable(level.getBlockState(target).getBlock())) return Component.translatable("mlog.gui.link.denied");
		var outside = !SableSubLevels.sameSpace(level, getBlockPos(), target);
		if (!inRange(target, outside)) return Component.translatable("mlog.gui.link.far");
		var pos = outside ? target : target.subtract(getBlockPos());
		if (links.stream().anyMatch(link -> link.absolute(getBlockPos()).equals(target)))
			return Component.translatable("mlog.gui.link.exists");
		links.add(new LogicLink(pos, findLinkName(level.getBlockState(target).getBlock()), outside, true));
		resetLinkBlocks();
		clearRedstone();
		if (executor != null) executor.updateLinks(links);
		sync(false);
		return null;
	}
	protected boolean linkable(Block target) {
		return privileged() || !(target instanceof GameMasterBlock);
	}
	private void resetLinkBlocks() {
		linkBlocks = new Block[links.size()];
	}
	public @Nullable Component removeLink(BlockPos target) {
		if (!links.removeIf(link -> link.absolute(getBlockPos()).equals(target))) return Component.translatable("mlog.gui.link.missing");
		resetLinkBlocks();
		clearRedstone();
		if (executor != null) executor.updateLinks(links);
		sync(false);
		return null;
	}
	public void applyVars(CompoundTag vars) {
		varSnapshot = vars;
	}
	public CompoundTag getVarSnapshot() {
		return varSnapshot;
	}
	public CompoundTag buildVarSnapshot() {
		var vars = new CompoundTag();
		if (executor == null) return vars;
		for (var var : executor.vars) {
			if (hiddenVar(var)) continue;
			var entry = new CompoundTag();
			entry.putString("v", PrintI.format(executor, var));
			entry.putByte("t", kind(var).id());
			vars.put(var.name, entry);
		}
		return vars;
	}
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
		var behaviourTags = tag.getCompound(NBT_BEHAVIOURS);
		var clientPacket = level != null && level.isClientSide;
		forEachBehaviour(behaviour -> {
			var entry = behaviourTags.getCompound(behaviour.getType().name());
			if (!entry.isEmpty()) behaviour.read(entry, registries, clientPacket);
		});
		if (level != null && level.isClientSide) return;
		updateCode();
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString(NBT_CODE, code);
		tag.putString(NBT_TAG, this.tag);
		tag.put(NBT_LINKS, linksTag());
		writeBehaviours(tag, registries, false);
	}
	@Override
	public void saveToItem(ItemStack stack, Provider registries) {
		if (isEmptyState()) return;
		super.saveToItem(stack, registries);
	}
	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
	@Override
	public CompoundTag getUpdateTag(Provider registries) {
		var tag = new CompoundTag();
		if (!linksOnly) tag.putString(NBT_CODE, code);
		tag.putString(NBT_TAG, this.tag);
		tag.put(NBT_LINKS, linksTag());
		writeBehaviours(tag, registries, true);
		return tag;
	}
	@Override
	public void setRemoved() {
		forEachBehaviour(ProcessorBehaviour::unload);
		SableLinks.untrack(this);
		MLogUnits.releaseOwned(getBlockPos());
		super.setRemoved();
	}
	@Override
	protected void collectImplicitComponents(Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, tag.isEmpty() ? null : Component.literal(tag));
		if (level == null || isEmptyState()) {
			components.set(DataComponents.BLOCK_ENTITY_DATA, null);
			components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null);
			return;
		}
		var saved = saveCustomOnly(level.registryAccess());
		addEntityType(saved, getType());
		components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(saved));
		components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
	}
	private void writeBehaviours(CompoundTag tag, Provider registries, boolean clientPacket) {
		var behaviourTags = new CompoundTag();
		forEachBehaviour(behaviour -> {
			var entry = new CompoundTag();
			behaviour.write(entry, registries, clientPacket);
			if (!entry.isEmpty()) behaviourTags.put(behaviour.getType().name(), entry);
		});
		if (!behaviourTags.isEmpty()) tag.put(NBT_BEHAVIOURS, behaviourTags);
	}
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
