package io.github.forgestove.mlog.content.processor.behaviour;
public record BehaviourType<T extends ProcessorBehaviour>(String name) {
	@Override
	public boolean equals(Object other) {
		return this == other;
	}
	@Override
	public int hashCode() {
		return System.identityHashCode(this) * 31 * 493286711;
	}
}
