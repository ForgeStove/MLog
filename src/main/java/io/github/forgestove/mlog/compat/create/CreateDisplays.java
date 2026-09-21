package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.network.chat.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.*;

import java.util.*;
public final class CreateDisplays {
	private static final String SOURCE_NAME = "processor_text";
	private static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MLog.ID);
	private static final DeferredHolder<DisplaySource, ProcessorDisplaySource> PROCESSOR_TEXT = SOURCES.register(
		SOURCE_NAME,
		ProcessorDisplaySource::new
	);
	public static void register(IEventBus modBus) {
		SOURCES.register(modBus);
		modBus.addListener(
			FMLCommonSetupEvent.class,
			event -> event.enqueueWork(() -> DisplaySource.BY_BLOCK_ENTITY.add(
				MLogBlockEntities.MICRO_PROCESSOR.get(),
				PROCESSOR_TEXT.get()
			))
		);
	}
	public static void print(DisplayLinkBlockEntity link, String text) {
		var level = link.getLevel();
		if (level == null) return;
		if (!(level.getBlockEntity(link.getSourcePosition()) instanceof MicroProcessorBlockEntity processor)) return;
		processor.setDisplayText(text);
		// 走 tickSource 而不是 updateGatheredData：通着红石时停止发送新信息
		link.tickSource();
	}
	/**
	 * 处理器的打印输出。
	 * <p>{@code shouldPassiveReset} 返回 {@code false}：文本仅在 {@code printflush} 时更新，链接器不按周期重推、也不在失去信号时清空，
	 * 与 CC 计算机那份数据来源一致。
	 */
	private static final class ProcessorDisplaySource extends DisplaySource {
		@Override
		public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
			if (!(context.getSourceBlockEntity() instanceof MicroProcessorBlockEntity processor)) return EMPTY;
			var text = processor.getDisplayText();
			// 未交过文本时返回 EMPTY：其内容为一行空文本，可清掉目标上的旧内容
			if (text.isEmpty()) return EMPTY;
			// 一行一个组件，超出的行由目标按自身行数截断
			return Arrays.stream(text.split("\n", -1)).map(Component::literal).toList();
		}
		@Override
		public boolean shouldPassiveReset() {
			return false;
		}
	}
}
