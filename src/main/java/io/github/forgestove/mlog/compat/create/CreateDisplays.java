package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.network.chat.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.*;

import java.util.*;
public class CreateDisplays {
	private static final String SOURCE_NAME = "processor_text";
	private static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MLog.ID);
	private static final DeferredHolder<DisplaySource, ProcessorDisplaySource> PROCESSOR_TEXT = SOURCES.register(
		SOURCE_NAME,
		ProcessorDisplaySource::new
	);
	public static void register(IEventBus modBus) {
		SOURCES.register(modBus);
		modBus.addListener(
			FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
				DisplaySource.BY_BLOCK_ENTITY.add(MLogBlockEntities.MICRO_PROCESSOR.get(), PROCESSOR_TEXT.get());
				DisplaySource.BY_BLOCK_ENTITY.add(MLogBlockEntities.WORLD_PROCESSOR.get(), PROCESSOR_TEXT.get());
			})
		);
	}
	public static void print(DisplayLinkBlockEntity link, String text) {
		var level = link.getLevel();
		if (level == null) return;
		if (!(level.getBlockEntity(link.getSourcePosition()) instanceof AbstractProcessorBlockEntity processor)) return;
		processor.setDisplayText(text);
		link.tickSource();
	}
	private static class ProcessorDisplaySource extends DisplaySource {
		@Override
		public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
			if (!(context.getSourceBlockEntity() instanceof AbstractProcessorBlockEntity processor)) return EMPTY;
			var text = processor.getDisplayText();
			if (text.isEmpty()) return EMPTY;
			return Arrays.stream(text.split("\n", -1)).map(Component::literal).toList();
		}
		@Override
		public boolean shouldPassiveReset() {
			return false;
		}
	}
}
