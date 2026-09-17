package io.github.forgestove.mlog.mixin;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/**
 * 让 {@link RedstoneSources} 里的虚拟充能参与原版的信号判定。
 * <p>拦的是「这个坐标收到了多少」，不是「这个坐标发出多少」（{@code getSignal}）——
 * 拦后者等于把它变成红石源，四周的线、门、活塞全会跟着动，而目标自己反倒没被充能。
 * <p>这两个方法在 {@code SignalGetter} 里各是一份 default 实现，没有任何类覆盖过，拦在这里就够。
 */
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
	private void mlog$virtualCharge(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (mlog$signal(pos) > 0) cir.setReturnValue(true);
	}
	@Unique
	private int mlog$signal(BlockPos pos) {
		// 客户端不查表：红石行为一律由服务端驱动，两边各算各的反而会不一致
		return this instanceof ServerLevel level ? RedstoneSources.signal(level.dimension(), pos) : 0;
	}
	@Inject(method = "getBestNeighborSignal", at = @At("HEAD"), cancellable = true)
	private void mlog$virtualChargeStrength(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		var signal = mlog$signal(pos);
		if (signal > 0) cir.setReturnValue(signal);
	}
}
