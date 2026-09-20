package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.MLogSenseables;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.List;
/**
 * MLog 与 Create「显示链接器」的兼容，机制对齐 Create 内置的 CC 计算机数据来源
 * （{@code ComputerDisplaySource} + {@code DisplayLinkPeripheral}）：处理器作为数据来源暴露打印输出，
 * {@code printflush} 把文本交给链接器，由链接器按自身配置转发给目标——翻牌显示器、辉光管、告示牌、
 * 讲台均可用，无需逐目标适配。
 * <p>使用方式：链接器贴着处理器放置（数据来源取自链接器面对的那一格），在链接器界面选定本数据来源与目标，
 * 再以 {@code printflush <链接器名>} 送文本。
 * <p>本类直接引用 Create 的类，未安装时不得加载：调用点以 {@link MLogSenseables#CREATE} 挡住。
 */
public final class CreateDisplays {
	/** 注册名，即 lang 键 {@code mlog.display_source.processor_text} 的中段。名称自拟，格式沿用 Create 的 {@code <命名空间>.display_source.<名>}。 */
	private static final String SOURCE_NAME = "processor_text";
	private static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MLog.ID);
	private static final DeferredHolder<DisplaySource, ProcessorDisplaySource> PROCESSOR_TEXT = SOURCES.register(SOURCE_NAME, ProcessorDisplaySource::new);
	/** @param modBus 模组事件总线。仅在装了 Create 时调用——未安装时 Create 的注册表不存在。 */
	public static void register(IEventBus modBus) {
		SOURCES.register(modBus);
		// 挂载依赖方块实体类型完成注册，故放在 setup 阶段。SimpleRegistry 本身线程安全，enqueueWork 只是统一到主线程
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() ->
			DisplaySource.BY_BLOCK_ENTITY.add(MLogBlockEntities.MICRO_PROCESSOR.get(), PROCESSOR_TEXT.get())));
	}
	/**
	 * {@code printflush <显示链接器>} 的落点：文本记到链接器读取的那个处理器上，并触发链接器发送一次。
	 * <p>链接器面对的不是处理器时不作处理——该文本不会被任何数据来源读到。
	 */
	public static void print(DisplayLinkBlockEntity link, String text) {
		var level = link.getLevel();
		if (level == null) return;
		if (!(level.getBlockEntity(link.getSourcePosition()) instanceof MicroProcessorBlockEntity processor)) return;
		processor.setDisplayText(text);
		// 走 tickSource 而不是 updateGatheredData：通着红石时照 Create 的规矩停止发送新信息
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
