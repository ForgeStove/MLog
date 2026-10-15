package io.github.forgestove.mlog.client.gui.logic;
import static io.github.forgestove.mlog.client.gui.LogicColors.TEXT;
/** 一条 {@code jump} 连线，两端是语句卡片，{@code lane} 决定它向右伸多远。 */
public final class JumpCurve {
	public final StatementCard from, to;
	public int lane;
	/** 每帧向 {@link JumpCurveLayout#reach} 的目标值平滑逼近。 */
	public float reach;
	/** 以下字段是 {@link JumpCurveLayout} 的临时工作区：语句序号区间、是否向上跳、算出的层号。 */
	int begin, end, height;
	boolean flipped, done;
	public JumpCurve(StatementCard from, StatementCard to) {
		this.from = from;
		this.to = to;
	}
	public int color() {
		return TEXT;
	}
}
