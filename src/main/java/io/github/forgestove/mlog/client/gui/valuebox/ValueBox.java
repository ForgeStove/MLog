package io.github.forgestove.mlog.client.gui.valuebox;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.content.processor.EditButtonTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
public abstract class ValueBox {
	protected final Level level;
	protected final BlockPos pos;
	protected final BlockState state;
	protected final EditButtonTransform transform;
	protected boolean passive;
	protected int color = 0xFFFFFFFF;
	protected ValueBox(Level level, BlockPos pos, EditButtonTransform transform) {
		this.level = level;
		this.pos = pos;
		state = level.getBlockState(pos);
		this.transform = transform;
	}
	public ValueBox passive(boolean passive) {
		this.passive = passive;
		return this;
	}
	public ValueBox color(int color) {
		this.color = color;
		return this;
	}
	public void render(PoseStack pose, Vec3 camera) {
		if (state.isAir()) return;
		var center = transform.getCenter(level, pos, state);
		pose.pushPose();
		pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
		pose.mulPose(transform.getRotation(level, pos, state));
		var scale = transform.getScale();
		if (scale != 1F) pose.scale(scale, scale, scale);
		if (!passive) renderOutline(pose);
		renderContents(pose);
		pose.popPose();
	}
	protected void renderOutline(PoseStack pose) {}
	protected abstract void renderContents(PoseStack pose);
}
