package io.github.forgestove.mlog.mixin;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/**
 * 让 {@link RedstoneSources} 里的虚拟源参与原版的信号判定。
 * <p>拦的是「这个坐标朝那个面发了多少」（{@code getSignal}）——它是所有充能判定的汇聚点：
 * {@code hasNeighborSignal} 与 {@code getBestNeighborSignal} 只是它按六个方向取的循环，
 * 中继器与比较器读输入、红石粉算强度也都是问它。只拦它们俩的话信号就止步于目标自己，
 * 贴上去的中继器读不到，旁边的粉也不会亮。
 * <p>{@code getDirectSignal} 是强充能那一份：红石导体靠它被判成「被充能」，
 * 再由 {@code getSignal} 朝四周辐射。只有 {@code strong} 开了的源才给这一份。
 * <p>这两个方法在 {@code SignalGetter} 里各是一份 default 实现，没有任何类覆盖过，拦在这里就够。
 */
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
	private void mlog$virtualSource(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		var signal = mlog$emitted(pos, direction, false);
		if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
	}
	@Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
	private void mlog$virtualStrongSource(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		var signal = mlog$emitted(pos, direction, true);
		if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
	}
	/**
	 * @param pos       发射源所在的位置
	 * @param direction 指向「从接收方到发射源」的方向，所以发射方向是它的反面
	 * @param direct    问的是不是强充能那一份
	 * @return 这个源朝对面那个方块发了多少；不是源就是 0
	 */
	@Unique
	private int mlog$emitted(BlockPos pos, Direction direction, boolean direct) {
		// 客户端不查表：红石行为一律由服务端驱动，两边各算各的反而会不一致
		if (!(this instanceof ServerLevel level)) return 0;
		return RedstoneSources.signal(level.dimension(), pos, direction.getOpposite(), direct);
	}
}
