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
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
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
        checkLater(e.getPlayer(), 100L);  // and again for plugins that load data late
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        ensureLater(e.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ensureLater(e.getPlayer(), 2L);   // per-world inventory plugins swap items on world change
        checkLater(e.getPlayer(), 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) {
            checkLater((Player) e.getPlayer(), 1L);   // other plugins' GUIs (sell, trade...) may have taken it
        }
    }

    private void checkLater(final Player p, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, new CheckTask(menus, p), ticks);
    }

    /** Named (not anonymous) so it's loaded up front and survives a live jar swap. */
    static final class CheckTask implements Runnable {
        private final ServerMenus menus;
        private final Player p;

        CheckTask(ServerMenus menus, Player p) {
            this.menus = menus;
            this.p = p;
        }

        @Override
        public void run() {
            if (p.isOnline()) {
                menus.quickCheck(p);
            }
        }
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

    private final java.util.Map<java.util.UUID, Long> lastOpen = new java.util.HashMap<java.util.UUID, Long>();

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !menus.isCompass(e.getItem())) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // let doors, chests, buttons and NPC-less blocks work normally while holding the compass
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !e.getPlayer().isSneaking()) {
            return;
        }
        e.setCancelled(true);
        if (!e.getPlayer().hasPermission("prisoncore.menu")) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(e.getPlayer().getUniqueId());
        if (last != null && now - last < 250) {
            return;   // right-clicking a block fires twice
        }
        lastOpen.put(e.getPlayer().getUniqueId(), now);
        openLater(e.getPlayer(), menus.mainMenu());
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (menus.isCompass(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (menus.isCompass(e.getMainHandItem()) || menus.isCompass(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            if (menus.isCompass(it.next())) {
                it.remove();   // given back on respawn
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (menus.isCompass(e.getPlayerItem())) {
            e.setCancelled(true);
        }
    }

    private final java.util.Map<java.util.UUID, Long> lastClick = new java.util.HashMap<java.util.UUID, Long>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();
        InventoryHolder top = e.getView().getTopInventory().getHolder();

        // clicks inside a server menu
        if (top instanceof ServerMenu) {
            e.setCancelled(true);
            ClickType type = e.getClick();
            boolean normalClick = type == ClickType.LEFT || type == ClickType.RIGHT
                    || type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
            long now = System.currentTimeMillis();
            Long last = lastClick.get(p.getUniqueId());
            if (!normalClick || (last != null && now - last < 250)) {
                return;   // ignore double-clicks, number keys, drops and click spam
            }
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                lastClick.put(p.getUniqueId(), now);
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

    /** The compass can never be used in crafting (e.g. recipe-book autofill into a map). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (menus.isCompass(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (menus.isCompass(it)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
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
