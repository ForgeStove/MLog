package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
/**
 * 处理器到某个方块的一条链接，{@code name} 形如 {@code block1}。
 * <p>
 * 同空间时 {@code pos} 为相对偏移，方块被推动或复制粘贴后仍然有效；
 * 跨空间时 {@code outside} 为真，改存目标在其空间中的绝对坐标。
 * {@code valid} 每刻按连接范围刷新，失效的链接保留但读写返回空值。
 *
 * @param pos     同空间时为相对偏移，跨空间时为绝对坐标
 * @param name    逻辑代码中引用的名字
 * @param outside 目标是否位于另一空间
 * @param valid   是否仍处于连接范围内
 */
public record LogicLink(BlockPos pos, String name, boolean outside, boolean valid) {
	/** 按处理器当前位置解析目标的绝对坐标。 */
	public BlockPos absolute(BlockPos origin) {
		return outside ? pos : origin.offset(pos);
	}
}
