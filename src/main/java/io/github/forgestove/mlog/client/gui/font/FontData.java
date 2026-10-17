package io.github.forgestove.mlog.client.gui.font;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.ByteBuffer;
/** 字形最终交给 FreeType 光栅化，它只在编入 Brotli 时才认 {@code wOF2} 头，WOFF2 因此要先还原成 sfnt。 */
public class FontData {
	/** 读入字体资源并放入直接内存，返回的数据由调用方负责 {@code MemoryUtil.memFree}。 */
	public static ByteBuffer read(InputStream in) throws IOException {
		var bytes = in.readAllBytes();
		if (Woff2Decoder.isWoff2(bytes, 0, bytes.length)) bytes = Woff2Decoder.toSfnt(bytes);
		var memory = MemoryUtil.memAlloc(bytes.length);
		memory.put(bytes).flip();
		return memory;
	}
}
