package io.github.forgestove.mlog.mixin;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
	private void virtualSource(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		if (!(this instanceof ServerLevel level)) return;
		var signal = RedstoneSources.signal(level.dimension(), pos, direction.getOpposite());
		if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
	}
}
