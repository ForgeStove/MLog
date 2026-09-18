package io.github.forgestove.mlog;
import io.github.forgestove.mlog.client.MLogClientSetup;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.gui.HoverTip;
import io.github.forgestove.mlog.client.gui.LogicCursor;
import io.github.forgestove.mlog.client.render.ModelOutline;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.*;
import net.neoforged.neoforge.common.NeoForge;
/** 客户端入口。 */
@Mod(value = MLog.ID, dist = Dist.CLIENT)
public class MLogClient {
	public MLogClient(IEventBus modBus) {
		modBus.addListener(MLogClientSetup::registerScreens);
		modBus.addListener(MLogClientSetup::registerRenderers);
		modBus.addListener(HoverTip::register);
		var gameBus = NeoForge.EVENT_BUS;
		gameBus.addListener(LinkMode::onMouseButton);
		// 续期要排在退格前面：反过来的话每次提示都会先掉一格亮度
		gameBus.addListener(LinkMode::onClientTick);
		gameBus.addListener(HoverTip::tick);
		gameBus.addListener(LinkMode::onScreenOpening);
		gameBus.addListener(LinkMode::onRightClickBlock);
		gameBus.addListener(LinkMode::onRenderLevel);
		gameBus.addListener(ModelOutline::onRenderHighlight);
		// 光标在整帧的最外层收发：控件渲染时只记请求，界面画完再统一下发一次，
		// 免得一帧里先下发默认箭头、再下发手型，鼠标停着不动时看着像在来回切
		gameBus.addListener(Pre.class, event -> LogicCursor.reset());
		gameBus.addListener(Post.class, event -> LogicCursor.apply());
	}
}
