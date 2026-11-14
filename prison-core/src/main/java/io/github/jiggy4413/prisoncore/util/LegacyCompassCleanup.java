package io.github.jiggy4413.prisoncore.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The server menu compass from PrisonCore 1.5.x is shelved. This removes any of those compasses
 * players still carry, since nothing protects or uses them anymore.
 */
public final class LegacyCompassCleanup implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public LegacyCompassCleanup(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "server_menu_compass");
    }

    public void cleanOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clean(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (e.getPlayer().isOnline()) {
                    clean(e.getPlayer());
                }
            }
        }, 20L);
    }

    private void clean(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.hasItemMeta()
                    && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                inv.setItem(i, null);
            }
        }
    }
}
