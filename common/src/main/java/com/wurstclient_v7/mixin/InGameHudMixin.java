package com.wurstclient_v7.mixin;

import com.wurstclient_v7.config.ConfigManager;
import com.wurstclient_v7.feature.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.*;

@Mixin(value = Gui.class, remap = false)
public class InGameHudMixin {
    private static final String WATERMARK_KEY = "wurst_client_on_neoforge.hud.watermark";
    private static final String ENABLED_MODULE_KEY = "wurst_client_on_neoforge.hud.enabled_module";
    private static final String INPUT_FORWARD_KEY = "wurst_client_on_neoforge.input.forward";
    private static final String INPUT_LEFT_KEY = "wurst_client_on_neoforge.input.left";
    private static final String INPUT_BACK_KEY = "wurst_client_on_neoforge.input.back";
    private static final String INPUT_RIGHT_KEY = "wurst_client_on_neoforge.input.right";
    private static final String INPUT_JUMP_KEY = "wurst_client_on_neoforge.input.jump";
    private static final String INPUT_MOUSE_LEFT_KEY = "wurst_client_on_neoforge.input.mouse_left";
    private static final String INPUT_MOUSE_RIGHT_KEY = "wurst_client_on_neoforge.input.mouse_right";

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        int x = 5;
        int y = 5;
        int color = 0xFF00FF00;

        // Draw Watermark
        guiGraphics.drawString(mc.font, I18n.get(WATERMARK_KEY), x, y, 0xFFFFFFFF, true);
        y += 12;

        // Input HUD
        long window = mc.getWindow().getWindow();
        int pressedColor = 0x59FF0000; // translucent red
        int idleColor = 0x59808080;    // translucent gray

        int baseX = 10;
        int baseY = 80;
        int boxW = 40;
        int boxH = 20;
        int spacing = 5;

// W on top
        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_FORWARD_KEY), GLFW_KEY_W,
                baseX + boxW + spacing, baseY, false, pressedColor, idleColor);

// A S D row
        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_LEFT_KEY), GLFW_KEY_A,
                baseX, baseY + boxH + spacing, false, pressedColor, idleColor);

        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_BACK_KEY), GLFW_KEY_S,
                baseX + boxW + spacing, baseY + boxH + spacing, false, pressedColor, idleColor);

        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_RIGHT_KEY), GLFW_KEY_D,
                baseX + (boxW + spacing) * 2, baseY + boxH + spacing, false, pressedColor, idleColor);

// SPACE below
        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_JUMP_KEY), GLFW_KEY_SPACE,
                baseX, baseY + (boxH + spacing) * 2, false, pressedColor, idleColor);

        //MOUSE BUTTONS
        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_MOUSE_LEFT_KEY), GLFW_MOUSE_BUTTON_LEFT,
                baseX + (boxW + spacing) * 3, baseY, true, pressedColor, idleColor);

        drawKeyBox(guiGraphics, mc.font, window, I18n.get(INPUT_MOUSE_RIGHT_KEY), GLFW_MOUSE_BUTTON_RIGHT,
                baseX + (boxW + spacing) * 3, baseY + boxH + spacing, true, pressedColor, idleColor);

        for (ModuleRegistry.Module module : ModuleRegistry.MODULES.values()) {
            if (module.isEnabled()) {
                String moduleName = I18n.get(module.translationKey());
                guiGraphics.drawString(mc.font, I18n.get(ENABLED_MODULE_KEY, moduleName), x, y, color, true);
                y += 10;
            }
        }
    }
    private void drawKeyBox(GuiGraphics guiGraphics, Font font, long window,
                            String label, int key, int x, int y, boolean isMouse,
                            int pressedColor, int idleColor) {
        boolean pressed;
        if (isMouse) {
            pressed = glfwGetMouseButton(window, key) == GLFW_PRESS;
        } else {
            pressed = glfwGetKey(window, key) == GLFW_PRESS;
        }

        int boxColor = pressed ? pressedColor : idleColor;

        // Draw rectangle (width=40, height=20 for example)
        guiGraphics.fill(x, y, x + 40, y + 20, boxColor);

        // Draw label centered inside
        int textWidth = font.width(label);
        int textX = x + (40 - textWidth) / 2;
        int textY = y + (20 - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, textX, textY, 0xFFFFFFFF, true);
    }
}
