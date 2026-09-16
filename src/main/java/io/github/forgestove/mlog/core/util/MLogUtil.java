package io.github.forgestove.mlog.core.util;
import io.github.forgestove.mlog.MLog;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
/** 双端通用工具。禁止引用任何客户端类，否则服务端会崩。 */
public final class MLogUtil {
	/** @return 本模组命名空间下的资源位置。 */
	public static @NotNull ResourceLocation getMLogRes(String path) {
		return getRes(MLog.ID, path);
	}
	public static @NotNull ResourceLocation getRes(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}
}
