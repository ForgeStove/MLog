package io.github.forgestove.mlog.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.gui.LogicFont;
import io.github.forgestove.mlog.core.net.UnitOwnerRequestPayload;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 准星指向被处理器接管的单位时，在单位旁画指向处理器的箭头，并给处理器加橙描边。 */
public class UnitMarker {
	/** 三角外接圆半径与灰色描边的外扩量，单位为格。 */
	private static final float ARROW_RADIUS = 0.4F, ARROW_OUTLINE = 0.2F;
	/** 控制来源按实体 id 缓存；悬停时只查一次，之后的变化由服务端主动推送。 */
	private static final Map<Integer, BlockPos> OWNERS = new HashMap<>();
	/** 上一次 tick 所在的世界；实体 id 在不同 Level 间可能重复，切换时须清空缓存。 */
	private static @Nullable Level lastLevel;
	private static int hovered = -1;
	public static void onClientTick(Post ignoredEvent) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			OWNERS.clear();
			hovered = -1;
		}
		var level = mc.level;
		if (level == null || mc.player == null || LinkMode.active()) {
			hovered = -1;
			return;
		}
		// 只有 Mob 可能被接管，看向其它实体时不查询
		var id = mc.crosshairPickEntity instanceof Mob mob ? mob.getId() : -1;
		if (id == hovered) return;
		hovered = id;
		// 首次看到该实体问一次：补上追踪开始前就已受控、不会再有推送的情况
		if (id >= 0) PacketDistributor.sendToServer(new UnitOwnerRequestPayload(id));
	}
	/** 服务端推送或应答：更新缓存，空值表示控制已解除。 */
	public static void applyOwner(int entityId, @Nullable BlockPos pos) {
		if (pos == null) OWNERS.remove(entityId);
		else OWNERS.put(entityId, pos);
	}
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != Stage.AFTER_WEATHER || LinkMode.active()) return;
		var pos = OWNERS.get(hovered);
		if (pos == null) return;
		var level = mc.level;
		if (level == null) return;
		var entity = level.getEntity(hovered);
		if (entity == null) return;
		var pose = event.getPoseStack();
		var camera = event.getCamera();
		var cam = camera.getPosition();
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		var shape = level.getBlockState(pos).getShape(level, pos);
		// 与链接范围同款：外层深色描边、内层主色细线
		OutlineRenderer.renderOutlinedBox(
			pose,
			cam,
			shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos),
			LogicFont.outlineColor(ACCENT),
			ACCENT
		);
		// 位置按 partial tick 插值，否则标记会比单位模型慢一 tick，看起来追不上
		var partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		var shift = entity.getPosition(partial).subtract(entity.position());
		var from = entity.getBoundingBox().getCenter().add(shift);
		var to = Vec3.atCenterOf(pos);
		var delta = to.subtract(from);
		// 箭头中心在距单位 hitSize * 2 处，近到箭头会越过处理器时不画
		var length = Math.max(entity.getBbWidth() * 2F, 1F);
		if (delta.length() > length) renderArrow(pose, camera, cam, from.add(delta.normalize().scale(length)), delta.normalize());
		pose.popPose();
	}
	/** 正三角按相机基向量摆放：一个顶点指向处理器，整体投影到屏幕平面。 */
	private static void renderArrow(PoseStack pose, Camera camera, Vec3 cam, Vec3 center, Vec3 direction) {
		var look = new Vec3(camera.getLookVector());
		var up = new Vec3(camera.getUpVector());
		var screenDir = direction.subtract(look.scale(direction.dot(look)));
		screenDir = screenDir.lengthSqr() < 1E-6 ? up : screenDir.normalize();
		var side = look.cross(screenDir).normalize();
		// 一个顶点在前，另两个在后方 ±120 度
		var back = screenDir.scale(-0.5F);
		var across = side.scale(0.8660254F);
		var outline = ARROW_RADIUS + ARROW_OUTLINE;
		OutlineRenderer.renderTriangle(
			pose.last(),
			cam,
			GRAY,
			center.add(screenDir.scale(outline)),
			center.add(back.add(across).scale(outline)),
			center.add(back.subtract(across).scale(outline))
		);
		OutlineRenderer.renderTriangle(
			pose.last(),
			cam,
			ACCENT,
			center.add(screenDir.scale(ARROW_RADIUS)),
			center.add(back.add(across).scale(ARROW_RADIUS)),
			center.add(back.subtract(across).scale(ARROW_RADIUS))
		);
	}
}
