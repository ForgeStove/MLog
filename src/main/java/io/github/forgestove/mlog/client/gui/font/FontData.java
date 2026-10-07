package io.github.forgestove.mlog.client.gui.font;
import io.github.forgestove.mlog.client.gui.font.woff2.Woff2Decoder;
import net.neoforged.api.distmarker.*;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.ByteBuffer;
/**
 * 字体资源读取。
 * <p>字形最终都交给 FreeType 光栅化，而它只在编入 Brotli 时才认 {@code wOF2} 头，
 * 所以 WOFF2 要先在这里还原成 sfnt。
 */
@OnlyIn(Dist.CLIENT)
public final class FontData {
	/**
	 * 读入字体资源并放入直接内存。
	 *
	 * @return 可直接交给 FreeType 的数据，由调用方负责 {@code MemoryUtil.memFree}
	 */
	public static ByteBuffer read(InputStream in) throws IOException {
		var bytes = in.readAllBytes();
		if (Woff2Decoder.isWoff2(bytes, 0, bytes.length)) bytes = Woff2Decoder.toSfnt(bytes);
		var memory = MemoryUtil.memAlloc(bytes.length);
		memory.put(bytes).flip();
		return memory;
	}
}
