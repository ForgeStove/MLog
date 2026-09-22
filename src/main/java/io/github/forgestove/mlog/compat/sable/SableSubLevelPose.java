package io.github.forgestove.mlog.compat.sable;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
/**
 * 将子层级的位姿压入渲染姿势。
 * <p>压入后顶点按 plot 坐标给出并减去返回的相机位置：plot 位于千万格量级，直接交给 float 会损失一两格精度。
 * <p>{@code PoseStack} 属于客户端，故本类仅在客户端加载；companion 未安装时全部退化为恒等返回。
 */
@OnlyIn(Dist.CLIENT)
public final class SableSubLevelPose {
	/**
	 * @param camera 世界坐标下的相机位置
	 * @return 顶点须减去的相机位置（plot 坐标）；位置不在子层级内时为 {@code null}，此时以世界相机绘制且无须 {@code popPose}
	 */
	public static @Nullable Vec3 push(PoseStack pose, Vec3 pos, Vec3 camera) {
		if (!MLogMods.sableCompanion.isLoaded()) return null;
		return Impl.push(pose, pos, camera);
	}
	/** @return 子层级内的坐标对应的世界坐标；位置不在子层级内时原样返回。 */
	public static Vec3 toWorld(Vec3 pos) {
		if (!MLogMods.sableCompanion.isLoaded()) return pos;
		return Impl.toWorld(pos);
	}
	private static final class Impl {
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
			// 相机在 plot 内的位置。顶点与旋转中心均以此为基准，位姿换算结果方落在该坐标系内
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
