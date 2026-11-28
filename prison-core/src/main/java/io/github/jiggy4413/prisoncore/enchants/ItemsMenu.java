package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.cosmetics.Cosmetics;
import io.github.jiggy4413.prisoncore.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /prisoncore items: an admin menu with every special item from items.yml (custom armor sets, frost
 * weapons, crate items) and every cosmetic. Click an item to get a fresh one (built like a crate reward,
 * so random enchant levels roll each time); click a cosmetic to unlock it for yourself.
 */
public final class ItemsMenu implements Listener {

    private static final int PAGE = 45;
    private static final int PREV = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;

    private final SpecialItems items;
    private final Cosmetics cosmetics;

    public ItemsMenu(SpecialItems items, Cosmetics cosmetics) {
        this.items = items;
        this.cosmetics = cosmetics;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        int page;
        /** what each slot gives: "item:<name>" or "cosmetic:<id>" */
        final Map<Integer, String> slots = new java.util.HashMap<Integer, String>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void command(CommandSender sender) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open this menu.");
            return;
        }
        open((Player) sender, 0);
    }

    private List<String> entries() {
        List<String> out = new ArrayList<String>();
        for (String n : items.names()) {
            out.add("item:" + n);
        }
        for (String id : cosmetics.ids()) {
            out.add("cosmetic:" + id);
        }
        return out;
    }

    private void open(Player p, int page) {
        List<String> all = entries();
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Text.color("&8Custom Items &7(" + (page + 1) + "/" + pages + ")"));
        h.inventory = inv;
        List<String> problems = new ArrayList<String>();
        for (int i = 0; i < PAGE && page * PAGE + i < all.size(); i++) {
            String e = all.get(page * PAGE + i);
            ItemStack icon;
            if (e.startsWith("item:")) {
                icon = items.build(e.substring(5), p, problems);
                if (icon == null) {
                    continue;
                }
                addHint(icon, "&eClick to get one");
            } else {
                icon = cosmetics.menuIcon(e.substring(9));
                if (icon == null) {
                    continue;
                }
                addHint(icon, "&eClick to unlock for yourself");
            }
            inv.setItem(i, icon);
            h.slots.put(i, e);
        }
        ItemStack glass = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = PAGE; i < 54; i++) {
            inv.setItem(i, glass);
        }
        if (page > 0) {
            inv.setItem(PREV, named(Material.ARROW, "&ePrevious page"));
        }
        if (page < pages - 1) {
            inv.setItem(NEXT, named(Material.ARROW, "&eNext page"));
        }
        inv.setItem(INFO, named(Material.BOOK, "&6" + all.size() + " custom items",
                "&7Everything in items.yml and cosmetics.yml.", "&7Items are built fresh on every click."));
        p.openInventory(inv);
    }

    private static void addHint(ItemStack it, String hint) {
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return;
        }
        List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
        lore.add("");
        lore.add(Text.color(hint));
        m.setLore(lore);
        it.setItemMeta(m);
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.color(name));
        if (lore.length > 0) {
            List<String> l = new ArrayList<String>();
            for (String s : lore) {
                l.add(Text.color(s));
            }
            m.setLore(l);
        }
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        Holder h = (Holder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot == PREV) {
            open(p, h.page - 1);
            return;
        }
        if (slot == NEXT) {
            open(p, h.page + 1);
            return;
        }
        String e = h.slots.get(slot);
        if (e == null || !p.hasPermission("prisoncore.admin")) {
            return;
        }
        if (e.startsWith("item:")) {
            List<String> problems = new ArrayList<String>();
            ItemStack it = items.build(e.substring(5), p, problems);
            if (it == null) {
                return;
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            if (!left.isEmpty()) {
                p.sendMessage(Text.color("&cYour inventory is full."));
                return;
            }
            for (String problem : problems) {
                p.sendMessage(Text.color("&e" + problem));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        } else {
            cosmetics.unlock(p, e.substring(9));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
