package io.github.forgestove.mlog.core.util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.*;
@OnlyIn(Dist.CLIENT)
public final class MLogClientUtil {
	public static final Minecraft mc = Minecraft.getInstance();
}
