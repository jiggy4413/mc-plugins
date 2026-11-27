package io.github.jiggy4413.prisoncore.cosmetics;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cosmetics (cosmetics.yml): back blings players unlock from crates and wear from the /cosmetics menu.
 *
 * Unlocks and the worn back bling are saved on the player (persistent data), so they survive logouts and
 * restarts and can't be dropped or lost. A worn back bling is an item display entity (not saved to the
 * world) that follows the player's body every tick, showing the resource pack model behind their back.
 */
public final class Cosmetics implements Listener, CommandExecutor {

    private static final String FILE = "cosmetics.yml";
    private static final int REMOVE_SLOT = 22;

    private final JavaPlugin plugin;
    private final NamespacedKey ownedKey;
    private final NamespacedKey wornKey;
    private YamlConfiguration config = new YamlConfiguration();
    private final Map<String, ConfigurationSection> backBlings = new LinkedHashMap<String, ConfigurationSection>();
    private final Map<UUID, ItemDisplay> shown = new HashMap<UUID, ItemDisplay>();
    private final Map<UUID, String> shownId = new HashMap<UUID, String>();
    private BukkitTask task;

    public Cosmetics(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ownedKey = new NamespacedKey(plugin, "cosmetics_owned");
        this.wornKey = new NamespacedKey(plugin, "backbling");
    }

    /** Reads cosmetics.yml. Returns a problem message, or null. */
    public String load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        backBlings.clear();
        ConfigurationSection s = config.getConfigurationSection("back-blings");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c != null) {
                    backBlings.put(id.toLowerCase(Locale.ROOT), c);
                }
            }
        }
        removeAll();   // rebuilt next tick with the new settings
        return null;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAll();
    }

    // ------------------------------------------------------------------ unlocks (saved on the player)

    private Set<String> owned(Player p) {
        String s = p.getPersistentDataContainer().get(ownedKey, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<String>();
        if (s != null) {
            for (String id : s.split(",")) {
                if (!id.trim().isEmpty()) {
                    out.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private void saveOwned(Player p, Set<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        p.getPersistentDataContainer().set(ownedKey, PersistentDataType.STRING, sb.toString());
    }

    private String worn(Player p) {
        String s = p.getPersistentDataContainer().get(wornKey, PersistentDataType.STRING);
        return s == null || s.isEmpty() ? null : s;
    }

    private void wear(Player p, String id) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (id == null) {
            pdc.remove(wornKey);
        } else {
            pdc.set(wornKey, PersistentDataType.STRING, id);
        }
    }

    /** /prisoncore cosmetic unlock|lock <player> <id> (console / admins, for crate rewards). */
    public void adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 4 || !(args[1].equalsIgnoreCase("unlock") || args[1].equalsIgnoreCase("lock"))) {
            sender.sendMessage(Text.color("&cUsage: /prisoncore cosmetic <unlock|lock> <player> <id>  &7(ids: "
                    + String.join(", ", backBlings.keySet()) + ")"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Text.color("&c" + args[2] + " isn't online."));
            return;
        }
        String id = args[3].toLowerCase(Locale.ROOT);
        ConfigurationSection c = backBlings.get(id);
        if (c == null) {
            sender.sendMessage(Text.color("&cNo cosmetic called " + id + ". Ids: " + String.join(", ", backBlings.keySet())));
            return;
        }
        Set<String> ids = owned(target);
        if (args[1].equalsIgnoreCase("unlock")) {
            boolean isNew = ids.add(id);
            saveOwned(target, ids);
            if (isNew) {
                msg(target, "unlocked", c);
            }
            sender.sendMessage(Text.color("&a" + target.getName() + (isNew ? " unlocked " : " already had ") + id + "."));
        } else {
            ids.remove(id);
            saveOwned(target, ids);
            if (id.equals(worn(target))) {
                wear(target, null);
            }
            sender.sendMessage(Text.color("&7Took " + id + " from " + target.getName() + "."));
        }
    }

    private void msg(Player p, String key, ConfigurationSection c) {
        String m = config.getString("messages." + key, "");
        if (!m.isEmpty()) {
            p.sendMessage(Text.color(p, m.replace("{name}", c == null ? "" : c.getString("name", ""))));
        }
    }

    // ------------------------------------------------------------------ the back bling on the player

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String id = worn(p);
            ConfigurationSection c = id == null ? null : backBlings.get(id);
            boolean hide = c == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR
                    || p.hasPotionEffect(PotionEffectType.INVISIBILITY) || !owned(p).contains(id);
            ItemDisplay d = shown.get(p.getUniqueId());
            if (hide) {
                if (d != null) {
                    remove(p.getUniqueId());
                }
                continue;
            }
            Location at = backLocation(p);
            if (d == null || !d.isValid() || !id.equals(shownId.get(p.getUniqueId())) || d.getWorld() != p.getWorld()) {
                remove(p.getUniqueId());
                d = spawn(at, c);
                shown.put(p.getUniqueId(), d);
                shownId.put(p.getUniqueId(), id);
            } else {
                d.teleport(at);
            }
        }
        // players who left without a quit event (shouldn't happen, but never leave entities behind)
        if (!shown.isEmpty()) {
            for (UUID id : new ArrayList<UUID>(shown.keySet())) {
                if (Bukkit.getPlayer(id) == null) {
                    remove(id);
                }
            }
        }
    }

    private Location backLocation(Player p) {
        Location l = p.getLocation();
        float yaw = p.getBodyYaw();
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);          // the way the body faces
        double back = config.getDouble("back-offset", 0.32);
        double up = config.getDouble("back-height", 1.32) - (p.isSneaking() ? config.getDouble("sneak-drop", 0.18) : 0);
        Location at = l.clone().add(-fx * back, up, -fz * back);
        at.setYaw(yaw);
        at.setPitch(0);
        return at;
    }

    private ItemDisplay spawn(Location at, final ConfigurationSection c) {
        final ItemStack model = icon(c, false);
        final float size = (float) c.getDouble("size", 1.2);
        return at.getWorld().spawn(at, ItemDisplay.class, new java.util.function.Consumer<ItemDisplay>() {
            @Override
            public void accept(ItemDisplay d) {
                d.setPersistent(false);
                d.setItemStack(model);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTeleportDuration(2);                         // smooth movement between ticks
                d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(),
                        new Vector3f(size, size, size), new Quaternionf()));
                d.setViewRange(0.6f);
            }
        });
    }

    private void remove(UUID id) {
        ItemDisplay d = shown.remove(id);
        shownId.remove(id);
        if (d != null && d.isValid()) {
            d.remove();
        }
    }

    private void removeAll() {
        for (UUID id : new ArrayList<UUID>(shown.keySet())) {
            remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ /cosmetics menu

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        final List<String> slots = new ArrayList<String>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }
        open((Player) sender);
        return true;
    }

    private void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Text.color(p, config.getString("menu.title", "&8Cosmetics")));
        h.inventory = inv;
        fill(p, h);
        p.openInventory(inv);
    }

    private void fill(Player p, Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        h.slots.clear();
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
            h.slots.add(null);
        }
        Set<String> mine = owned(p);
        String wearing = worn(p);
        int[] layout = {10, 11, 12, 13, 14, 15, 16};
        int n = 0;
        for (Map.Entry<String, ConfigurationSection> e : backBlings.entrySet()) {
            if (n >= layout.length) {
                break;
            }
            String id = e.getKey();
            boolean have = mine.contains(id);
            ItemStack it = icon(e.getValue(), true);
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            if (!have) {
                lore.add(Text.color("&c&lLOCKED"));
            } else if (id.equals(wearing)) {
                lore.add(Text.color("&a&lWEARING &7(click to remove)"));
                m.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Text.color("&eClick to wear"));
            }
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(layout[n], it);
            h.slots.set(layout[n], id);
            n++;
        }
        ItemStack none = new ItemStack(Material.BARRIER);
        ItemMeta nm = none.getItemMeta();
        nm.setDisplayName(Text.color("&cRemove back bling"));
        none.setItemMeta(nm);
        inv.setItem(REMOVE_SLOT, none);
    }

    private ItemStack icon(ConfigurationSection c, boolean withText) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString(c.getString("item-model", "minecraft:paper").toLowerCase(Locale.ROOT));
        if (model != null) {
            m.setItemModel(model);
        }
        if (withText) {
            m.setDisplayName(Text.color(c.getString("name", "Back bling")));
            List<String> lore = new ArrayList<String>();
            for (String line : c.getStringList("lore")) {
                lore.add(Text.color(line));
            }
            m.setLore(lore);
        }
        m.addItemFlags(ItemFlag.values());
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
        if (slot == REMOVE_SLOT) {
            if (worn(p) != null) {
                wear(p, null);
                msg(p, "removed", null);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            }
            fill(p, h);
            return;
        }
        String id = slot >= 0 && slot < h.slots.size() ? h.slots.get(slot) : null;
        if (id == null) {
            return;
        }
        ConfigurationSection c = backBlings.get(id);
        if (!owned(p).contains(id)) {
            msg(p, "locked", c);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        if (id.equals(worn(p))) {
            wear(p, null);
            msg(p, "removed", c);
        } else {
            wear(p, id);
            msg(p, "equipped", c);
        }
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.2f);
        fill(p, h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public List<String> ids() {
        return new ArrayList<String>(backBlings.keySet());
    }

    static List<String> list(String... s) {
        return Collections.unmodifiableList(Arrays.asList(s));
    }
}
