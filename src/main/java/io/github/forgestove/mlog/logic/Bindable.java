package io.github.forgestove.mlog.logic;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.InventoryCarrier;
/** 标记可被 {@code ubind} 绑定的实体。 */
public interface Bindable {
	static boolean of(Entity entity) {
		if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) return false;
		if (mob instanceof Bindable bindable) return bindable.canBind();
		return !(mob instanceof Enemy) && (playerGolem(mob) || tamed(mob) || mob instanceof InventoryCarrier);
	}
	static boolean typeOf(Entity entity) {
		if (entity instanceof Bindable) return entity instanceof Mob;
		return !(entity instanceof Enemy) && (
			entity instanceof AbstractGolem
				|| entity instanceof TamableAnimal
				|| entity instanceof AbstractHorse
				|| entity instanceof InventoryCarrier
		);
	}
	static boolean tamed(Mob mob) {
		return mob instanceof TamableAnimal animal && animal.isTame() || mob instanceof AbstractHorse horse && horse.isTamed();
	}
	static boolean playerGolem(Mob mob) {
		if (mob instanceof IronGolem golem) return golem.isPlayerCreated();
		return mob instanceof AbstractGolem;
	}
	default boolean canBind() {
		return this instanceof Mob mob && mob.isAlive() && !mob.isRemoved();
	}
}
