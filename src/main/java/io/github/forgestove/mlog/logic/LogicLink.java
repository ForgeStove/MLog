package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
/**
 * 处理器到某个方块的链接。{@code name} 形如 {@code block1}，逻辑代码里直接引用。
 * <p>同空间时存相对处理器的偏移而非绝对坐标，方块被推动或复制粘贴后链接仍然有效。
 * <p>目标不在处理器所在空间时（地面与子层级之间、两个子层级之间）偏移无效，改存 {@code outside} 与目标所在空间内的绝对坐标。
 * <p>{@code valid} 由处理器每 tick 按连接范围刷新：失效的链接仍留在名单里，但读写一律得到空值。
 */
public record LogicLink(BlockPos pos, String name, boolean outside, boolean valid) {
	public static final int MAX_LINKS = 64;
	/** 连接范围的半边长：以处理器为中心，三个轴各 ±RANGE 格（立方体而非球体）。 */
	public static final int RANGE = 10;
	/** @return 链接指向的方块绝对坐标；{@code outside} 时 {@code pos} 已是绝对坐标。 */
	public BlockPos absolute(BlockPos origin) {
		return outside ? pos : origin.offset(pos);
	}
}
