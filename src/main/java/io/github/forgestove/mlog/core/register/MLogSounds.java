package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.*;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** 界面音效。 */
public final class MLogSounds {
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MLog.ID);
	/** 对应 {@code Sounds.uiButton}。 */
	public static final DeferredHolder<SoundEvent, SoundEvent> UI_BUTTON = register("ui_button");
	/** 对应 {@code Sounds.uiBack}。 */
	public static final DeferredHolder<SoundEvent, SoundEvent> UI_BACK = register("ui_back");
	public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = register("click");
	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		// 定距音效：界面音不该随距离衰减
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(getMLogRes(name)));
	}
}
