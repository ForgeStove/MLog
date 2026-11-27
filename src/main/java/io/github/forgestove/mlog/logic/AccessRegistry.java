package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.Mods;
import io.github.forgestove.mlog.logic.Senseables.SlotSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
/**
 * 所有可读可写的属性条目。
 * <p>
 * 原版在类加载时注册，模组在 {@code MLog} 构造时按加载状态注册。
 * <p>
 * 重新注册会重建缓存的列表。
 */
public class AccessRegistry {
	private static final String VANILLA = Mods.minecraft.id();
	private static volatile Map<String, Class<? extends Enum<?>>> enumTypes = Map.of();
	private static volatile Map<String, LAccess> byName = Map.of();
	private static volatile Map<String, List<String>> controls = Map.of();
	private static volatile List<String> names = List.of();
	private static volatile List<String> controlAllowed = List.of();
	private static volatile List<BlockSense> blockSenses = List.of();
	private static volatile List<EntitySense> entitySenses = List.of();
	private static volatile List<SlotSpaceProvider> slotSpaces = List.of();
	static {
		register(VanillaAccess.class, VanillaAccess.controls());
	}
	public static synchronized void register(Class<? extends Enum<?>> type, List<String> controlNames) {
		var constants = type.getEnumConstants();
		if (constants.length == 0) return;
		var newTypes = new LinkedHashMap<>(enumTypes);
		var newByName = new LinkedHashMap<>(byName);
		var newControls = new LinkedHashMap<>(controls);
		var namespace = ((LAccess) constants[0]).namespace();
		newTypes.put(type.getName(), type);
		for (var constant : constants) {
			var access = (LAccess) constant;
			// 同名时先注册的优先；模组的 size 走控制名单，不占 @size
			newByName.putIfAbsent(access.name(), access);
		}
		if (!controlNames.isEmpty()) newControls.put(namespace, List.copyOf(controlNames));
		var entries = new ArrayList<LAccess>();
		for (var enumType : newTypes.values())
			for (var constant : enumType.getEnumConstants()) {
				var access = (LAccess) constant;
				if (newByName.get(access.name()) == access) entries.add(access);
			}
		var allowed = new ArrayList<>(newControls.getOrDefault(VANILLA, List.of()));
		newControls.entrySet()
			.stream()
			.filter(entry -> !VANILLA.equals(entry.getKey()))
			.sorted(Entry.comparingByKey())
			.forEach(entry -> allowed.addAll(entry.getValue()));
		enumTypes = Collections.unmodifiableMap(newTypes);
		byName = Collections.unmodifiableMap(newByName);
		controls = Collections.unmodifiableMap(newControls);
		names = entries.stream().map(access -> "@" + access.name()).toList();
		controlAllowed = List.copyOf(allowed);
	}
	public static @Nullable LAccess byName(String name) {
		return byName.get(name);
	}
	public static List<String> names() {
		return names;
	}
	/** 属性所属的命名空间，未知名字按原版处理。 */
	public static String namespace(String name) {
		var access = byName.get(name);
		return access == null ? VANILLA : access.namespace();
	}
	public static String controlKey(String access) {
		return "mlog.control.prop." + access;
	}
	public static String controlTipKey(String access) {
		return controlKey(access) + ".tip";
	}
	public static boolean isControl(String access) {
		return controlAllowed.contains(access);
	}
	public static List<String> controlAllowed() {
		return controlAllowed;
	}
	public static Map<String, List<String>> controlGroups() {
		var groups = new LinkedHashMap<String, List<String>>();
		groups.put(VANILLA, controls.getOrDefault(VANILLA, List.of()));
		controls.entrySet()
			.stream()
			.filter(entry -> !VANILLA.equals(entry.getKey()))
			.sorted(Entry.comparingByKey())
			.forEach(entry -> groups.put(entry.getKey(), entry.getValue()));
		return groups;
	}
	public static @Nullable Class<? extends Enum<?>> enumType(String className) {
		return enumTypes.get(className);
	}
	public static synchronized void registerBlockSense(BlockSense sense) {
		blockSenses = append(blockSenses, sense);
	}
	public static synchronized void registerEntitySense(EntitySense sense) {
		entitySenses = append(entitySenses, sense);
	}
	public static synchronized void registerSlotSpace(SlotSpaceProvider provider) {
		slotSpaces = append(slotSpaces, provider);
	}
	public static List<BlockSense> blockSenses() {
		return blockSenses;
	}
	public static List<EntitySense> entitySenses() {
		return entitySenses;
	}
	public static List<SlotSpaceProvider> slotSpaces() {
		return slotSpaces;
	}
	private static <T> List<T> append(List<T> list, T value) {
		var copy = new ArrayList<>(list);
		copy.add(value);
		return List.copyOf(copy);
	}
	/** 兼容模组在通用方块适配器外再包一层。 */
	public interface BlockSense {
		Senseable wrap(Senseable delegate, Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side);
	}
	public interface EntitySense {
		Senseable wrap(Senseable delegate, Entity entity);
	}
	/** 兼容模组提供的每槽空间。 */
	public interface SlotSpaceProvider {
		@Nullable SlotSpace of(@Nullable BlockEntity be);
	}
}
