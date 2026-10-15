package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
/**
 * 处理器到某个方块的链接：{@code name} 形如 {@code block1}，逻辑代码直接引用；同空间时 {@code pos} 是相对处理器的偏移，方块被推动或复制粘贴后链接仍然有效。
 * 目标跨空间时（地面与子层级之间、两个子层级之间）偏移无效，改存 {@code outside} 与目标所在空间内的绝对坐标；{@code valid} 由处理器每 tick 按连接范围刷新，失效的链接仍保留在列表中，但读写均得到空值。
 */
public record LogicLink(BlockPos pos, String name, boolean outside, boolean valid) {
	public BlockPos absolute(BlockPos origin) {
		return outside ? pos : origin.offset(pos);
	}
}
