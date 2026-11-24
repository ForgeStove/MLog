package io.github.forgestove.mlog.mixin;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import io.github.forgestove.mlog.compat.sable.SableLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {
	@Inject(
		method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;"
			+ "Ljava/lang/Iterable;)V", at = @At("RETURN")
	)
	private static void remapLinks(ServerLevel level, AssemblyTransform transform, Iterable<BlockPos> blocks, CallbackInfo ci) {
		SableLinks.handleMove(transform.getLevel(), blocks, transform::apply);
	}
}
