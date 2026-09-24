package io.github.forgestove.mlog.client.event;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
public class ResourcePackHandler {
	public static void register(AddPackFindersEvent event) {
		if (event.getPackType() == PackType.CLIENT_RESOURCES) event.addPackFinders(
			getMLogRes("resourcepacks/mlog_pbr"),
			PackType.CLIENT_RESOURCES,
			Component.literal("MLog PBR"),
			PackSource.BUILT_IN,
			false,
			Position.TOP
		);
	}
}
