package io.github.forgestove.mlog.compat.sable;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
/** 顶点按 plot 坐标给出并须减去返回的相机位置；plot 在千万格量级，交给 float 会损失精度。 */
public class SableSubLevelPose {
	/** 位置不在子层级内时返回 {@code null}（顶点以世界相机绘制），此时无须 {@code popPose}。 */
	public static @Nullable Vec3 push(PoseStack pose, Vec3 pos, Vec3 camera) {
		if (!MLogMods.sableCompanion.isLoaded()) return null;
		return Impl.push(pose, pos, camera);
	}
	public static Vec3 toWorld(Vec3 pos) {
		if (!MLogMods.sableCompanion.isLoaded()) return pos;
		return Impl.toWorld(pos);
	}
	private static class Impl {
		static Vec3 toWorld(Vec3 pos) {
			var subLevel = SableCompanion.INSTANCE.getContainingClient(pos);
			return subLevel == null ? pos : subLevel.renderPose().transformPosition(pos);
		}
		static @Nullable Vec3 push(PoseStack pose, Vec3 pos, Vec3 camera) {
			var subLevel = SableCompanion.INSTANCE.getContainingClient(pos);
			if (subLevel == null) return null;
			// 须用不带参的重载：其插值部分 tick 与方块渲染一致
			var source = subLevel.renderPose();
			var position = source.position();
			var rotationPoint = source.rotationPoint();
			var scale = source.scale();
			// 相机在 plot 内的位置；顶点与旋转中心均以此为基准，位姿换算结果方落在该坐标系内
			var local = source.transformPositionInverse(camera);
			pose.pushPose();
			pose.translate(position.x() - camera.x, position.y() - camera.y, position.z() - camera.z);
			pose.mulPose(new Quaternionf(source.orientation()));
			pose.translate(local.x - rotationPoint.x(), local.y - rotationPoint.y(), local.z - rotationPoint.z());
			pose.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
			return local;
		}
	}
}
