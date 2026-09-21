package io.github.forgestove.mlog.mixin;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
	private void mlog$virtualSource(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		var signal = mlog$emitted(pos, direction, false);
		if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
	}
	@Unique
	private int mlog$emitted(BlockPos pos, Direction direction, boolean direct) {
		if (!(this instanceof ServerLevel level)) return 0;
		return RedstoneSources.signal(level.dimension(), pos, direction.getOpposite(), direct);
	}
	@Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
	private void mlog$virtualStrongSource(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		var signal = mlog$emitted(pos, direction, true);
		if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
	}
}
