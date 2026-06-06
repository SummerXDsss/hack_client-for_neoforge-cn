package com.wurstclient_v7.feature;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleRegistry {
	private static final String LANG_PREFIX = "wurst_client_on_neoforge.module.";

	public static final Map<String, Module> MODULES = new LinkedHashMap<>();

	static {
		register("kill_aura", "kill_aura_toggle", KillAura::isEnabled, KillAura::toggle);
		register("auto_attack", "autoattack_toggle", AutoAttack::isEnabled, AutoAttack::toggle);
		register("speed_hack", "speedhack_toggle", SpeedHack::isEnabled, SpeedHack::toggle);
		register("full_bright", "fullbright_toggle", FullBright::isEnabled, FullBright::toggle);
		register("flight", "flight_toggle", Flight::isEnabled, Flight::toggle);
		register("no_fall", "nofall_toggle", NoFall::isEnabled, NoFall::toggle);
		register("xray", "xray_toggle", XRay::isEnabled, XRay::toggle);
		register("jetpack", "jetpack_toggle", Jetpack::isEnabled, Jetpack::toggle);
		register("nuker", "nuker_toggle", Nuker::isEnabled, Nuker::toggle);
		register("spider", "spider_toggle", Spider::isEnabled, Spider::toggle);
		register("esp", "esp_toggle", ESP::isEnabled, ESP::toggle);
		register("tracers", "tracers_toggle", Tracers::isEnabled, Tracers::toggle);
		register("andromeda_bridge", "andromeda_toggle", AndromedaBridge::isEnabled, AndromedaBridge::toggle);
		register("safe_walk", "safewalk_toggle", SafeWalk::isEnabled, SafeWalk::toggle);
		register("god_mode", "godmode_toggle", GodMode::isEnabled, GodMode::toggle);
		register("freecam", "freecam_toggle", Freecam::isEnabled, Freecam::toggle);
		register("jesus", "jesus_toggle", JesusHack::isEnabled, JesusHack::toggle);
		register("glide", "glide_toggle", Glide::isEnabled, Glide::toggle);
		register("air_place", "airplace_toggle", AirPlace::isEnabled, AirPlace::toggle);
		register("boat_fly", "boatfly_toggle", BoatFly::isEnabled, BoatFly::toggle);
	}

	private static void register(String id, String actionKey, ModuleToggle isEnabled, ModuleAction toggle) {
		MODULES.put(id, new Module(id, actionKey, LANG_PREFIX + id, isEnabled, toggle));
	}

	public interface ModuleToggle {
		boolean isEnabled();
	}

	public interface ModuleAction {
		void toggle();
	}

	public static final class Module {
		private final String id;
		private final String actionKey;
		private final String translationKey;
		private final ModuleToggle isEnabled;
		private final ModuleAction toggle;

		private Module(String id, String actionKey, String translationKey, ModuleToggle isEnabled, ModuleAction toggle) {
			this.id = id;
			this.actionKey = actionKey;
			this.translationKey = translationKey;
			this.isEnabled = isEnabled;
			this.toggle = toggle;
		}

		public String id() {
			return id;
		}

		public String actionKey() {
			return actionKey;
		}

		public String translationKey() {
			return translationKey;
		}

		public boolean isEnabled() {
			return isEnabled.isEnabled();
		}

		public void toggle() {
			toggle.toggle();
		}
	}
}
