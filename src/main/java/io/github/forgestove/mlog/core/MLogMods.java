package io.github.forgestove.mlog.core;
import net.neoforged.fml.loading.LoadingModList;
/** 支持适配的模组，常量名即模组 id。 */
public enum MLogMods {
	create,
	;
	/** @return 模组 id */
	public String id() {
		return name();
	}
	/** @return 该模组是否已加载 */
	public boolean isLoaded() {
		// 用 LoadingModList：模组加载期 ModList 尚未建好
		return LoadingModList.get().getModFileById(id()) != null;
	}
}
