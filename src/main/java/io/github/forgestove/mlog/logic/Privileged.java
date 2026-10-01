package io.github.forgestove.mlog.logic;
public interface Privileged {
	default boolean privileged() {
		return false;
	}
}
