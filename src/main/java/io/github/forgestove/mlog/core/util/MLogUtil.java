package io.github.forgestove.mlog.core.util;
import io.github.forgestove.mlog.MLog;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
public final class MLogUtil {
	public static @NotNull ResourceLocation getMLogRes(String path) {
		return getRes(MLog.ID, path);
	}
	public static @NotNull ResourceLocation getRes(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}
}
