package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LVarIO.EntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/** 变量值的类型：编号随存档落盘，重排枚举不得改变既有取值；内存方块按它选择落盘形式，变量表按它着色并显示类型名。 */
public enum VarType {
	NULL(0, "null"),
	NUMBER(1, "number"),
	STRING(2, "string"),
	BLOCK(3, "block"),
	ITEM(4, "item"),
	FLUID(5, "fluid"),
	ENTITY_TYPE(6, "unit"),
	ENTITY(7, "unit"),
	BLOCK_POS(8, "building"),
	LINK(9, "link"),
	ENUM(10, "enum"),
	LIST(11, "object"),
	/** 认不出的第三方对象：没有存档形式，编号不会写进存档。 */
	OBJECT(12, "object");
	private final byte id;
	/** 界面上的类型名，不做本地化。 */
	private final String display;
	VarType(int id, String display) {
		this.id = (byte) id;
		this.display = display;
	}
	/** 字符串按其指向的内容归类，没有存档形式的第三方对象归为 {@link #OBJECT}。 */
	public static VarType of(@Nullable Object value) {
		return switch (value) {
			case Number ignored -> NUMBER;
			// @物品名 / @方块名 会汇编成字符串常量，按名字指向的内容归类
			case String text -> kindOf(text);
			case Block ignored -> BLOCK;
			case Item ignored -> ITEM;
			case Fluid ignored -> FLUID;
			case EntityType<?> ignored -> ENTITY_TYPE;
			case Entity ignored -> ENTITY;
			case EntityRef ignored -> ENTITY;
			case BlockEntity ignored -> BLOCK_POS;
			case BlockPos ignored -> BLOCK_POS;
			case LogicLink ignored -> LINK;
			case Enum<?> ignored -> ENUM;
			case List<?> ignored -> LIST;
			// 空值写进存档再读取仍是空值，认不出的对象没有可写的存档形式
			case null -> NULL;
			default -> OBJECT;
		};
	}
	private static VarType kindOf(String name) {
		var content = MLogSenseables.contentType(name);
		return content == null ? STRING : content;
	}
	public static VarType byId(byte id) {
		for (var type : values()) if (type.id == id) return type;
		return NULL;
	}
	public byte id() {
		return id;
	}
	public String display() {
		return display;
	}
}
