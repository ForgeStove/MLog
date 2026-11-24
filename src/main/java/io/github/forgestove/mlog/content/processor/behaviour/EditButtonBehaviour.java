package io.github.forgestove.mlog.content.processor.behaviour;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.content.processor.behaviour.ValueBoxTransform.Faced;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public class EditButtonBehaviour extends ProcessorBehaviour implements ValueSettingsBehaviour {
	public static final BehaviourType<EditButtonBehaviour> TYPE = new BehaviourType<>("edit_button");
	public EditButtonBehaviour(AbstractProcessorBlockEntity blockEntity) {
		super(blockEntity);
	}
	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}
	@Override
	public ValueBoxTransform getSlotPositioning() {
		return Faced.INSTANCE;
	}
	@Override
	public boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit) {
		return getSlotPositioning().testHit(level, pos, state, hit);
	}
	@Override
	public Component getLabel() {
		return Component.translatable("mlog.gui.edit");
	}
}
