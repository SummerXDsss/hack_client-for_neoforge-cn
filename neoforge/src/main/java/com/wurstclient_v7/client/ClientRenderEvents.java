package com.wurstclient_v7.client;

import com.wurstclient_v7.feature.Tracers;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber
public final class ClientRenderEvents {

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
			return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		Tracers.render(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
	}
}
