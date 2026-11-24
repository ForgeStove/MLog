package io.github.forgestove.mlog.client.gui.valuebox;
import net.minecraft.resources.ResourceLocation;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 四角框样式，取自 Create 的 {@code AllIcons.VALUE_BOX_HOVER_*}。
 * 编号为角标跨度，单位为图案像素；贴图 16 像素见方，故 1 像素 = {@code 1/16} 格。
 */
public enum ValueBoxHover {
	PX_4("value_box_hover_4px"),
	PX_6("value_box_hover_6px"),
	PX_8("value_box_hover_8px"),
	;
	private final ResourceLocation texture;
	ValueBoxHover(String name) {
		texture = getMLogRes("textures/gui/" + name + ".png");
	}
	public ResourceLocation texture() {
		return texture;
	}
}
