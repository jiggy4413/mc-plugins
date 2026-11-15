package io.github.jiggy4413.prisoncore.warps;

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

/** Handles clicks in the warps menu and opens it for /warps (and a bare /warp). */
public final class WarpsListener implements Listener {

    private final Warps warps;

    public WarpsListener(Warps warps) {
        this.warps = warps;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof WarpsMenu)) {
            return;
        }
        // nothing can be taken out of or put into the menu
        event.setCancelled(true);
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ((WarpsMenu) holder).handleClick(event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WarpsMenu) {
            event.setCancelled(true);
        }
    }

    /** Runs before Essentials sees the command, so the menu opens instead of the text list. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (message.length() < 2) {
            return;
        }
        String[] parts = message.substring(1).split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1); // "/essentials:warps" counts as "/warps"
        }

        boolean open = false;
        List<String> commands = warps.config().getStringList("open-commands");
        for (String c : commands) {
            String name = c.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.equals(label)) {
                open = parts.length == 1; // "/warps 2" (page) still goes to Essentials
                break;
            }
        }
        if (!open && parts.length == 1 && (label.equals("warp") || label.equals("ewarp"))
                && warps.config().getBoolean("open-on-bare-warp", true)) {
            open = true;
        }
        if (!open) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("prisoncore.warps")) {
            return; // let Essentials handle it as usual
        }
        event.setCancelled(true);
        warps.open(player);
    }
}
