package io.github.jiggy4413.prisoncore.auto;

import io.github.jiggy4413.prisoncore.util.Smelting;
import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
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

/**
 * Auto-smelt and auto-pickup (autominer.yml). Each player turns them on/off with /autosmelt,
 * /autopickup or the /auto menu; the choice is saved on the player (stays across restarts).
 *
 * Order on a block break:
 *   NORMAL   - auto-smelt changes the drops in place
 *   HIGH     - the Fortune enchant (Enchants) may multiply the drops and give them itself
 *   HIGHEST  - auto-pickup moves what's left into the inventory; what doesn't fit is lost
 */
public final class AutoMine implements Listener, CommandExecutor, TabCompleter {

    private static final String FILE = "autominer.yml";
    private static final int SMELT_SLOT = 11;
    private static final int PICKUP_SLOT = 15;

    private final JavaPlugin plugin;
    private final NamespacedKey smeltKey;
    private final NamespacedKey pickupKey;
    private YamlConfiguration config = new YamlConfiguration();
    private final Map<UUID, Long> lastWarning = new HashMap<UUID, Long>();

    public AutoMine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.smeltKey = new NamespacedKey(plugin, "autosmelt");
        this.pickupKey = new NamespacedKey(plugin, "autopickup");
    }

    /** Reads autominer.yml. Returns a problem message, or null. */
    public String load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        Smelting.configure(config.getStringList("smelt-extra"), config.getStringList("smelt-exclude"),
                config.getBoolean("smelt-raw-blocks", true));
        return null;
    }

    // ------------------------------------------------------------------ toggles

    public boolean isSmelt(Player p) {
        return get(p, smeltKey, config.getBoolean("defaults.autosmelt", true));
    }

    public boolean isPickup(Player p) {
        return get(p, pickupKey, config.getBoolean("defaults.autopickup", true));
    }

    private static boolean get(Player p, NamespacedKey key, boolean def) {
        Byte b = p.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b == null ? def : b == 1;
    }

    private static void set(Player p, NamespacedKey key, boolean on) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
    }

    private void toggle(Player p, boolean smelt, Boolean to) {
        NamespacedKey key = smelt ? smeltKey : pickupKey;
        boolean now = to != null ? to : !(smelt ? isSmelt(p) : isPickup(p));
        set(p, key, now);
        String path = "messages." + (smelt ? "autosmelt" : "autopickup") + (now ? "-on" : "-off");
        String msg = config.getString(path, "");
        if (!msg.isEmpty()) {
            p.sendMessage(Text.color(p, msg));
        }
    }

    // ------------------------------------------------------------------ mining

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDropSmelt(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        if (!isSmelt(p)) {
            return;
        }
        for (Item item : event.getItems()) {
            item.setItemStack(Smelting.smelt(item.getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropPickup(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        if (event.getItems().isEmpty() || p.getGameMode() == GameMode.CREATIVE || !isPickup(p)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        for (Item item : event.getItems()) {
            stacks.add(item.getItemStack());
        }
        event.getItems().clear(); // nothing drops on the ground
        Map<Integer, ItemStack> left = p.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        if (!left.isEmpty()) {
            warnFull(p);
        }
    }

    /** Auto-pickup also takes the XP, with Mending repairing gear like normal orbs would. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakXp(BlockBreakEvent event) {
        Player p = event.getPlayer();
        int xp = event.getExpToDrop();
        if (xp <= 0 || p.getGameMode() == GameMode.CREATIVE || !isPickup(p)) {
            return;
        }
        event.setExpToDrop(0);
        p.giveExp(xp, true);
    }

    /** Action bar warning, at most once every few seconds. Used by Explosive too. */
    public void warnFull(Player p) {
        String msg = config.getString("messages.inventory-full", "");
        if (msg.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(p.getUniqueId());
        long gap = Math.max(0, config.getLong("inventory-full-warning-seconds", 2)) * 1000L;
        if (last != null && now - last < gap) {
            return;
        }
        lastWarning.put(p.getUniqueId(), now);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Text.color(p, msg)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }
        Player p = (Player) sender;
        String name = command.getName().toLowerCase();
        if (name.equals("auto")) {
            openMenu(p);
            return true;
        }
        Boolean to = null;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("on")) {
                to = Boolean.TRUE;
            } else if (args[0].equalsIgnoreCase("off")) {
                to = Boolean.FALSE;
            }
        }
        toggle(p, name.equals("autosmelt"), to);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && !command.getName().equalsIgnoreCase("auto")) {
            List<String> out = new ArrayList<String>();
            for (String o : Arrays.asList("on", "off")) {
                if (o.startsWith(args[0].toLowerCase())) {
                    out.add(o);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------ menu

    private static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openMenu(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.color(p, config.getString("menu.title", "&8Auto Mining")));
        holder.inventory = inv;
        fill(p, inv);
        p.openInventory(inv);
    }

    private void fill(Player p, Inventory inv) {
        ItemStack glass = icon(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.<String>emptyList(), false);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
        boolean smelt = isSmelt(p);
        boolean pickup = isPickup(p);
        inv.setItem(SMELT_SLOT, icon(Material.FURNACE, "&6&lAuto-Smelt", Arrays.asList(
                "&7Mined ores come out smelted",
                "&7(raw iron -> iron ingot).",
                "",
                "&7Status: " + (smelt ? "&a&lON" : "&c&lOFF"),
                "&eClick to turn " + (smelt ? "off" : "on")), smelt));
        inv.setItem(PICKUP_SLOT, icon(Material.HOPPER, "&b&lAuto-Pickup", Arrays.asList(
                "&7Drops and XP go straight",
                "&7into your inventory.",
                "&cIf your inventory is full,",
                "&cextra items are lost!",
                "",
                "&7Status: " + (pickup ? "&a&lON" : "&c&lOFF"),
                "&eClick to turn " + (pickup ? "off" : "on")), pickup));
    }

    private static ItemStack icon(Material type, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        List<String> colored = new ArrayList<String>();
        for (String line : lore) {
            colored.add(Text.color(line));
        }
        meta.setLore(colored);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
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
        int slot = event.getRawSlot();
        if (slot != SMELT_SLOT && slot != PICKUP_SLOT) {
            return;
        }
        toggle(p, slot == SMELT_SLOT, null);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        fill(p, event.getView().getTopInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
