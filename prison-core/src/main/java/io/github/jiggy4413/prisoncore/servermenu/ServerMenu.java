package io.github.jiggy4413.prisoncore.servermenu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** One open copy of a menu from server-menu.yml, for one player. */
public final class ServerMenu implements InventoryHolder {

    final String id;
    Inventory inventory;
    /** slot -> what clicking it does */
    final Map<Integer, List<String>> actions = new HashMap<Integer, List<String>>();
    /** slot -> message for a locked item */
    final Map<Integer, String> lockedMessages = new HashMap<Integer, String>();
    final Map<Integer, Boolean> closeOnClick = new HashMap<Integer, Boolean>();

    ServerMenu(String id) {
        this.id = id;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
