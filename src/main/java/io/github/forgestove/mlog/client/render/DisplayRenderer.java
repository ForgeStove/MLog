package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.*;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.core.net.DisplaySyncPayload;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 每格各自绘制本格那一块画布；画布以成员格为身份登记在它服务的每一格上。 */
public class DisplayRenderer implements BlockEntityRenderer<TileLogicDisplayBlockEntity> {
	/** 同时保留的画布上限，超出后淘汰最久未渲染的一份并归还显存。 */
	private static final int MAX_BUFFERS = 1024;
	private static final float HALF = 0.5F;
	/** 成员格 → 画布：一格同时只属于一份画布。 */
	private static final Map<Cell, DisplayBuffer> CELLS = new HashMap<>();
	/** 画布表，按最近使用排序。 */
	private static final Map<DisplayBuffer, Canvas> CANVASES = new LinkedHashMap<>(16, 0.75F, true);
	/** 组渲染状态的每 tick 缓存；不缓存则每格每帧重做整组拼接。 */
	private static final Map<BlockPos, Group> GROUPS = new HashMap<>();
	private static final Set<DisplayBuffer> PAINTED = new HashSet<>();
	private static boolean renderingLevel;
	private static @Nullable Vec3 cameraPos;
	private static @Nullable Level lastLevel;
	/** 某一格被真正拆除时丢弃它所属的画布；区块卸载不调。 */
	public static void invalidate(Level level, BlockPos pos) {
		var owner = CELLS.get(new Cell(level.dimension(), pos));
		if (owner == null) return;
		var canvas = CANVASES.get(owner);
		if (canvas != null) discard(canvas);
	}
	/** 丢弃一份画布：格登记先摘，显存延后一帧归还。 */
	private static void discard(Canvas canvas) {
		unregister(canvas);
		canvas.buffer().retire();
	}
	private static void unregister(Canvas canvas) {
		for (var cell : canvas.cells()) CELLS.remove(new Cell(canvas.dimension(), cell), canvas.buffer());
		CANVASES.remove(canvas.buffer());
	}
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
	/** 丢弃全部画布；切世界时调用，否则新世界同坐标的屏会先显示旧画面。 */
	public static void discardAll() {
		for (var canvas : CANVASES.values()) canvas.buffer().close();
		CANVASES.clear();
		CELLS.clear();
		PAINTED.clear();
	}
	/** 按世界现状丢弃已不成组的画布；格所在区块未加载时保留。 */
	public static void sweep(Level level) {
		for (var canvas : List.copyOf(CANVASES.values())) {
			if (!canvas.dimension().equals(level.dimension())) continue;
			var cells = canvas.cells();
			if (unloaded(level, cells)) continue;
			if (registered(canvas) && intact(level, cells)) continue;
			discard(canvas);
		}
	}
	private static boolean unloaded(Level level, List<BlockPos> cells) {
		for (var cell : cells) if (!level.isLoaded(cell)) return true;
		return false;
	}
	private static boolean registered(Canvas canvas) {
		for (var cell : canvas.cells()) if (CELLS.get(new Cell(canvas.dimension(), cell)) == canvas.buffer()) return true;
		return false;
	}
	private static boolean intact(Level level, List<BlockPos> cells) {
		for (var cell : cells) if (!(level.getBlockState(cell).getBlock() instanceof TileLogicDisplayBlock)) return false;
		return true;
	}
	public static void onRenderLevel(RenderLevelStageEvent event) {
		var stage = event.getStage();
		if (stage == Stage.AFTER_ENTITIES) {
			PAINTED.clear();
			renderingLevel = true;
			cameraPos = event.getCamera().getPosition();
			return;
		}
		if (stage == Stage.AFTER_BLOCK_ENTITIES) {
			renderingLevel = false;
			cameraPos = null;
		}
	}
	/** 按世界状态只读绘制预览：不取画布，也不改任何登记。 */
	private static void preview(Level level, BlockPos pos, PoseStack pose, MultiBufferSource buffers) {
		var group = GROUPS.get(pos);
		if (group == null || !group.shape().complete()) return;
		var shape = group.shape();
		var x = DisplayGroup.offset(shape.origin(), pos, group.u());
		var y = DisplayGroup.offset(shape.origin(), pos, group.v());
		var face = new Face(group.u(), group.v(), group.orientation().front().getNormal());
		var renderType = group.buffer().renderType();
		var tile = DisplayGroup.tile(DisplayGroup.connections(level, pos, group.orientation()));
		screen(buffers.getBuffer(renderType), pose, face, shape, x, y, tile);
		if (buffers instanceof BufferSource source) source.endBatch(renderType);
	}
	/** 屏幕只画在正面，故只按法向剔除；格与相机不在同一坐标系时不做剔除。 */
	private static boolean visible(BlockState state, BlockPos pos) {
		if (cameraPos == null) return true;
		var delta = cameraPos.subtract(Vec3.atCenterOf(pos));
		var normal = state.getValue(TileLogicDisplayBlock.ORIENTATION).front().getNormal();
		return normal.getX() * delta.x + normal.getY() * delta.y + normal.getZ() * delta.z > 0;
	}
	private static Group group(Level level, BlockPos pos, BlockState state) {
		var orientation = state.getValue(TileLogicDisplayBlock.ORIENTATION);
		var cached = GROUPS.get(pos);
		// 朝向已变或画布被摘除时缓存失效，须重建
		if (cached != null && cached.orientation() == orientation && CANVASES.containsKey(cached.buffer())) return cached;
		var shape = DisplayGroup.of(level, pos);
		var u = DisplayGroup.right(orientation);
		var v = DisplayGroup.down(orientation);
		var cells = cells(shape, u, v);
		var group = new Group(
			shape,
			orientation,
			u,
			v,
			buffer(level, shape, orientation, u, v, cells),
			shape.cells().cardinality() == shape.width() * shape.height()
		);
		// 登记到每一成员格，此后每格每帧只查一次表
		for (var cell : cells) GROUPS.put(cell, group);
		return group;
	}
	/** 整组一格画整块：UV 覆盖画布整张，两端各内缩 {@code INSET} 让开边框。 */
	private static void whole(VertexConsumer consumer, PoseStack pose, Face face, DisplayGroup group, int x, int y) {
		var inset = TileLogicDisplayBlock.INSET;
		var minU = -x - HALF + inset;
		var maxU = group.width() - x - HALF - inset;
		var minV = -y - HALF + inset;
		var maxV = group.height() - y - HALF - inset;
		face.vertex(consumer, pose, minU, minV, 0F, 1F);
		face.vertex(consumer, pose, maxU, minV, 1F, 1F);
		face.vertex(consumer, pose, maxU, maxV, 1F, 0F);
		face.vertex(consumer, pose, minU, maxV, 0F, 0F);
	}
	/** 本格那一块画布：坐标以本格面心为原点；{@code y} 自内容上沿往下数，故 UV 按离上沿多远折算。 */
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
				// 画布内容按外接框去掉边框那一圈铺开，正对应瓷砖上透明的那几块
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
	private static List<BlockPos> cells(DisplayGroup group, Direction u, Direction v) {
		var cells = new ArrayList<BlockPos>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y)) cells.add(group.at(u, v, x, y));
		return cells;
	}
	private static DisplayBuffer buffer(
		Level level,
		DisplayGroup group,
		FrontAndTop orientation,
		Direction u,
		Direction v,
		List<BlockPos> cells
	) {
		var found = found(level, cells);
		var kept = own(found, group, cells, orientation, u, v);
		// 其余登记在同一批格上的画布：本组前身按位移搬入，已无格在它名下的丢弃
		var others = new ArrayList<>(found);
		if (kept != null) others.remove(kept);
		if (kept == null || kept.orientation() != orientation || !kept.cells().equals(cells)) {
			if (kept == null) kept = create(level, orientation, group, cells);
			else {
				// 角落未变而尺寸变化时就地改建，换新缓冲会使已提交的批次引用到已销毁的纹理
				if (!kept.buffer().matches(group.canvasWidth(), group.canvasHeight())) {
					var shift = shift(group, kept, u, v);
					kept.buffer().resize(group.canvasWidth(), group.canvasHeight(), shift.x(), shift.y());
				}
				// 被接管的画布各按位移整块搬进来，两者的格不相交；非本组前身或朝向不同则无从对位
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
	private static Canvas create(Level level, FrontAndTop orientation, DisplayGroup group, List<BlockPos> cells) {
		var buffer = new DisplayBuffer(group.canvasWidth(), group.canvasHeight());
		// 画布尚无内容，向服务端要一次，把本组各格已发出的命令重发过来
		PacketDistributor.sendToServer(new DisplaySyncPayload(cells.getFirst()));
		return new Canvas(level.dimension(), orientation, buffer, group.origin(), group.height(), cells);
	}
	/** 旧画布内容落入新画布所需的像素位移；画布的 y 与 {@code v} 反向，故须计入高度差。 */
	private static Shift shift(DisplayGroup group, Canvas previous, Direction u, Direction v) {
		return new Shift(
			DisplayGroup.offset(group.origin(), previous.origin(), u) * DisplayGroup.RESOLUTION,
			(group.height() - previous.height() - DisplayGroup.offset(group.origin(), previous.origin(), v)) * DisplayGroup.RESOLUTION
		);
	}
	/** 共面时投影是恒等映射，故等价于「都是本组的成员格」。 */
	private static boolean within(DisplayGroup group, List<BlockPos> cells, Direction u, Direction v) {
		for (var cell : cells)
			if (!group.contains(DisplayGroup.offset(group.origin(), cell, u), DisplayGroup.offset(group.origin(), cell, v))) return false;
		return true;
	}
	private static void bind(Canvas canvas) {
		for (var cell : canvas.cells()) CELLS.put(new Cell(canvas.dimension(), cell), canvas.buffer());
		CANVASES.put(canvas.buffer(), canvas);
	}
	private static void evict() {
		var iterator = CANVASES.entrySet().iterator();
		while (CANVASES.size() > MAX_BUFFERS && iterator.hasNext()) {
			var canvas = iterator.next().getValue();
			for (var cell : canvas.cells()) CELLS.remove(new Cell(canvas.dimension(), cell), canvas.buffer());
			iterator.remove();
			canvas.buffer().close();
		}
	}
	private static List<DrawCmd> drain(Level level, DisplayGroup group, Direction u, Direction v) {
		var commands = new ArrayList<DrawCmd>();
		for (var y = 0; y < group.height(); y++)
			for (var x = 0; x < group.width(); x++)
				if (group.contains(x, y) && level.getBlockEntity(group.at(u, v, x, y)) instanceof TileLogicDisplayBlockEntity be)
					commands.addAll(be.drain());
		return commands;
	}
	@Override
	public void render(
		TileLogicDisplayBlockEntity be,
		float partialTick,
		PoseStack pose,
		MultiBufferSource buffers,
		int light,
		int overlay
	) {
		DisplayBuffer.releaseRetired();
		var level = be.getLevel();
		if (level == null) return;
		var pos = be.getBlockPos();
		var state = level.getBlockState(pos);
		// 界面预览把候选状态临时写入方块实体，非当前候选朝向与真实屏无关
		if (be.getBlockState() != state) return;
		// 预览发生在方块实体渲染阶段之外，改走只读路径
		if (!renderingLevel) {
			preview(level, pos, pose, buffers);
			return;
		}
		// 背向相机或不在视锥内时整格跳过，不取画布、不排空与应用
		if (!visible(state, pos)) return;
		// 取组会一并取画布并切换离屏目标，须在绘制之前完成
		var group = group(level, pos, state);
		var shape = group.shape();
		// 组不完整（超出格数上限或邻格未加载）时不绘制，否则被截断的外接框会使内容错位
		if (!shape.complete()) return;
		var x = DisplayGroup.offset(shape.origin(), pos, group.u());
		var y = DisplayGroup.offset(shape.origin(), pos, group.v());
		var face = new Face(group.u(), group.v(), group.orientation().front().getNormal());
		var renderType = group.buffer().renderType();
		if (group.rectangular()) {
			// 矩形组整块铺开，只由本帧第一个渲染到的成员格提交顶点
			if (!PAINTED.add(group.buffer())) return;
			whole(buffers.getBuffer(renderType), pose, face, shape, x, y);
		} else {
			// 缺角形状逐格画：本格瓷砖决定哪些位置让给边框
			var tile = DisplayGroup.tile(DisplayGroup.connections(level, pos, group.orientation()));
			screen(buffers.getBuffer(renderType), pose, face, shape, x, y, tile);
		}
		// 该批次不在固定缓冲表内，须立即提交，否则会绘于半透明方块之上
		if (buffers instanceof BufferSource source) source.endBatch(renderType);
	}
	@Override
	public int getViewDistance() {
		return 512;
	}
	private record Cell(ResourceKey<Level> dimension, BlockPos pos) {}
	/** 一份画布及其服务的格：{@code cells} 恒为某一组的成员格，{@code origin} 与 {@code height} 用于折算内容位移。 */
	private record Canvas(
		ResourceKey<Level> dimension, FrontAndTop orientation, DisplayBuffer buffer, BlockPos origin, int height, List<BlockPos> cells
	) {}
	private record Shift(int x, int y) {}
	private record Group(
		DisplayGroup shape, FrontAndTop orientation, Direction u, Direction v, DisplayBuffer buffer, boolean rectangular
	) {}
	/** 本格朝向面的坐标系：{@code u} 为面内的右、{@code v} 为面内的下、{@code normal} 为外法向。 */
	private record Face(Direction u, Direction v, Vec3i normal) {
		private void vertex(VertexConsumer consumer, PoseStack pose, float du, float dv, float textureU, float textureV) {
			// 画布与边框严格共面，沿法向偏移会在斜视时露出视差缝，两者零重叠无须深度差
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
