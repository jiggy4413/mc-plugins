package io.github.jiggy4413.prisoncore.npc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets chosen commands from Citizens NPCs through PL-Hide (npc-commands.yml).
 *
 * Citizens runs "-p" NPC commands by making the player type them, which PL-Hide blocks. When a
 * player has just clicked a Citizens NPC and one of the listed commands comes in, this cancels the
 * typed command before PL-Hide sees it and runs it with performCommand instead (the same way the
 * warps and ranks menus run commands). PL-Hide only watches typed commands, so it doesn't step in.
 * Typing the command without clicking an NPC is untouched, so PL-Hide still blocks it.
 */
public final class NpcCommandPass implements Listener {

    private static final String FILE = "npc-commands.yml";

    private final JavaPlugin plugin;
    private final Set<String> commands = new HashSet<String>();
    private long windowMs = 1000;
    private final Map<UUID, Long> clicked = new HashMap<UUID, Long>();

    public NpcCommandPass(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reads npc-commands.yml. Returns a problem message, or null. */
    public String load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        commands.clear();
        for (String c : yml.getStringList("commands")) {
            String label = label(c);
            if (!label.isEmpty()) {
                commands.add(label);
            }
        }
        windowMs = Math.max(50, yml.getLong("window-ms", 1000));
        return null;
    }

    // ------------------------------------------------------------------ remember NPC clicks

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            clicked.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && isNpc(event.getEntity())) {
            clicked.put(event.getDamager().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clicked.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ let the command through

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (commands.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Long at = clicked.get(player.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > windowMs) {
            return;
        }
        String message = event.getMessage();
        String line = message.startsWith("/") ? message.substring(1) : message;
        if (!commands.contains(label(line))) {
            return;
        }
        clicked.remove(player.getUniqueId()); // one command per click
        event.setCancelled(true);
        player.performCommand(line);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isNpc(Entity entity) {
        return entity != null && entity.hasMetadata("NPC"); // Citizens marks its NPCs this way
    }

    /** First word, lower case, without a "plugin:" prefix and leading slash. */
    private static String label(String line) {
        String s = line.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        if (space >= 0) {
            s = s.substring(0, space);
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }
}
