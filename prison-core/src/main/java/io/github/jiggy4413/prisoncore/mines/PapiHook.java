package io.github.jiggy4413.prisoncore.mines;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps PlaceholderAPI's classes out of PrisonCore's main class, so PrisonCore still loads on
 * servers without PlaceholderAPI. Only call these when PlaceholderAPI is enabled.
 */
public final class PapiHook {

    private PapiHook() {
    }

    /** Returns the registered expansion (as Object), or null if registering failed. */
    public static Object register(JavaPlugin plugin, MineManager manager) {
        MinePlaceholders expansion = new MinePlaceholders(plugin, manager);
        return expansion.registerNow() ? expansion : null;
    }

    public static void unregister(Object expansion) {
        ((MinePlaceholders) expansion).tryUnregister();
    }
}
