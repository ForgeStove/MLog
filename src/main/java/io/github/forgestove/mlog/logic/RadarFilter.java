package io.github.forgestove.mlog.logic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.*;
/** {@code uradar} 的目标筛选。 */
public enum RadarFilter {
	any,
	hostile,
	passive,
	player,
	flying,
	ground,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.radarfilter." + name();
	}
	public boolean test(LivingEntity entity) {
		return switch (this) {
			case any -> true;
			case hostile -> entity instanceof Enemy;
			case passive -> entity instanceof Mob && !(entity instanceof Enemy);
			case player -> entity instanceof Player;
			case flying -> !entity.onGround();
			case ground -> entity.onGround();
		};
	}
}
