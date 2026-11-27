package io.github.forgestove.mlog.compat.sable;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import io.github.forgestove.mlog.core.Mods;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
public class SableSubLevelPose {
	public static @Nullable Vec3 push(PoseStack pose, Vec3 pos, Vec3 camera) {
		if (!Mods.sableCompanion.isLoaded()) return null;
		return Impl.push(pose, pos, camera);
	}
	public static Vec3 toWorld(Vec3 pos) {
		if (!Mods.sableCompanion.isLoaded()) return pos;
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
			var source = subLevel.renderPose();
			var position = source.position();
			var rotationPoint = source.rotationPoint();
			var scale = source.scale();
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
