package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
/**
 * 蓝图旋转与镜像时，方块实体内部偏移的补算。Create 仅变换方块状态与坐标。
 */
public final class CreateSchematics {
	public static void register(IEventBus modBus) {
		modBus.addListener(
			FMLCommonSetupEvent.class,
			event -> event.enqueueWork(() -> {
				// 两种处理器各挂各的方块实体类型，须分别注册
				MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(
					MLogBlockEntities.MICRO_PROCESSOR.get(),
					CreateSchematics::transform
				);
				MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(
					MLogBlockEntities.WORLD_PROCESSOR.get(),
					CreateSchematics::transform
				);
			})
		);
	}
	private static void transform(BlockEntity be, StructureTransform transform) {
		if (!(be instanceof AbstractProcessorBlockEntity processor)) return;
		processor.transformLinks(pos -> {
			var rotated = transform.applyWithoutOffsetUncentered(Vec3.atLowerCornerOf(pos));
			// 旋转经三角函数计算，90° 时带 1E-17 的误差，按 floor 取整会落到 -1
			return new BlockPos((int) Math.round(rotated.x), (int) Math.round(rotated.y), (int) Math.round(rotated.z));
		});
	}
}
