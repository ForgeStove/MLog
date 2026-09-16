package io.github.forgestove.mlog.core.util;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.*;
/** 客户端专用工具，只能被客户端类引用。 */
@OnlyIn(Dist.CLIENT)
public final class MLogClientUtil {
	public static final Minecraft mc = Minecraft.getInstance();
}
