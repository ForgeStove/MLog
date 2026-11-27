package io.github.forgestove.mlog.compat.create;
import io.github.forgestove.mlog.core.Mods;
import io.github.forgestove.mlog.logic.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
public enum CreateAccess implements LAccess {
	filter(AccessFlags.SIDED),
	value,
	valueRow,
	speed,
	stressImpact,
	stressCapacity,
	networkStress,
	networkCapacity,
	hasNetwork,
	networkSize,
	networkSources,
	overstressed,
	axis_along_first(AccessFlags.BLOCK_PROPERTY),
	target(AccessFlags.BLOCK_PROPERTY),
	double_face(AccessFlags.BLOCK_PROPERTY),
	vertical(AccessFlags.BLOCK_PROPERTY),
	backwards(AccessFlags.BLOCK_PROPERTY),
	ceiling(AccessFlags.BLOCK_PROPERTY),
	wall(AccessFlags.BLOCK_PROPERTY),
	flipped(AccessFlags.BLOCK_PROPERTY),
	pointing(AccessFlags.BLOCK_PROPERTY),
	extracting(AccessFlags.BLOCK_PROPERTY),
	casing(AccessFlags.BLOCK_PROPERTY),
	top_shaft(AccessFlags.BLOCK_PROPERTY),
	bottom_shaft(AccessFlags.BLOCK_PROPERTY),
	powering(AccessFlags.BLOCK_PROPERTY),
	shape(AccessFlags.BLOCK_PROPERTY),
	size(AccessFlags.BLOCK_PROPERTY),
	;
	private static final Map<String, CreateAccess> BY_NAME = new HashMap<>();
	private static final List<String> CONTROLS = List.of(
		axis_along_first.name(),
		target.name(),
		double_face.name(),
		vertical.name(),
		backwards.name(),
		ceiling.name(),
		wall.name(),
		flipped.name(),
		pointing.name(),
		extracting.name(),
		casing.name(),
		top_shaft.name(),
		bottom_shaft.name(),
		size.name(),
		powering.name(),
		value.name(),
		filter.name()
	);
	static {
		for (var access : values()) BY_NAME.put(access.name(), access);
	}
	private final int flags;
	CreateAccess() {
		this(0);
	}
	CreateAccess(int flags) {
		this.flags = flags;
	}
	public static @Nullable CreateAccess byName(String name) {
		return BY_NAME.get(name);
	}
	public static List<String> controls() {
		return CONTROLS;
	}
	@Override
	public String namespace() {
		return Mods.create.id();
	}
	@Override
	public boolean blockProperty() {
		return (flags & AccessFlags.BLOCK_PROPERTY) != 0;
	}
	@Override
	public boolean sided() {
		return (flags & AccessFlags.SIDED) != 0;
	}
}
