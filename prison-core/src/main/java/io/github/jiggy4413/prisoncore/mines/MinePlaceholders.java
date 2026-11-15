package io.github.jiggy4413.prisoncore.mines;

import io.github.jiggy4413.prisoncore.util.Text;
import java.lang.reflect.Method;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlaceholderAPI placeholders for mines, for scoreboards, holograms and tab:
 *
 *   %prisoncore_mine_a_mined%      42        (percent mined)
 *   %prisoncore_mine_a_remaining%  58        (percent left)
 *   %prisoncore_mine_a_until%      33        (percent left to mine before it resets)
 *   %prisoncore_mine_a_resetat%    75
 *   %prisoncore_mine_a_status%     Ready / Resetting / Resetting soon
 *   %prisoncore_mine_a_timer%      4m 12s    (until the timer reset, or "Off")
 *   %prisoncore_mine_a_bar%        a colored progress bar of how mined it is
 *   %prisoncore_mine_a_name%       the mine's display name
 *
 * Swap "mine_a" for "current_mine" to get the mine the player is standing at.
 * Only created when PlaceholderAPI is installed.
 */
public final class MinePlaceholders extends PlaceholderExpansion {

    private static final String[] FIELDS = MineText.FIELDS;

    private final JavaPlugin plugin;
    private final MineManager manager;

    public MinePlaceholders(JavaPlugin plugin, MineManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "prisoncore";
    }

    @Override
    public String getAuthor() {
        return "jiggy4413";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;   // survive /papi reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /** Called through PapiHook, so callers never need PlaceholderAPI's classes. */
    boolean registerNow() {
        return register();
    }

    /**
     * Unregisters on plugin disable, so a live PrisonCore update can register fresh placeholders.
     * PlaceholderAPI has renamed this over its versions, so try each known way.
     */
    public void tryUnregister() {
        try {
            Method m = getClass().getMethod("unregister");
            m.invoke(this);
            return;
        } catch (Exception ignored) {
            // older PlaceholderAPI
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = api.getMethod("unregisterPlaceholderHook", String.class);
            m.invoke(null, getIdentifier());
        } catch (Exception e) {
            plugin.getLogger().warning("Couldn't unregister the prisoncore placeholders: " + e);
        }
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        String p = params.toLowerCase(Locale.ROOT);
        if (p.equals("current_mine")) {
            Mine m = current(player);
            return m == null ? "" : Text.color(m.display());
        }
        for (String field : FIELDS) {
            String suffix = "_" + field;
            if (!p.endsWith(suffix)) {
                continue;
            }
            String head = p.substring(0, p.length() - suffix.length());
            Mine m;
            if (head.equals("current_mine")) {
                m = current(player);
                if (m == null) {
                    return "";
                }
            } else if (head.startsWith("mine_")) {
                m = manager.get(head.substring("mine_".length()));
                if (m == null) {
                    return null;
                }
            } else {
                return null;
            }
            return value(m, field);
        }
        return null;
    }

    private Mine current(OfflinePlayer player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Player online = player.getPlayer();
        return online == null ? null : manager.mineNear(online.getLocation());
    }

    private String value(Mine m, String field) {
        return MineText.value(manager, m, field);
    }
}
