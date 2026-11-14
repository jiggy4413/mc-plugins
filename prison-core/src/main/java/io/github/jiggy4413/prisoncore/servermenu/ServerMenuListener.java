package io.github.jiggy4413.prisoncore.servermenu;

import java.util.Iterator;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps the menu compass in its slot and handles clicks in server menus. */
public final class ServerMenuListener implements Listener {

    private final JavaPlugin plugin;
    private final ServerMenus menus;

    public ServerMenuListener(JavaPlugin plugin, ServerMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    // =================================================================== giving the compass

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        ensureLater(e.getPlayer(), 2L);   // after inventory plugins load the player's items
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        ensureLater(e.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ensureLater(e.getPlayer(), 2L);   // per-world inventory plugins swap items on world change
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        ensureLater(e.getPlayer(), 1L);
    }

    private void ensureLater(final Player p, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    menus.ensureCompass(p);
                }
            }
        }, ticks);
    }

    // =================================================================== using the compass

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !menus.isCompass(e.getItem())) {
            return;
        }
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            openLater(e.getPlayer(), menus.mainMenu());
        }
    }

    private void openLater(final Player p, final String menu) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    menus.open(p, menu);
                }
            }
        });
    }

    // =================================================================== keeping it in place

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (menus.isCompass(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (menus.isCompass(e.getMainHandItem()) || menus.isCompass(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            if (menus.isCompass(it.next())) {
                it.remove();   // given back on respawn
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame) {
            ItemStack hand = e.getHand() == EquipmentSlot.HAND
                    ? e.getPlayer().getInventory().getItemInMainHand()
                    : e.getPlayer().getInventory().getItemInOffHand();
            if (menus.isCompass(hand)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (menus.isCompass(e.getPlayerItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();
        InventoryHolder top = e.getView().getTopInventory().getHolder();

        // clicks inside a server menu
        if (top instanceof ServerMenu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                final ServerMenu menu = (ServerMenu) top;
                final int slot = e.getRawSlot();
                // opening another menu during a click event is unsafe: wait a tick
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == menu) {
                            menus.click(p, menu, slot);
                        }
                    }
                });
            }
            return;
        }

        // the compass can't be moved, swapped or stored anywhere
        boolean touchesCompass = menus.isCompass(e.getCurrentItem()) || menus.isCompass(e.getCursor());
        if (!touchesCompass && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
            touchesCompass = menus.isCompass(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (!touchesCompass && e.getClick() == ClickType.SWAP_OFFHAND) {
            touchesCompass = menus.isCompass(p.getInventory().getItemInOffHand());
        }
        if (!touchesCompass) {
            return;
        }
        e.setCancelled(true);
        // clicking the compass in your own inventory opens the menu
        if (menus.isCompass(e.getCurrentItem()) && e.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        p.closeInventory();
                        menus.open(p, menus.mainMenu());
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (menus.isCompass(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof ServerMenu) {
            int topSize = e.getView().getTopInventory().getSize();
            for (int raw : e.getRawSlots()) {
                if (raw < topSize) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }
}
