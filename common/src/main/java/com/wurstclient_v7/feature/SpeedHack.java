package com.wurstclient_v7.feature;

import com.wurstclient_v7.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SpeedHack {

    private static boolean enabled = ConfigManager.getBoolean("speed.enabled", false);

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        ConfigManager.setBoolean("speed.enabled", enabled);
        System.out.println("SpeedHack: " + (enabled ? "ON" : "OFF"));
    }

    public static double getMultiplier() {
        return ConfigManager.getDouble("speed.multiplier", 1.5);
    }

    public static void setMultiplier(double value) {
        ConfigManager.setDouble("speed.multiplier", Math.max(1.0, Math.min(5.0, value)));
    }

    public static void onClientTick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = mc.player;

        // Check if the player is pressing WASD
        boolean isPressingMove = player.xxa != 0 || player.zza != 0;

        if (isPressingMove && player.onGround()) {
            double multiplier = getMultiplier();

            // Get current movement
            Vec3 motion = player.getDeltaMovement();

            // Only apply if the current horizontal velocity is low (starting to move)
            // This prevents the "Exponential Acceleration" bug.
            double horizontalMag = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            if (horizontalMag < 0.3) { // 0.3 is slightly higher than normal walking
                player.setDeltaMovement(
                        motion.x * multiplier,
                        motion.y,
                        motion.z * multiplier
                );
            }
        }
    }
}
