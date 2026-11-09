package io.github.jiggy4413.prisoncore.menu;

import io.github.jiggy4413.prisoncore.PrisonCore;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.InventoryHolder;

/** Handles clicks in the menu and takes over /ranks and /prestiges from Rankup. */
public final class MenuListener implements Listener {

    private final PrisonCore plugin;

    public MenuListener(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof RanksMenu)) {
            return;
        }
        // nothing can be taken out of or put into the menu
        event.setCancelled(true);
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ((RanksMenu) holder).handleClick(event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RanksMenu) {
            event.setCancelled(true);
        }
    }

    /** Runs before Rankup sees the command, so our menu opens instead of Rankup's. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        // "/rankup:ranks" should count as "/ranks"
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }

        RanksMenu.Tab tab;
        if (matches(label, "open-commands.ranks")) {
            tab = RanksMenu.Tab.RANKS;
        } else if (matches(label, "open-commands.prestiges")) {
            tab = RanksMenu.Tab.PRESTIGES;
        } else {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("prisoncore.ranks")) {
            return; // let Rankup handle it as usual
        }
        if (!plugin.isRanksMenuAvailable()) {
            return; // Rankup missing or broken: don't hijack its commands
        }
        event.setCancelled(true);
        plugin.openRanksMenu(player, tab);
    }

    private boolean matches(String label, String path) {
        List<String> commands = plugin.getMenuConfig().getStringList(path);
        for (String command : commands) {
            String c = command.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) {
                c = c.substring(1);
            }
            if (c.equals(label)) {
                return true;
            }
        }
        return false;
    }
}
