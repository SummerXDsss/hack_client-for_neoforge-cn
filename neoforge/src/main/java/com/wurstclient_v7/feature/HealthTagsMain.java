package com.wurstclient_v7.feature;

import com.wurstclient_v7.config.NeoForgeConfigManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(HealthTagsMain.MODID)
public final class HealthTagsMain {
	public static final String MODID = "wurst_client_on_neoforge";

	// === Public API for Click GUI ===
	private static boolean enabled = false;

	public static void toggle() {
		enabled = !enabled;
		NeoForgeConfigManager.setBoolean("healthtags.enabled", enabled);
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public HealthTagsMain(IEventBus modEventBus) {
	}
}
