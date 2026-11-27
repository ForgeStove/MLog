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
public class CreateSchematics {
	public static void register(IEventBus modBus) {
		modBus.addListener(
			FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
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
			return new BlockPos((int) Math.round(rotated.x), (int) Math.round(rotated.y), (int) Math.round(rotated.z));
		});
	}
}
