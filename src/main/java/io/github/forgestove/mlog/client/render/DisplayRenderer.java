package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;

/**
 * 逻辑显示单元的渲染。
 * <p>每格各自绘制本格那一块画布，不设主方块：整组形状变化或仅加载部分时，各格仍可独立绘制。
 * <p>画布以成员格为身份：一份画布登记在它服务的每一格上，取画布只能经由它自己的格，故朝向、所在平面与相连
 * 关系都由格本身保证，不必再进键，也不存在跨帧猜前身这一步。
 * <p>边框由模型画在屏幕面那一层：画布只铺瓷砖上透明的那几块，两者零重叠且严格共面，不需要深度先后。
 */
@OnlyIn(Dist.CLIENT)
public class DisplayRenderer implements BlockEntityRenderer<TileLogicDisplayBlockEntity> {
	/** 同时保留的画布上限，超出后淘汰最久未渲染的一份并归还显存；淘汰即内容丢失。 */
	private static final int MAX_BUFFERS = 1024;
	/** 方块中心到面中心的距离。 */
	private static final float HALF = 0.5F;
	/** 画布的身份：维度与成员格坐标，朝向与所在平面都由这一格自己定。 */
	private record Cell(ResourceKey<Level> dimension, BlockPos pos) {}
	/**
	 * 一份画布及其服务的格。
	 * <p>{@code cells} 恒为某一组的成员格，故其有效性归结为「这些格是否仍同属一组」；
	 * {@code origin} 与 {@code height} 用于折算内容位移。
	 */
	private record Canvas(
		ResourceKey<Level> dimension,
		FrontAndTop orientation,
		DisplayBuffer buffer,
		BlockPos origin,
		int height,
		List<BlockPos> cells
	) {}
	/** 旧画布的内容在新画布上要平移的像素数。 */
	private record Shift(int x, int y) {}
	/** 成员格 → 画布：一份画布在它服务的每一格上各登记一次，一格同时只属于一份画布。 */
	private static final Map<Cell, DisplayBuffer> CELLS = new HashMap<>();
	/** 画布表，按最近使用排序；格上的登记反过来指向这里的一份。 */
	private static final Map<DisplayBuffer, Canvas> CANVASES = new LinkedHashMap<>(16, 0.75F, true);
	/**
	 * 一个 tick 内的组缓存。
	 * <p>世界每 tick 才变一次，而每格每帧都要用到所属组：不缓存则每格每帧重做一次整组漫开，
	 * 代价随格数呈平方增长。
	 */
	private static final Map<BlockPos, DisplayGroup> GROUPS = new HashMap<>();
	/** 上一次 tick 的世界，用于识别切世界。 */
	private static @Nullable Level lastLevel;
	@Override
	public void render(TileLogicDisplayBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		DisplayBuffer.releaseRetired();
		var level = be.getLevel();
		if (level == null) return;
		var pos = be.getBlockPos();
		// 界面预览会临时把候选状态写入方块实体再还原，此时读到的并非世界中的那一份
		if (be.getBlockState() != level.getBlockState(pos)) return;
		var orientation = be.getBlockState().getValue(TileLogicDisplayBlock.ORIENTATION);
		var u = DisplayGroup.right(orientation);
		var v = DisplayGroup.down(orientation);
		var group = group(level, pos);
		// 组不完整（超出格数上限、或邻格所在区块未加载）时不绘制：拿被截断的组算外接框会让内容整体错位
		if (!group.complete()) return;
		var x = DisplayGroup.offset(group.origin(), pos, u);
		var y = DisplayGroup.offset(group.origin(), pos, v);
		// 本格的瓷砖决定哪些位置让给边框
		var tile = DisplayGroup.tile(DisplayGroup.connections(level, pos, orientation));
		// 取画布会切换离屏目标，须在绘制之前完成
		var canvas = buffer(level, group, orientation, u, v);
		var renderType = canvas.renderType();
		var face = new Face(u, v, orientation.front().getNormal());
		screen(buffers.getBuffer(renderType), pose, face, group, x, y, tile);
		// 该批次不在固定缓冲表内，须立即提交，否则会绘于半透明方块之上
		if (buffers instanceof BufferSource source) source.endBatch(renderType);
	}
	/**
	 * 取本组共用的画布。
	 * <p>由成员格反查：登记只可能指向「曾把这一格算作成员」的那份画布，故查到的那份必然属于同一块屏，
	 * 不必再比朝向与平面。两块屏刚并成一组时会同时查到两份，保留先查到的一份，另一份按位移搬入后摘除。
	 */
	private static DisplayBuffer buffer(Level level, DisplayGroup group, FrontAndTop orientation, Direction u, Direction v) {
		var cells = cells(group, u, v);
		var found = found(level, cells);
		var kept = own(found, group, cells, orientation, u, v);
		// 其余登记在同一批格上的画布：确为本组前身的按位移搬入内容，随后丢弃其中已无格在它名下的
		var others = new ArrayList<>(found);
		if (kept != null) others.remove(kept);
		// 朝向与格表均未变时登记与内容仍然有效，无须每格每帧重做
		if (kept == null || kept.orientation() != orientation || !kept.cells().equals(cells)) {
			if (kept == null) kept = create(level, orientation, group, cells);
			else {
				// 角落未变而尺寸变化时就地改建：换一份新缓冲会使本帧已提交的批次引用到已销毁的纹理
				if (!kept.buffer().matches(group.canvasWidth(), group.canvasHeight())) {
					var shift = shift(group, kept, u, v);
					kept.buffer().resize(group.canvasWidth(), group.canvasHeight(), shift.x(), shift.y());
				}
				// 被接管的画布各按位移整块搬进来：两者的格不相交，不会互相覆盖；非本组前身或朝向不同则无从对位
				for (var canvas : others) {
					if (canvas.orientation() != orientation || !within(group, canvas.cells(), u, v)) continue;
					var shift = shift(group, canvas, u, v);
					kept.buffer().copyFrom(canvas.buffer(), shift.x(), shift.y());
				}
				kept = new Canvas(level.dimension(), orientation, kept.buffer(), group.origin(), group.height(), cells);
			}
			bind(kept);
		}
		for (var canvas : others) if (!registered(canvas)) discard(canvas);
		evict();
		var commands = drain(level, group, u, v);
		if (!commands.isEmpty()) kept.buffer().apply(commands);
		return kept.buffer();
	}
	/** @return 本组这些格上登记着的画布，按扫描序去重；空表示本组还没有画布。 */
	private static List<Canvas> found(Level level, List<BlockPos> cells) {
		var found = new ArrayList<Canvas>();
		for (var cell : cells) {
			var owner = CELLS.get(new Cell(level.dimension(), cell));
			if (owner == null) continue;
			var canvas = CANVASES.get(owner);
			if (canvas != null && !found.contains(canvas)) found.add(canvas);
		}
		return found;
	}
	/** @return 应当留住的那份：格表与本组一致的优先（转动屏幕时格表不变），否则取第一份同朝向且格全在本组内的。 */
	private static @Nullable Canvas own(
		List<Canvas> found,
		DisplayGroup group,
		List<BlockPos> cells,
		FrontAndTop orientation,
		Direction u,
		Direction v
	) {
		for (var canvas : found) if (canvas.cells().equals(cells)) return canvas;
		for (var canvas : found) if (canvas.orientation() == orientation && within(group, canvas.cells(), u, v)) return canvas;
		return null;
	}
	/**
	 * @return 这些格是否都在本组内；共面时投影是恒等映射，故等价于「都是本组的成员格」。
	 * <p>认领画布必须过这一关：格只属于一个组、两组的格集不相交，故一份画布只可能被一个组取到。
	 */
	private static boolean within(DisplayGroup group, List<BlockPos> cells, Direction u, Direction v) {
		for (var cell : cells)
			if (!group.contains(DisplayGroup.offset(group.origin(), cell, u), DisplayGroup.offset(group.origin(), cell, v))) return false;
		return true;
	}
	/** @return 这份画布是否还有格在它名下；没有即已无组可服务。 */
	private static boolean registered(Canvas canvas) {
		for (var cell : canvas.cells()) if (CELLS.get(new Cell(canvas.dimension(), cell)) == canvas.buffer()) return true;
		return false;
	}
	/** @return 一份空白画布，尺寸取本组外接框去掉边框那一圈。 */
	private static Canvas create(Level level, FrontAndTop orientation, DisplayGroup group, List<BlockPos> cells) {
		var buffer = new DisplayBuffer(group.canvasWidth(), group.canvasHeight());
		return new Canvas(level.dimension(), orientation, buffer, group.origin(), group.height(), cells);
	}
	/** @return 旧画布内容落入新画布所需的像素位移；画布的 y 与 {@code v} 反向，故须计入高度差 */
	private static Shift shift(DisplayGroup group, Canvas previous, Direction u, Direction v) {
		return new Shift(
			DisplayGroup.offset(group.origin(), previous.origin(), u) * DisplayGroup.RESOLUTION,
			(group.height() - previous.height() - DisplayGroup.offset(group.origin(), previous.origin(), v)) * DisplayGroup.RESOLUTION
		);
	}
	/** @return 本组各成员格的坐标。 */
	private static List<BlockPos> cells(DisplayGroup group, Direction u, Direction v) {
		var cells = new ArrayList<BlockPos>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y)) cells.add(group.at(u, v, x, y));
		return cells;
	}
	/** 把画布登记到它服务的每一格上，并把它推到画布表最近使用的一端。 */
	private static void bind(Canvas canvas) {
		for (var cell : canvas.cells()) CELLS.put(new Cell(canvas.dimension(), cell), canvas.buffer());
		CANVASES.put(canvas.buffer(), canvas);
	}
	/** 摘掉一份画布占用的全部格登记，不释放显存。 */
	private static void unregister(Canvas canvas) {
		for (var cell : canvas.cells()) CELLS.remove(new Cell(canvas.dimension(), cell), canvas.buffer());
		CANVASES.remove(canvas.buffer());
	}
	/** 丢弃一份画布：格登记先摘，显存延后一帧归还，避开仍在绘制中的批次。 */
	private static void discard(Canvas canvas) {
		unregister(canvas);
		canvas.buffer().retire();
	}
	/** 淘汰最久未渲染的画布直至回到上限之内；淘汰的是久未渲染的一份，直接归还显存是安全的。 */
	private static void evict() {
		var iterator = CANVASES.entrySet().iterator();
		while (CANVASES.size() > MAX_BUFFERS && iterator.hasNext()) {
			var canvas = iterator.next().getValue();
			for (var cell : canvas.cells()) CELLS.remove(new Cell(canvas.dimension(), cell), canvas.buffer());
			iterator.remove();
			canvas.buffer().close();
		}
	}
	/** @return 本格所在的那一组，同一 tick 内按坐标缓存。 */
	private static DisplayGroup group(Level level, BlockPos pos) {
		return GROUPS.computeIfAbsent(pos, at -> DisplayGroup.of(level, at));
	}
	/**
	 * 按世界现状丢弃已不成组的画布。
	 * <p>判据只有两条：每一格是否仍是显示单元（被拆、被换成其他方块即丢弃，同既有「拆即清屏」约定），
	 * 以及是否还有格在它名下（格已被其他画布接管即丢弃）；两条判据均不看朝向——转动屏幕只换面内摆向，
	 * 内容随屏幕一起转，也不依赖任何回调的时序。
	 * <p>格所在区块未加载时无从核对世界现状，宁可留着也不据一个看不到的世界丢弃画布。
	 */
	public static void sweep(Level level) {
		for (var canvas : List.copyOf(CANVASES.values())) {
			if (!canvas.dimension().equals(level.dimension())) continue;
			var cells = canvas.cells();
			if (unloaded(level, cells)) continue;
			if (registered(canvas) && intact(level, cells)) continue;
			discard(canvas);
		}
	}
	/** @return 这些格里有没有所在区块尚未加载的；未加载即无从核对世界现状。 */
	private static boolean unloaded(Level level, List<BlockPos> cells) {
		for (var cell : cells) if (!level.isLoaded(cell)) return true;
		return false;
	}
	/** @return 这些格是否都仍是显示单元。 */
	private static boolean intact(Level level, List<BlockPos> cells) {
		for (var cell : cells) if (!(level.getBlockState(cell).getBlock() instanceof TileLogicDisplayBlock)) return false;
		return true;
	}
	/** 某一格被真正拆除时丢弃它所属的画布，整块屏幕随之清空；区块卸载不调。 */
	public static void invalidate(Level level, BlockPos pos) {
		var owner = CELLS.get(new Cell(level.dimension(), pos));
		if (owner == null) return;
		var canvas = CANVASES.get(owner);
		if (canvas != null) discard(canvas);
	}
	/** 丢弃全部画布；切世界时调用，新世界同坐标的屏不该先显示旧世界的画面。 */
	public static void discardAll() {
		for (var canvas : CANVASES.values()) canvas.buffer().close();
		CANVASES.clear();
		CELLS.clear();
	}
	/** 每 tick 丢弃组缓存、按世界现状清扫画布；切世界时连同画布一并丢弃。 */
	public static void onClientTick(Post ignoredEvent) {
		GROUPS.clear();
		var level = mc.level;
		if (level == null) return;
		if (level != lastLevel) {
			lastLevel = level;
			discardAll();
			return;
		}
		sweep(level);
	}
	/**
	 * 本格那一块画布。
	 * <p>局部坐标以本格面心为原点，一格为一单位。画布只铺瓷砖上透明的那几块，边框占着的让开，
	 * 两者零重叠，不必与深度打交道。
	 * <p>画布的 y 朝上、贴图的 v 也朝上，两者同向；但这里的 {@code y} 是自内容上沿往下数的行号，
	 * 故 UV 按「离上沿多远」折算。
	 */
	private static void screen(VertexConsumer consumer, PoseStack pose, Face face, DisplayGroup group, int x, int y, int tile) {
		var resolution = DisplayGroup.RESOLUTION;
		var frame = DisplayGroup.FRAME;
		var canvasWidth = group.canvasWidth();
		var canvasHeight = group.canvasHeight();
		float[] cut = {0F, TileLogicDisplayBlock.INSET, 1F - TileLogicDisplayBlock.INSET, 1F};
		for (var i = 0; i < 3; i++)
			for (var j = 0; j < 3; j++) {
				if (DisplayGroup.framed(tile, i, j)) continue;
				var minU = -HALF + cut[i];
				var minV = -HALF + cut[j];
				var maxU = -HALF + cut[i + 1];
				var maxV = -HALF + cut[j + 1];
				// 画布内容按外接框去掉边框那一圈铺开，采到的正是瓷砖上透明那几块
				var u0 = (x * resolution + cut[i] * resolution - frame) / (float) canvasWidth;
				var u1 = (x * resolution + cut[i + 1] * resolution - frame) / (float) canvasWidth;
				var v1 = 1F - (y * resolution + cut[j] * resolution - frame) / (float) canvasHeight;
				var v0 = 1F - (y * resolution + cut[j + 1] * resolution - frame) / (float) canvasHeight;
				face.vertex(consumer, pose, minU, minV, u0, v1);
				face.vertex(consumer, pose, maxU, minV, u1, v1);
				face.vertex(consumer, pose, maxU, maxV, u1, v0);
				face.vertex(consumer, pose, minU, maxV, u0, v0);
			}
	}
	/** @return 取走组内各成员格积压的命令，按扫描顺序拼接。 */
	private static List<DrawCmd> drain(Level level, DisplayGroup group, Direction u, Direction v) {
		var commands = new ArrayList<DrawCmd>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y) && level.getBlockEntity(group.at(u, v, x, y)) instanceof TileLogicDisplayBlockEntity be)
					commands.addAll(be.drain());
		return commands;
	}
	/** 本格朝向面的坐标系：{@code u} 为面内的右、{@code v} 为面内的下、{@code normal} 为外法向。 */
	private record Face(Direction u, Direction v, Vec3i normal) {
		/** 顶点色与亮度均须给出，供光影包的兜底 program 读取。 */
		private void vertex(VertexConsumer consumer, PoseStack pose, float du, float dv, float textureU, float textureV) {
			// 画布与边框严格共面：沿法向偏移一点就会在斜视时露出视差缝，而两者零重叠本就无须深度差
			var x = HALF + normal.getX() * HALF + u.getStepX() * du + v.getStepX() * dv;
			var y = HALF + normal.getY() * HALF + u.getStepY() * du + v.getStepY() * dv;
			var z = HALF + normal.getZ() * HALF + u.getStepZ() * du + v.getStepZ() * dv;
			consumer.addVertex(pose.last(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(textureU, textureV)
				.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
		}
	}
}
