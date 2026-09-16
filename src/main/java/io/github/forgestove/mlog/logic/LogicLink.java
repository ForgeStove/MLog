package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
/**
 * 处理器到某个方块的链接。{@code name} 形如 {@code block1}，逻辑代码里直接引用。
 * <p>存的是相对处理器的偏移而不是世界坐标：这样方块被活塞推动、被结构方块复制粘贴之后，
 * 链接仍指向原来的相对位置，不会全部失效。
 */
public record LogicLink(BlockPos offset, String name) {
	public static final int MAX_LINKS = 64;
	public static final int RANGE = 10;
	/** @return 相对 {@code origin} 的世界坐标。 */
	public BlockPos absolute(BlockPos origin) {
		return origin.offset(offset);
	}
}
