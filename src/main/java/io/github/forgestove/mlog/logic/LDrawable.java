package io.github.forgestove.mlog.logic;
import java.util.List;
/** 接收 {@code drawflush} 交过来的绘制命令的目标。 */
public interface LDrawable {
	/** @return 本次绘制是否被允许，由目标按自身的权限判定给出。 */
	default boolean drawable(LExecutor exec) {
		return true;
	}
	/** 追加绘制命令；缓冲区由调用方负责清空。 */
	void draw(List<DrawCmd> buffer);
}
