package io.github.forgestove.mlog.client.gui.font;
import org.brotli.dec.BrotliInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
/** 将 WOFF2 转换为 sfnt：LWJGL 的 FreeType 未启用 Brotli，无法直接读取 {@code wOF2}。 */
public class Woff2Decoder {
	/** 文件头签名 {@code wOF2}。 */
	private static final int SIGNATURE = 0x774F4632;
	/** 表被变换的标志，放在第 8 位以避开低两位的变换版本号。 */
	private static final int FLAG_TRANSFORM = 1 << 8;
	private static final int SFNT_HEADER_SIZE = 12, SFNT_ENTRY_SIZE = 16;
	private static final int TAG_GLYF = tag("glyf"), TAG_LOCA = tag("loca");
	private static final int TAG_HMTX = tag("hmtx"), TAG_HHEA = tag("hhea");
	private static final int TAG_HEAD = tag("head"), TAG_MAXP = tag("maxp");
	/** 表目录里 tag 可省略时所用的固定标签表，下标对应标志字节的低 6 位。 */
	private static final int[] KNOWN_TAGS = {
		tag("cmap"),
		tag("head"),
		tag("hhea"),
		tag("hmtx"),
		tag("maxp"),
		tag("name"),
		tag("OS/2"),
		tag("post"),
		tag("cvt "),
		tag("fpgm"),
		tag("glyf"),
		tag("loca"),
		tag("prep"),
		tag("CFF "),
		tag("VORG"),
		tag("EBDT"),
		tag("EBLC"),
		tag("gasp"),
		tag("hdmx"),
		tag("kern"),
		tag("LTSH"),
		tag("PCLT"),
		tag("VDMX"),
		tag("vhea"),
		tag("vmtx"),
		tag("BASE"),
		tag("GDEF"),
		tag("GPOS"),
		tag("GSUB"),
		tag("EBSC"),
		tag("JSTF"),
		tag("MATH"),
		tag("CBDT"),
		tag("CBLC"),
		tag("COLR"),
		tag("CPAL"),
		tag("SVG "),
		tag("sbix"),
		tag("acnt"),
		tag("avar"),
		tag("bdat"),
		tag("bloc"),
		tag("bsln"),
		tag("cvar"),
		tag("fdsc"),
		tag("feat"),
		tag("fmtx"),
		tag("fvar"),
		tag("gvar"),
		tag("hsty"),
		tag("just"),
		tag("lcar"),
		tag("mort"),
		tag("morx"),
		tag("opbd"),
		tag("prop"),
		tag("trak"),
		tag("Zapf"),
		tag("Silf"),
		tag("Glat"),
		tag("Gloc"),
		tag("Feat"),
		tag("Sill")
	};
	public static boolean isWoff2(byte[] data, int offset, int length) {
		return length >= 4
			&& offset >= 0
			&& offset + 4 <= data.length
			&& (data[offset] & 0xFF) == 'w'
			&& (data[offset + 1] & 0xFF) == 'O'
			&& (data[offset + 2] & 0xFF) == 'F'
			&& (data[offset + 3] & 0xFF) == '2';
	}
	/** 数据非法或解码失败时抛出 {@link Woff2Exception}。 */
	public static byte[] toSfnt(byte[] data) {
		var header = readHeader(data);
		var out = new Out(SFNT_HEADER_SIZE + SFNT_ENTRY_SIZE * header.tables.length);
		writeHeaders(header, out);
		var info = new Woff2FontInfo();
		reconstructFont(data, header, out, info);
		return Arrays.copyOf(out.data, out.size());
	}
	private static Woff2Header readHeader(byte[] data) {
		var header = new Woff2Header();
		var file = new Buffer(data);
		if (file.u32() != SIGNATURE) throw new Woff2Exception("Not WOFF2 data");
		header.flavor = file.u32();
		if (file.u32() != data.length) throw new Woff2Exception("File length does not match header");
		header.numTables = file.u16();
		if (header.numTables == 0) throw new Woff2Exception("Table count is zero");
		// 跳过 reserved 与 totalSfntSize，后者不可信
		file.skip(6);
		header.compressedLength = file.u32();
		file.skip(4); // majorVersion / minorVersion
		if (file.u32() != 0) throw new Woff2Exception("Metadata is not supported");
		if (file.u32() != 0) throw new Woff2Exception("Metadata length is not zero");
		file.skip(4); // metaOrigLength
		if (file.u32() != 0) throw new Woff2Exception("Private data is not supported");
		if (file.u32() != 0) throw new Woff2Exception("Private data length is not zero");
		header.tables = new Table[header.numTables];
		readTableDirectory(file, header.tables);
		var last = header.tables[header.numTables - 1];
		header.uncompressedSize = last.srcOffset + last.srcLength;
		if (header.flavor == tag("ttcf")) throw new Woff2Exception("TrueType collections are not supported");
		header.compressedOffset = file.offset();
		// 压缩块与文件尾部之间仅允许 4 字节对齐的填充
		if (round4((long) header.compressedOffset + header.compressedLength) != round4(data.length))
			throw new Woff2Exception("Compressed block length does not match file length");
		return header;
	}
	/** 按标签升序写出 sfnt 文件头与表目录，各表条目先以 0 占位，待重建时回填。 */
	private static void writeHeaders(Woff2Header header, Out out) {
		var sorted = header.tables.clone();
		Arrays.sort(sorted, (a, b) -> Integer.compareUnsigned(a.tag, b.tag));
		var head = new byte[SFNT_HEADER_SIZE + SFNT_ENTRY_SIZE * header.numTables];
		int offset = SFNT_HEADER_SIZE;
		storeU32(head, 0, header.flavor);
		store16(head, 4, header.numTables);
		var maxPow2 = 0;
		while (1 << maxPow2 + 1 <= header.numTables) maxPow2++;
		int searchRange = 1 << maxPow2 << 4;
		store16(head, 6, searchRange);
		store16(head, 8, maxPow2);
		store16(head, 10, (header.numTables << 4) - searchRange);
		for (var i = 0; i < header.numTables; i++) {
			var table = sorted[i];
			table.entryOffset = offset;
			storeU32(head, offset, table.tag);
			offset += SFNT_ENTRY_SIZE;
		}
		header.sortedTables = sorted;
		out.write(head, 0, head.length);
	}
	/**
	 * 解压压缩块，逐表重建并回填表目录，最后修正 {@code head} 的校验和调整量；按标签升序处理，{@code hhea} 先于 {@code hmtx} 提供横向度量数，{@code glyf} 先于 {@code hmtx} 提供各字形的
	 * xMin。
	 */
	private static void reconstructFont(byte[] data, Woff2Header header, Out out, Woff2FontInfo info) {
		Table glyf = null, loca = null, hmtx = null;
		for (var table : header.tables)
			if (table.tag == TAG_GLYF) glyf = table;
			else if (table.tag == TAG_LOCA) loca = table;
			else if (table.tag == TAG_HMTX) hmtx = table;
		if ((glyf == null) != (loca == null)) throw new Woff2Exception("glyf and loca must appear together");
		if (glyf != null && (glyf.flags & FLAG_TRANSFORM) != (loca.flags & FLAG_TRANSFORM))
			throw new Woff2Exception("glyf and loca transform states must match");
		var transformed = new byte[header.uncompressedSize];
		decompress(data, header, transformed);
		if (hmtx != null && (hmtx.flags & FLAG_TRANSFORM) != 0 && (glyf == null || (glyf.flags & FLAG_TRANSFORM) == 0))
			readRawGlyfInfo(header, glyf, loca, transformed, info);
		int fontChecksum = computeULongSum(out.data, 0, out.size);
		var entry = new byte[SFNT_ENTRY_SIZE - 4];
		for (var table : header.sortedTables) {
			// hhea 的 numberOfHMetrics 位于表内偏移 34 处，重建 hmtx 时需要
			if (table.tag == TAG_HHEA) {
				if (table.srcLength < 36) throw new Woff2Exception("hhea table is too short");
				info.numHmetrics = (transformed[table.srcOffset + 34] & 0xFF) << 8 | transformed[table.srcOffset + 35] & 0xFF;
			}
			int checksum;
			if ((table.flags & FLAG_TRANSFORM) == 0) {
				if (table.tag == TAG_HEAD) {
					if (table.srcLength < 12) throw new Woff2Exception("head table is too short");
					storeU32(transformed, table.srcOffset + 8, 0); // 校验和调整量先置 0
				}
				table.dstOffset = out.size();
				checksum = computeULongSum(transformed, table.srcOffset, table.srcLength);
				out.write(transformed, table.srcOffset, table.srcLength);
			} else if (table.tag == TAG_GLYF) {
				if (loca == null) throw new Woff2Exception("glyf is missing its loca table");
				table.dstOffset = out.size();
				reconstructGlyf(transformed, table, loca, info, out);
				checksum = table.checksum;
			} else if (table.tag == TAG_LOCA) checksum = table.checksum; // 已随重建 glyf 一并写出
			else if (table.tag == TAG_HMTX) {
				table.dstOffset = out.size();
				checksum = reconstructHmtx(transformed, table, info, out);
			} else throw new Woff2Exception("Unknown transformed table");
			fontChecksum += checksum;
			storeU32(entry, 0, checksum);
			storeU32(entry, 4, table.dstOffset);
			storeU32(entry, 8, table.dstLength);
			out.writeAt(entry, table.entryOffset + 4);
			fontChecksum += computeULongSum(entry, 0, entry.length);
			pad4(out);
		}
		for (var table : header.sortedTables)
			if (table.tag == TAG_HEAD) {
				if (table.dstLength < 12) throw new Woff2Exception("head table is too short");
				var adjustment = new byte[4];
				storeU32(adjustment, 0, 0xB1B0AFBA - fontChecksum);
				out.writeAt(adjustment, table.dstOffset + 8);
			}
	}
	/** 读取表目录，各项按出现顺序依次排布在压缩块内。 */
	private static void readTableDirectory(Buffer file, Table[] tables) {
		var srcOffset = 0;
		for (var i = 0; i < tables.length; i++) {
			var table = tables[i] = new Table();
			int flagByte = file.u8();
			int index = flagByte & 0x3F;
			int tag = index == 0x3F ? file.u32() : KNOWN_TAGS[index];
			int xformVersion = flagByte >> 6 & 0x03;
			// glyf/loca：0 为已变换、3 为未变换；其余表仅允许 hmtx 的版本 1
			if (tag == TAG_GLYF || tag == TAG_LOCA) {
				if (xformVersion == 0) table.flags |= FLAG_TRANSFORM;
				else if (xformVersion != 3) throw new Woff2Exception("Unknown transform version");
			} else if (xformVersion != 0) {
				if (tag != TAG_HMTX || xformVersion != 1) throw new Woff2Exception("Unknown transform version");
				table.flags |= FLAG_TRANSFORM;
			}
			table.flags |= xformVersion;
			table.dstLength = readBase128(file);
			table.transformLength = table.dstLength;
			if ((table.flags & FLAG_TRANSFORM) != 0) {
				table.transformLength = readBase128(file);
				if (tag == TAG_LOCA && table.transformLength != 0)
					throw new Woff2Exception("loca must not have a separate transform length");
			}
			table.tag = tag;
			table.srcOffset = srcOffset;
			table.srcLength = table.transformLength;
			srcOffset += table.transformLength;
		}
	}
	private static int tag(String tag) {
		return tag(tag.charAt(0), tag.charAt(1), tag.charAt(2), tag.charAt(3));
	}
	private static int tag(char a, char b, char c, char d) {
		return a << 24 | b << 16 | c << 8 | d;
	}
	private static long round4(long value) {
		return value + 3 & ~3L;
	}
	private static int storeU32(byte[] dst, int offset, int value) {
		dst[offset] = (byte) (value >>> 24);
		dst[offset + 1] = (byte) (value >>> 16);
		dst[offset + 2] = (byte) (value >>> 8);
		dst[offset + 3] = (byte) value;
		return offset + 4;
	}
	private static int store16(byte[] dst, int offset, int value) {
		dst[offset] = (byte) (value >> 8);
		dst[offset + 1] = (byte) value;
		return offset + 2;
	}
	/** 解压 Brotli 压缩块；解出长度与表目录推算值一致。 */
	private static void decompress(byte[] data, Woff2Header header, byte[] out) {
		if (out.length == 0) throw new Woff2Exception("Compressed block length is zero");
		try (var in = new BrotliInputStream(new ByteArrayInputStream(data, header.compressedOffset, header.compressedLength))) {
			var offset = 0;
			while (offset < out.length) {
				int read = in.read(out, offset, out.length - offset);
				if (read < 0) break;
				offset += read;
			}
			if (offset != out.length || in.read() >= 0) throw new Woff2Exception("Decompressed length does not match");
		} catch (IOException e) {
			throw new Woff2Exception("Brotli decompression failed: " + e.getMessage());
		}
	}
	/** sfnt 的表校验和：按大端 32 位累加，尾部不足 4 字节的按 0 补齐。 */
	private static int computeULongSum(byte[] buf, int offset, int size) {
		var checksum = 0;
		int aligned = size & ~3;
		for (var i = 0; i < aligned; i += 4)
			checksum += (buf[offset + i] & 0xFF) << 24
				| (buf[offset + i + 1] & 0xFF) << 16
				| (buf[offset + i + 2] & 0xFF) << 8
				| buf[offset + i + 3] & 0xFF;
		for (var i = aligned; i < size; i++) checksum += (buf[offset + i] & 0xFF) << 24 - 8 * (i & 3);
		return checksum;
	}
	/** 由变换后的 glyf 数据重建 glyf 与 loca 两张表。 */
	private static void reconstructGlyf(byte[] data, Table glyf, Table loca, Woff2FontInfo info, Out out) {
		var file = new Buffer(data, glyf.srcOffset, glyf.transformLength);
		file.u16(); // 版本，未使用
		boolean hasOverlapBitmap = (file.u16() & 1) != 0;
		info.numGlyphs = file.u16();
		info.indexFormat = file.u16();
		if (info.indexFormat > 1) throw new Woff2Exception("Invalid loca index format");
		int expectedLocaDstLength = (info.indexFormat != 0 ? 4 : 2) * (info.numGlyphs + 1);
		if (loca.dstLength != expectedLocaDstLength) throw new Woff2Exception("loca table length does not match glyph count");
		var substreams = new Buffer[7];
		var offset = 36; // 4 个头字段 + 7 段长度表
		for (var i = 0; i < substreams.length; i++) {
			int size = file.u32();
			if (size > glyf.transformLength - offset) throw new Woff2Exception("Substream out of bounds");
			substreams[i] = new Buffer(data, glyf.srcOffset + offset, size);
			offset += size;
		}
		var nContourStream = substreams[0];
		var nPointsStream = substreams[1];
		var flagStream = substreams[2];
		var glyphStream = substreams[3];
		var compositeStream = substreams[4];
		var bboxStream = substreams[5];
		var instructionStream = substreams[6];
		byte[] overlapBitmap = null;
		if (hasOverlapBitmap) {
			int length = info.numGlyphs + 7 >> 3;
			if (length > glyf.transformLength - offset) throw new Woff2Exception("Overlap bitmap out of bounds");
			overlapBitmap = Arrays.copyOfRange(data, glyf.srcOffset + offset, glyf.srcOffset + offset + length);
		}
		var locaValues = new int[info.numGlyphs + 1];
		var contourPoints = new int[256];
		var pointX = new int[256];
		var pointY = new int[256];
		var pointOnCurve = new boolean[256];
		var bboxBitmap = bboxStream.read(info.numGlyphs + 31 >> 5 << 2);
		var glyphBuf = new byte[5120];
		info.xMins = new int[info.numGlyphs];
		int glyfStart = out.size();
		for (var i = 0; i < info.numGlyphs; i++) {
			boolean haveBbox = (bboxBitmap[i >> 3] & 0x80 >> (i & 7)) != 0;
			int nContours = nContourStream.u16();
			int glyphSize;
			if (nContours == 0xFFFF) {
				// 复合字形：轮廓数据完整存于复合流，原样搬运
				if (!haveBbox) throw new Woff2Exception("Composite glyph is missing a bounding box");
				var composite = sizeOfComposite(compositeStream);
				var instructionSize = 0;
				if (composite.haveInstructions) instructionSize = read255UShort(glyphStream);
				int needed = 12 + composite.size + instructionSize;
				if (glyphBuf.length < needed) glyphBuf = new byte[needed];
				glyphSize = store16(glyphBuf, 0, nContours);
				bboxStream.read(glyphBuf, glyphSize, 8);
				glyphSize += 8;
				compositeStream.read(glyphBuf, glyphSize, composite.size);
				glyphSize += composite.size;
				if (composite.haveInstructions) {
					glyphSize = store16(glyphBuf, glyphSize, instructionSize);
					instructionStream.read(glyphBuf, glyphSize, instructionSize);
					glyphSize += instructionSize;
				}
			} else if (nContours > 0) {
				// 简单字形：轮廓点数、标志与三元组编码的坐标分散在各子流
				if (contourPoints.length < nContours) contourPoints = new int[nContours];
				var totalPoints = 0;
				for (var j = 0; j < nContours; j++) {
					int points = read255UShort(nPointsStream);
					contourPoints[j] = points;
					totalPoints += points;
				}
				if (flagStream.remaining() < totalPoints) throw new Woff2Exception("Flag stream out of bounds");
				if (pointX.length < totalPoints) {
					pointX = new int[totalPoints];
					pointY = new int[totalPoints];
					pointOnCurve = new boolean[totalPoints];
				}
				int consumed = tripletDecode(flagStream, glyphStream, totalPoints, pointX, pointY, pointOnCurve);
				flagStream.skip(totalPoints);
				glyphStream.skip(consumed);
				int instructionSize = read255UShort(glyphStream);
				int needed = 12 + 2 * nContours + 5 * totalPoints + instructionSize;
				if (glyphBuf.length < needed) glyphBuf = new byte[needed];
				glyphSize = store16(glyphBuf, 0, nContours);
				if (haveBbox) bboxStream.read(glyphBuf, glyphSize, 8);
				else computeBbox(totalPoints, pointX, pointY, glyphBuf);
				glyphSize = 10; // endPtsOfContours 起始
				int endPoint = -1;
				for (var j = 0; j < nContours; j++) {
					endPoint += contourPoints[j];
					glyphSize = store16(glyphBuf, glyphSize, endPoint);
				}
				glyphSize = store16(glyphBuf, glyphSize, instructionSize);
				instructionStream.read(glyphBuf, glyphSize, instructionSize);
				boolean hasOverlap = hasOverlapBitmap && (overlapBitmap[i >> 3] & 0x80 >> (i & 7)) != 0;
				glyphSize = storePoints(totalPoints, pointX, pointY, pointOnCurve, nContours, instructionSize, hasOverlap, glyphBuf);
			} else {
				glyphSize = 0; // 空字形不带包围盒
				if (haveBbox) throw new Woff2Exception("Empty glyph has a bounding box");
			}
			locaValues[i] = out.size() - glyfStart;
			out.write(glyphBuf, 0, glyphSize);
			pad4(out);
			glyf.checksum += computeULongSum(glyphBuf, 0, glyphSize);
			if (nContours != 0) info.xMins[i] = (short) ((glyphBuf[2] & 0xFF) << 8 | glyphBuf[3] & 0xFF);
		}
		glyf.dstLength = out.size() - glyf.dstOffset;
		loca.dstOffset = out.size();
		locaValues[info.numGlyphs] = glyf.dstLength;
		// loca 的偏移在短格式下以 2 为单位存
		var locaData = new byte[locaValues.length * (info.indexFormat != 0 ? 4 : 2)];
		var locaOffset = 0;
		for (int value : locaValues)
			if (info.indexFormat != 0) locaOffset = storeU32(locaData, locaOffset, value);
			else locaOffset = store16(locaData, locaOffset, value >> 1);
		loca.checksum = computeULongSum(locaData, 0, locaData.length);
		out.write(locaData, 0, locaData.length);
	}
	/** 由变换后的 hmtx 数据重建 hmtx 表；左侧边距常被省略，缺省时取字形的 xMin。 */
	private static int reconstructHmtx(byte[] data, Table table, Woff2FontInfo info, Out out) {
		var in = new Buffer(data, table.srcOffset, table.srcLength);
		int flags = in.u8();
		if ((flags & 0xFC) != 0) throw new Woff2Exception("Invalid hmtx flags");
		boolean proportionalLsbs = (flags & 1) == 0, monospaceLsbs = (flags & 2) == 0;
		if (proportionalLsbs && monospaceLsbs) throw new Woff2Exception("hmtx provides no side bearing data");
		if (info.numHmetrics > info.numGlyphs) throw new Woff2Exception("hhea metric count exceeds glyph count");
		if (info.numHmetrics < 1) throw new Woff2Exception("hhea metric count is zero");
		var advanceWidths = new int[info.numHmetrics];
		var lsbs = new int[info.numGlyphs];
		for (var i = 0; i < info.numHmetrics; i++) advanceWidths[i] = in.u16();
		for (var i = 0; i < info.numHmetrics; i++) lsbs[i] = proportionalLsbs ? in.s16() : info.xMins[i];
		for (var i = info.numHmetrics; i < info.numGlyphs; i++) lsbs[i] = monospaceLsbs ? in.s16() : info.xMins[i];
		var hmtx = new byte[2 * info.numGlyphs + 2 * info.numHmetrics];
		var offset = 0;
		for (var i = 0; i < info.numGlyphs; i++) {
			if (i < info.numHmetrics) offset = store16(hmtx, offset, advanceWidths[i]);
			offset = store16(hmtx, offset, lsbs[i]);
		}
		out.write(hmtx, 0, hmtx.length);
		return computeULongSum(hmtx, 0, hmtx.length);
	}
	/** 未变换 glyf 时的字形数与 xMin，供 hmtx 重建使用。 */
	private static void readRawGlyfInfo(Woff2Header header, @Nullable Table glyf, @Nullable Table loca, byte[] data, Woff2FontInfo info) {
		Table maxp = null, head = null;
		for (var table : header.tables)
			if (table.tag == TAG_MAXP) maxp = table;
			else if (table.tag == TAG_HEAD) head = table;
		if (glyf == null || loca == null || maxp == null || head == null)
			throw new Woff2Exception("hmtx transform is missing required glyf tables");
		if (maxp.srcLength < 6 || head.srcLength < 52) throw new Woff2Exception("maxp or head table is too short");
		info.numGlyphs = readU16(data, maxp.srcOffset + 4);
		boolean longLoca = readU16(data, head.srcOffset + 50) != 0;
		if (loca.srcLength < (longLoca ? 4 : 2) * (info.numGlyphs + 1))
			throw new Woff2Exception("loca table length does not match glyph count");
		info.xMins = new int[info.numGlyphs];
		for (var i = 0; i < info.numGlyphs; i++) {
			int start = readLoca(data, loca.srcOffset, i, longLoca), end = readLoca(data, loca.srcOffset, i + 1, longLoca);
			if (start < 0 || end < start || end > glyf.srcLength) throw new Woff2Exception("loca offset out of bounds");
			if (end > start) {
				if (start + 4 > end) throw new Woff2Exception("Glyph data out of bounds");
				info.xMins[i] = (short) readU16(data, glyf.srcOffset + start + 2);
			}
		}
	}
	private static int readLoca(byte[] data, int offset, int index, boolean longLoca) {
		return longLoca ? readU32(data, offset + 4 * index) : readU16(data, offset + 2 * index) << 1;
	}
	private static int readU16(byte[] src, int offset) {
		return (src[offset] & 0xFF) << 8 | src[offset + 1] & 0xFF;
	}
	private static int readU32(byte[] src, int offset) {
		return (src[offset] & 0xFF) << 24 | (src[offset + 1] & 0xFF) << 16 | (src[offset + 2] & 0xFF) << 8 | src[offset + 3] & 0xFF;
	}
	private static void pad4(Out out) {
		int pad = (out.size() + 3 & ~3) - out.size();
		if (pad > 0) out.write(new byte[pad], 0, pad);
	}
	private static int readBase128(Buffer buf) {
		var result = 0;
		for (var i = 0; i < 5; i++) {
			int code = buf.u8();
			if (i == 0 && code == 0x80) throw new Woff2Exception("Invalid UIntBase128 leading zero");
			if ((result & 0xFE000000) != 0) throw new Woff2Exception("UIntBase128 overflow");
			result = result << 7 | code & 0x7F;
			if ((code & 0x80) == 0) return result;
		}
		throw new Woff2Exception("UIntBase128 is too long");
	}
	/** 量出复合字形的组件记录长度并判断是否带指令；只读，不推进 {@code source}，调用方按此长度搬运记录。 */
	private static CompositeInfo sizeOfComposite(Buffer source) {
		var stream = new Buffer(source.data, source.offset(), source.remaining());
		var info = new CompositeInfo();
		int start = stream.offset();
		int flags = 1 << 5; // MORE_COMPONENTS
		while ((flags & 1 << 5) != 0) {
			flags = stream.u16();
			if ((flags & 1 << 8) != 0) info.haveInstructions = true;
			int argSize = (flags & 1) != 0 ? 6 : 4; // 字形索引 + 两参数
			if ((flags & 1 << 3) != 0) argSize += 2;
			else if ((flags & 1 << 6) != 0) argSize += 4;
			else if ((flags & 1 << 7) != 0) argSize += 8;
			stream.skip(argSize);
		}
		info.size = stream.offset() - start;
		return info;
	}
	/** 读 {@code 255UInt16}，对应字形点数等小整数。 */
	private static int read255UShort(Buffer buf) {
		int code = buf.u8();
		if (code == 253) return buf.u16();
		if (code == 255) return buf.u8() + 253;
		if (code == 254) return buf.u8() + 506;
		return code;
	}
	/** 把三元组编码的相对坐标还原成点序列，返回消耗的字节数。 */
	private static int tripletDecode(Buffer flags, Buffer in, int nPoints, int[] pointX, int[] pointY, boolean[] onCurve) {
		int x = 0, y = 0, index = 0;
		for (var i = 0; i < nPoints; i++) {
			int flag = flags.readByte(i);
			onCurve[i] = flag >> 7 == 0;
			flag &= 0x7F;
			int nBytes = flag < 84 ? 1 : flag < 120 ? 2 : flag < 124 ? 3 : 4;
			in.need(index + nBytes);
			int dx, dy;
			if (flag < 10) {
				dx = 0;
				dy = withSign(flag, (flag & 14) << 7 | in.readByte(index));
			} else if (flag < 20) {
				dx = withSign(flag, (flag - 10 & 14) << 7 | in.readByte(index));
				dy = 0;
			} else if (flag < 84) {
				int a = flag - 20, b = in.readByte(index);
				dx = withSign(flag, 1 + (a & 0x30) + (b >> 4));
				dy = withSign(flag >> 1, 1 + ((a & 0x0C) << 2) + (b & 0x0F));
			} else if (flag < 120) {
				int a = flag - 84;
				dx = withSign(flag, 1 + (a / 12 << 8) + in.readByte(index));
				dy = withSign(flag >> 1, 1 + (a % 12 >> 2 << 8) + in.readByte(index + 1));
			} else if (flag < 124) {
				int b = in.readByte(index + 1);
				dx = withSign(flag, in.readByte(index) << 4 | b >> 4);
				dy = withSign(flag >> 1, (b & 0x0F) << 8 | in.readByte(index + 2));
			} else {
				dx = withSign(flag, in.readByte(index) << 8 | in.readByte(index + 1));
				dy = withSign(flag >> 1, in.readByte(index + 2) << 8 | in.readByte(index + 3));
			}
			index += nBytes;
			x += dx;
			y += dy;
			pointX[i] = x;
			pointY[i] = y;
		}
		return index;
	}
	/** 计算点集的包围盒写入字形头。 */
	private static void computeBbox(int nPoints, int[] pointX, int[] pointY, byte[] dst) {
		int minX = 0, minY = 0, maxX = 0, maxY = 0;
		if (nPoints > 0) {
			minX = maxX = pointX[0];
			minY = maxY = pointY[0];
			for (var i = 1; i < nPoints; i++) {
				minX = Math.min(minX, pointX[i]);
				maxX = Math.max(maxX, pointX[i]);
				minY = Math.min(minY, pointY[i]);
				maxY = Math.max(maxY, pointY[i]);
			}
		}
		var offset = 2;
		offset = store16(dst, offset, minX);
		offset = store16(dst, offset, minY);
		offset = store16(dst, offset, maxX);
		store16(dst, offset, maxY);
	}
	/** 把点序列按 TTF 的增量编码写入字形，返回字形总长。 */
	private static int storePoints(
		int nPoints,
		int[] pointX,
		int[] pointY,
		boolean[] onCurve,
		int nContours,
		int instructionLength,
		boolean hasOverlap,
		byte[] dst
	) {
		int flagOffset = 10 + 2 * nContours + 2 + instructionLength;
		int lastFlag = -1, repeatCount = 0, lastX = 0, lastY = 0, xBytes = 0, yBytes = 0;
		for (var i = 0; i < nPoints; i++) {
			int flag = onCurve[i] ? 1 : 0;
			if (hasOverlap && i == 0) flag |= 1 << 6;
			int dx = pointX[i] - lastX, dy = pointY[i] - lastY;
			if (dx == 0) flag |= 1 << 4;
			else if (dx > -256 && dx < 256) {
				flag |= 1 << 1 | (dx > 0 ? 1 << 4 : 0);
				xBytes++;
			} else xBytes += 2;
			if (dy == 0) flag |= 1 << 5;
			else if (dy > -256 && dy < 256) {
				flag |= 1 << 2 | (dy > 0 ? 1 << 5 : 0);
				yBytes++;
			} else yBytes += 2;
			if (flag == lastFlag && repeatCount != 255) {
				dst[flagOffset - 1] |= 1 << 3;
				repeatCount++;
			} else {
				if (repeatCount != 0) dst[flagOffset++] = (byte) repeatCount;
				dst[flagOffset++] = (byte) flag;
				repeatCount = 0;
			}
			lastX = pointX[i];
			lastY = pointY[i];
			lastFlag = flag;
		}
		if (repeatCount != 0) dst[flagOffset++] = (byte) repeatCount;
		int xyBytes = xBytes + yBytes;
		if (flagOffset + xyBytes > dst.length) throw new Woff2Exception("Glyph data exceeds buffer");
		int xOffset = flagOffset, yOffset = flagOffset + xBytes;
		lastX = lastY = 0;
		for (var i = 0; i < nPoints; i++) {
			int dx = pointX[i] - lastX;
			if (dx != 0) if (dx > -256 && dx < 256) dst[xOffset++] = (byte) Math.abs(dx);
			else xOffset = store16(dst, xOffset, dx);
			lastX += dx;
			int dy = pointY[i] - lastY;
			if (dy != 0) if (dy > -256 && dy < 256) dst[yOffset++] = (byte) Math.abs(dy);
			else yOffset = store16(dst, yOffset, dy);
			lastY += dy;
		}
		return yOffset;
	}
	private static int withSign(int flag, int base) {
		return (flag & 1) != 0 ? base : -base;
	}
	public static class Woff2Exception extends RuntimeException {
		Woff2Exception(String message) {
			super(message);
		}
	}
	/** 带越界检查的大端字节读取；{@code readByte} 按相对下标读取而不推进位置，供前瞻若干字节。 */
	private static class Buffer {
		private final byte[] data;
		private final int end;
		private int pos;
		Buffer(byte[] data) {
			this(data, 0, data.length);
		}
		Buffer(byte[] data, int offset, int length) {
			this.data = data;
			pos = offset;
			end = offset + length;
		}
		int offset() {
			return pos;
		}
		int remaining() {
			return end - pos;
		}
		int readByte(int index) {
			need(index + 1);
			return data[pos + index] & 0xFF;
		}
		void need(int length) {
			if (length < 0 || length > end - pos) throw new Woff2Exception("Data out of bounds");
		}
		int u8() {
			need(1);
			return data[pos++] & 0xFF;
		}
		int s16() {
			return (short) u16();
		}
		int u16() {
			need(2);
			int value = (data[pos] & 0xFF) << 8 | data[pos + 1] & 0xFF;
			pos += 2;
			return value;
		}
		int u32() {
			need(4);
			int value = (data[pos] & 0xFF) << 24 | (data[pos + 1] & 0xFF) << 16 | (data[pos + 2] & 0xFF) << 8 | data[pos + 3] & 0xFF;
			pos += 4;
			return value;
		}
		void skip(int length) {
			need(length);
			pos += length;
		}
		byte[] read(int length) {
			var result = new byte[length];
			read(result, 0, length);
			return result;
		}
		void read(byte[] dst, int offset, int length) {
			need(length);
			System.arraycopy(data, pos, dst, offset, length);
			pos += length;
		}
	}
	/** 可增长且支持回填的输出缓冲。 */
	private static class Out {
		byte[] data;
		private int size;
		Out(int capacity) {
			data = new byte[Math.max(capacity, 256)];
		}
		int size() {
			return size;
		}
		void write(byte[] src, int offset, int length) {
			ensure(size + length);
			System.arraycopy(src, offset, data, size, length);
			size += length;
		}
		private void ensure(int capacity) {
			if (capacity > data.length) data = Arrays.copyOf(data, Math.max(capacity, data.length * 2));
		}
		void writeAt(byte[] src, int at) {
			ensure(at + src.length);
			System.arraycopy(src, 0, data, at, src.length);
		}
	}
	private static class Table {
		int tag, flags, srcOffset, srcLength, transformLength, dstOffset, dstLength;
		/** 该表在输出头部里的条目位置。 */
		int entryOffset;
		int checksum;
	}
	private static class Woff2Header {
		int flavor, numTables, compressedOffset, compressedLength, uncompressedSize;
		Table[] tables = new Table[0], sortedTables = new Table[0];
	}
	private static class Woff2FontInfo {
		int numGlyphs, indexFormat, numHmetrics;
		int[] xMins = new int[0];
	}
	private static class CompositeInfo {
		int size;
		boolean haveInstructions;
	}
}
