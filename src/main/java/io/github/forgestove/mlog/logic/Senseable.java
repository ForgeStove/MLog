package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
public interface Senseable {
	Object NO_SENSED = new Object();
	double sense(String access);
	default Object senseObject(String access) {
		return NO_SENSED;
	}
	default @Nullable Item itemAt(int slot) {
		return null;
	}
	default @Nullable Fluid fluidAt(int tank) {
		return null;
	}
	default @Nullable IItemHandler itemHandler() {
		return null;
	}
	default boolean control(String access, LVar value, @Nullable Direction face, @Nullable BlockPos owner, boolean privileged, int index) {
		return false;
	}
	default void setProp(LAccess access, LVar value) {}
	default void setProp(String content, LVar value) {}
	default boolean read(LVar position, LVar output, boolean privileged) {
		return false;
	}
	default boolean write(LVar position, LVar value, boolean privileged) {
		return false;
	}
	default void print(String text) {}
}
