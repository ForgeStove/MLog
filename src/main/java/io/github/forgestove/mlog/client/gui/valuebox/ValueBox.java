package io.github.forgestove.mlog.client.gui.valuebox;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.forgestove.mlog.content.processor.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
public abstract class ValueBox {
	protected final Level level;
	protected final BlockPos pos;
	protected final BlockState state;
	protected final Component label;
	protected ValueBoxTransform transform;
	protected boolean passive;
	protected int overrideColor = 0xFFFFFFFF;
	protected ValueBoxHover outline = ValueBoxHover.PX_4;
	protected ValueBox(Level level, BlockPos pos, Component label, ValueBoxTransform transform) {
		this.level = level;
		this.pos = pos;
		state = level.getBlockState(pos);
		this.label = label;
		this.transform = transform;
	}
	public ValueBox transform(ValueBoxTransform transform) {
		this.transform = transform;
		return this;
	}
	public ValueBox wideOutline() {
		outline = ValueBoxHover.PX_6;
		return this;
	}
	public ValueBox passive(boolean passive) {
		this.passive = passive;
		return this;
	}
	public ValueBox withColor(int color) {
		overrideColor = color;
		return this;
	}
	public Component label() {
		return label;
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
	protected void renderOutline(PoseStack pose) {
		ValueBoxRenderer.renderSprite(pose, overrideColor, outline);
	}
	protected void renderContents(PoseStack pose) {}
}
