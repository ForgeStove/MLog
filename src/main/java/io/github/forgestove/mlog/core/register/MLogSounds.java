package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.*;
/**
 * 界面音效，取自 Mindustry 的 {@code sounds/ui}。
 * <p>那边由 arc 的 {@code ClickListener.clicked} 统一给按钮播放点击音，
 * {@code BaseDialog.hidden} 给关闭播放返回音；这里同样只在两处控件里调用。
 */
public final class MLogSounds {
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MLog.ID);
	/** 按钮点击，对应 {@code Sounds.uiButton}。 */
	public static final DeferredHolder<SoundEvent, SoundEvent> UI_BUTTON = register("ui_button");
	/** 关闭 / 返回，对应 {@code Sounds.uiBack}。 */
	public static final DeferredHolder<SoundEvent, SoundEvent> UI_BACK = register("ui_back");
	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		var id = ResourceLocation.fromNamespaceAndPath(MLog.ID, name);
		// 定距音效：界面音不该随距离衰减
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
	}
}
