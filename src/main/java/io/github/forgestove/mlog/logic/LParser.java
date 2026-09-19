package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LStatements.*;

import java.util.*;
/** 逻辑代码的词法与语法分析，移植自 Mindustry 的 {@code LParser}。 */
public class LParser {
	private static final int MAX_TOKENS = 16, MAX_JUMPS = 500;
	private final List<LStatement> statements = new ArrayList<>();
	private final List<JumpIndex> jumps = new ArrayList<>();
	private final Map<String, Integer> jumpLocations = new LinkedHashMap<>();
	private final String[] tokens = new String[MAX_TOKENS];
	private final char[] chars;
	private int pos;
	private int line;
	public LParser(String text) {
		chars = text.toCharArray();
		// 统一换行符，多出来的 \n 无害
		for (var i = 0; i < chars.length; i++) if (chars[i] == '\r') chars[i] = '\n';
	}
	/** @return 解析出的语句序列，跳转标签已换成行号。 */
	public List<LStatement> parse() {
		while (pos < chars.length && line < LExecutor.MAX_INSTRUCTIONS) switch (chars[pos]) {
			case '\n', ';', ' ' -> pos++;
			default -> statement();
		}
		for (var jump : jumps) {
			var location = jumpLocations.get(jump.location);
			if (location == null)
				throw error("Undefined jump location: \"" + jump.location + "\". Make sure the jump label exists and is typed correctly.");
			jump.statement.destIndex = location;
		}
		// 回填编辑态的跳转目标引用，界面靠它画连线
		for (var statement : statements)
			if (statement instanceof JumpStatement jump && jump.destIndex >= 0 && jump.destIndex < statements.size())
				jump.dest = statements.get(jump.destIndex);
		return statements;
	}
	/** 解析一行语句。 */
	void statement() {
		var expectNext = false;
		var tok = 0;
		while (pos < chars.length) {
			var c = chars[pos];
			if (tok >= MAX_TOKENS) throw error("Line too long; may only contain " + MAX_TOKENS + " tokens");
			if (c == '\n' || c == ';') break;
			if (expectNext && c != ' ' && c != '#' && c != '\t') throw error("Expected space after string/token.");
			expectNext = false;
			if (c == '#') {
				comment();
				break;
			}
			if (c == '"') {
				tokens[tok++] = string();
				expectNext = true;
			} else if (c != ' ' && c != '\t') {
				tokens[tok++] = token();
				expectNext = true;
			} else pos++;
		}
		if (tok == 0) return;
		// 跳转标签以冒号结尾，单独占一行，不产生语句
		if (tok == 1 && tokens[0].charAt(tokens[0].length() - 1) == ':') {
			if (jumpLocations.size() >= MAX_JUMPS) throw error("Too many jump locations. Max jumps: " + MAX_JUMPS);
			var label = tokens[0].substring(0, tokens[0].length() - 1);
			if (jumpLocations.containsKey(label)) throw error("Jump label already defined: \"" + label + "\".");
			jumpLocations.put(label, line);
			return;
		}
		var wasJump = false;
		String jumpLocation = null;
		// 跳转目标先占位，解析完标签后再回填
		if (tokens[0].equals("jump") && tok > 1 && !isInt(tokens[1])) {
			wasJump = true;
			jumpLocation = tokens[1];
			tokens[1] = "-1";
		}
		LStatement statement;
		try {
			statement = Statements.parse(tokens, tok);
		} catch (Exception e) {
			statement = new InvalidStatement();
		}
		if (statement instanceof JumpStatement jump && wasJump) jumps.add(new JumpIndex(jump, jumpLocation));
		statements.add(statement);
		line++;
	}
	/** 返回异常而不是直接抛出，调用处写 {@code throw error(...)} 让编译器能推导控制流。 */
	public static RuntimeException error(String message) {
		return new RuntimeException("Invalid code. " + message);
	}
	/** 读到行尾，换行符本身也吃掉。 */
	void comment() {
		// 先走到换行符上，再把它本身也吃掉
		while (pos < chars.length && chars[pos] != '\n') pos++;
		if (pos < chars.length) pos++;
	}
	/** 读一个字符串字面量，含引号，转义序列原样保留以便往返解析。 */
	String string() {
		var from = pos;
		while (++pos < chars.length) {
			var c = chars[pos];
			if (c == '\\' && pos + 1 < chars.length && (chars[pos + 1] == 'n' || chars[pos + 1] == '"' || chars[pos + 1] == '\\')) {
				pos++;
				continue;
			}
			if (c == '\\' && pos + 1 < chars.length && chars[pos + 1] == 'u') {
				if (pos + 5 >= chars.length) throw error("Invalid \\u escape; expected 4 hex digits.");
				for (var i = pos + 2; i <= pos + 5; i++)
					if (Character.digit(chars[i], 16) == -1) throw error("Invalid \\u escape; expected 4 hex digits.");
				pos += 5;
				continue;
			}
			if (c == '\n') throw error("Missing closing quote \" before end of line.");
			if (c == '"') break;
		}
		if (pos >= chars.length || chars[pos] != '"') throw error("Missing closing quote \" before end of file.");
		pos++;
		return new String(chars, from, pos - from);
	}
	/** 读一个不含空白的 token。 */
	String token() {
		var from = pos;
		while (pos < chars.length) {
			var c = chars[pos];
			if (c == '\n' || c == ' ' || c == '#' || c == '\t' || c == ';' || c == '"') break;
			pos++;
		}
		return new String(chars, from, pos - from);
	}
	/**
	 * @return token 是不是行号，带负号也算。
	 * 	<p>断了目标的 {@code jump} 写出来是 {@code jump -1 ...}，不认负号的话它会被当成跳转标签，
	 * 	然后因为找不到这个标签而报错。
	 */
	private static boolean isInt(String s) {
		if (s.isEmpty()) return false;
		var from = s.charAt(0) == '-' ? 1 : 0;
		if (from == s.length()) return false;
		for (var i = from; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
		return true;
	}
	/** 用标签的跳转，解析完后回填行号。 */
	private record JumpIndex(JumpStatement statement, String location) {}
}
