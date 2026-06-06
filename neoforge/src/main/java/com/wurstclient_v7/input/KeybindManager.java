package com.wurstclient_v7.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

public final class KeybindManager {
    private static final String LANG_PREFIX = "wurst_client_on_neoforge.key.";

    private static final class Keybind {
        public final boolean isMouse;

        public final int key;

        public final int modifiers;

        public Keybind(boolean isMouse, int key, int modifiers) {
            this.isMouse = isMouse;
            this.key = key;
            this.modifiers = modifiers;
        }

        public String toString() {
            // Format: TYPE : MODIFIERS : KEY
            return (this.isMouse ? "M" : "K") + ":" + this.modifiers + ":" + this.key;
        }

        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Keybind kb = (Keybind)o;
            return (this.isMouse == kb.isMouse && this.key == kb.key && this.modifiers == kb.modifiers);
        }

        public int hashCode() {
            return Objects.hash(new Object[] { Boolean.valueOf(this.isMouse), Integer.valueOf(this.key), Integer.valueOf(this.modifiers) });
        }
    }

    private static final File CONFIG_DIR = new File("config");

    private static final File CONFIG_FILE = new File(CONFIG_DIR, "hackclient-binds.properties");

    private static final Map<String, String> BINDS = new ConcurrentHashMap<>();

    static {
        BINDS.put("open_menu", (new Keybind(false, GLFW.GLFW_KEY_RIGHT_CONTROL, 0)).toString());
        BINDS.put("kill_aura_toggle", (new Keybind(false, GLFW.GLFW_KEY_K, 0)).toString());
        BINDS.put("safewalk_toggle", (new Keybind(false, GLFW.GLFW_KEY_P, 0)).toString());
        // Remove elytra_mace_toggle here for 1.20.4
        load();
    }

    public static Keybind getKeybind(String action) {
        String s = BINDS.get(action);
        if (s == null) return null;
        try {
            String[] parts = s.split(":");
            boolean isMouse = "M".equals(parts[0]);
            int mods = Integer.parseInt(parts[1]); // Modifiers second
            int key = Integer.parseInt(parts[2]);  // Key third
            return new Keybind(isMouse, key, mods);
        } catch (Exception e) {
            // Fallback for old integer-only format
            try {
                return new Keybind(false, Integer.parseInt(s), 0);
            } catch (Exception e2) { return null; }
        }
    }

    public static void setKey(String action, int key, int modifiers, boolean isMouse) {
        BINDS.put(action, (new Keybind(isMouse, key, modifiers)).toString());
        save();
    }

    public static void clear(String action) {
        BINDS.remove(action);
        save();
    }

    public static String keyName(int key) {
        if (key == -1)
            return tr("none");
        String specialName = specialKeyName(key);
        if (specialName != null)
            return specialName;
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty())
            return name.toUpperCase();
        return I18n.get(LANG_PREFIX + "keyboard_unknown", key);
    }

    public static String keybindLabel(Keybind kb) {
        if (kb == null)
            return tr("none");
        StringBuilder sb = new StringBuilder();
        if ((kb.modifiers & 0x2) != 0)
            sb.append(tr("modifier.ctrl"));
        if ((kb.modifiers & 0x1) != 0)
            sb.append(tr("modifier.shift"));
        if ((kb.modifiers & 0x4) != 0)
            sb.append(tr("modifier.alt"));
        if ((kb.modifiers & 0x8) != 0)
            sb.append(tr("modifier.super"));
        if (kb.isMouse) {
            switch (kb.key) {
                case 0:
                    sb.append(tr("mouse.left"));
                    return sb.toString();
                case 1:
                    sb.append(tr("mouse.right"));
                    return sb.toString();
                case 2:
                    sb.append(tr("mouse.middle"));
                    return sb.toString();
            }
            sb.append(I18n.get(LANG_PREFIX + "mouse_unknown", kb.key));
        } else {
            sb.append(keyName(kb.key));
        }
        return sb.toString();
    }

    public static String getLabel(String action) {
        return keybindLabel(getKeybind(action));
    }

    public static boolean isPressed(long window, String action) {
        boolean glfwDown;
        Keybind kb = getKeybind(action);
        if (kb == null)
            return false;
        if ((kb.modifiers & 0x2) != 0) {
            boolean ctrl = (InputConstants.isKeyDown(window, 341) || InputConstants.isKeyDown(window, 345));
            if (!ctrl)
                return false;
        }
        if ((kb.modifiers & 0x1) != 0) {
            boolean sh = (InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344));
            if (!sh)
                return false;
        }
        if ((kb.modifiers & 0x4) != 0) {
            boolean alt = (InputConstants.isKeyDown(window, 342) || InputConstants.isKeyDown(window, 346));
            if (!alt)
                return false;
        }
        if (kb.isMouse) {
            glfwDown = (GLFW.glfwGetMouseButton(window, kb.key) == 1);
        } else {
            glfwDown = (GLFW.glfwGetKey(window, kb.key) == 1);
        }
        boolean icDown = InputConstants.isKeyDown(window, kb.key);
        if (glfwDown != icDown)
            System.out.println("[KEYDEBUG] discrepancy for " + keybindLabel(kb) + ": InputConstants=" + icDown + " GLFW=" + glfwDown + " key=" + kb.key);
        return glfwDown;
    }

    private static void load() {
        if (!CONFIG_FILE.exists())
            return;
        Properties p = new Properties();
        try {
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            try {
                p.load(in);
                for (String name : p.stringPropertyNames())
                    BINDS.put(name, p.getProperty(name));
                in.close();
            } catch (Throwable throwable) {
                try {
                    in.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (IOException e) {
            System.err.println("Failed to load keybinds: " + e.getMessage());
        }
    }

    private static void save() {
        CONFIG_DIR.mkdirs();
        Properties p = new Properties();
        for (Map.Entry<String, String> e : BINDS.entrySet())
            p.setProperty(e.getKey(), e.getValue());
        try {
            FileOutputStream out = new FileOutputStream(CONFIG_FILE);
            try {
                p.store(out, "Hack client keybinds");
                out.close();
            } catch (Throwable throwable) {
                try {
                    out.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (IOException e) {
            System.err.println("Failed to save keybinds: " + e.getMessage());
        }
    }

    private static String specialKeyName(int key) {
        switch (key) {
            case 345:
                return tr("right_control");
            case 341:
                return tr("left_control");
            case 344:
                return tr("right_shift");
            case 340:
                return tr("left_shift");
            case 346:
                return tr("right_alt");
            case 342:
                return tr("left_alt");
            case 347:
                return tr("right_super");
            case 343:
                return tr("left_super");
            default:
                return null;
        }
    }

    private static String tr(String key) {
        return I18n.get(LANG_PREFIX + key);
    }
}
