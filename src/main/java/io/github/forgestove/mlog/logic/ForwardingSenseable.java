package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
public class ForwardingSenseable implements Senseable {
	protected final Senseable delegate;
	protected ForwardingSenseable(Senseable delegate) {
		this.delegate = delegate;
	}
	@Override
	public double sense(String access) {
		return delegate.sense(access);
	}
	@Override
	public Object senseObject(String access) {
		return delegate.senseObject(access);
	}
	@Override
	public @Nullable Item itemAt(int slot) {
		return delegate.itemAt(slot);
	}
	@Override
	public @Nullable Fluid fluidAt(int tank) {
		return delegate.fluidAt(tank);
	}
	@Override
	public @Nullable IItemHandler itemHandler() {
		return delegate.itemHandler();
	}
	@Override
	public boolean control(
		String access,
		LVar value,
		@Nullable Direction face,
		@Nullable BlockPos owner,
		boolean privileged,
		int index
	) {
		return delegate.control(access, value, face, owner, privileged, index);
	}
	@Override
	public void setProp(LAccess access, LVar value) {
		delegate.setProp(access, value);
	}
	@Override
	public void setProp(String content, LVar value) {
		delegate.setProp(content, value);
	}
	@Override
	public boolean read(LVar position, LVar output, boolean privileged) {
		return delegate.read(position, output, privileged);
	}
	@Override
	public boolean write(LVar position, LVar value, boolean privileged) {
		return delegate.write(position, value, privileged);
	}
	@Override
	public void print(String text) {
		delegate.print(text);
	}
}
