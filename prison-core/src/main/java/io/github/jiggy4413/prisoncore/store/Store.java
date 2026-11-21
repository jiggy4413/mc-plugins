package io.github.jiggy4413.prisoncore.store;

import io.github.jiggy4413.prisoncore.util.ItemTemplate;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A good-looking /buy menu in place of Tebex's. Payment still happens on the Tebex website:
 * clicking a package closes the menu and sends a clickable link to it. Settings are in store.yml.
 */
public final class Store implements Listener, CommandExecutor {

    public static final String FILE = "store.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    /** Package id → live price info from Tebex (replaced as a whole on each refresh). */
    private volatile Map<Long, TebexPackage> packages = new HashMap<Long, TebexPackage>();
    private volatile String lastRefreshProblem;
    private org.bukkit.scheduler.BukkitTask refreshTask;

    /** One package as Tebex reports it. */
    static final class TebexPackage {
        String name;
        double basePrice;
        double totalPrice;
        double discount;
        String currency;
    }

    public Store(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
            }
            return FILE + " has a mistake, so the store menu didn't change: " + f.problem;
        }
        config = f.config;
        startRefreshing();
        return null;
    }

    // ------------------------------------------------------------------ prices from Tebex

    private void startRefreshing() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        String token = config.getString("tebex.public-token", "");
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        long minutes = Math.max(1, config.getLong("tebex.refresh-minutes", 10));
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 20L, minutes * 60 * 20);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Downloads every category and package from Tebex's Headless API (read-only, uses the store's
     * public token). Runs off the main thread.
     */
    private void refresh() {
        String token = config.getString("tebex.public-token", "").trim();
        java.net.HttpURLConnection con = null;
        try {
            java.net.URL url = new java.net.URL("https://headless.tebex.io/api/accounts/"
                    + java.net.URLEncoder.encode(token, "UTF-8") + "/categories?includePackages=1");
            con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "PrisonCore");
            int code = con.getResponseCode();
            if (code != 200) {
                lastRefreshProblem = "Tebex answered HTTP " + code + (code == 404 || code == 403
                        ? " (check tebex.public-token in store.yml)" : "");
                plugin.getLogger().warning("Couldn't load store prices: " + lastRefreshProblem);
                return;
            }
            java.io.Reader reader = new java.io.InputStreamReader(con.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            reader.close();
            Map<Long, TebexPackage> found = new HashMap<Long, TebexPackage>();
            for (com.google.gson.JsonElement cat : root.getAsJsonArray("data")) {
                com.google.gson.JsonObject c = cat.getAsJsonObject();
                if (!c.has("packages") || !c.get("packages").isJsonArray()) {
                    continue;
                }
                for (com.google.gson.JsonElement pe : c.getAsJsonArray("packages")) {
                    com.google.gson.JsonObject p = pe.getAsJsonObject();
                    TebexPackage t = new TebexPackage();
                    t.name = str(p, "name");
                    t.basePrice = num(p, "base_price");
                    t.totalPrice = num(p, "total_price");
                    t.discount = num(p, "discount");
                    t.currency = str(p, "currency");
                    found.put(p.get("id").getAsLong(), t);
                }
            }
            packages = found;
            lastRefreshProblem = null;
        } catch (Exception e) {
            lastRefreshProblem = e.getClass().getSimpleName() + ": " + e.getMessage();
            plugin.getLogger().warning("Couldn't load store prices: " + lastRefreshProblem);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private static String str(com.google.gson.JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static double num(com.google.gson.JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0;
    }

    /** The {price} text for an item: live from Tebex when it has a package id, else its "price:". */
    private String priceText(ConfigurationSection e) {
        long id = e.getLong("package", 0);
        TebexPackage t = id > 0 ? packages.get(id) : null;
        if (t == null) {
            return e.getString("price", config.getString("tebex.price-unknown", "See store"));
        }
        String price = money(t.totalPrice, t.currency);
        if (t.discount > 0 && t.basePrice > t.totalPrice) {
            return config.getString("tebex.sale-format", "&8&m{old}&r &a{price} &c&lSALE")
                    .replace("{old}", money(t.basePrice, t.currency)).replace("{price}", price);
        }
        return config.getString("tebex.price-format", "{price}").replace("{price}", price);
    }

    private static String money(double amount, String currency) {
        String c = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
        String symbol = c.equals("USD") || c.equals("CAD") || c.equals("AUD") ? "$" : c.equals("EUR") ? "€"
                : c.equals("GBP") ? "£" : "";
        String number = new java.text.DecimalFormat("#,##0.00").format(amount);
        return symbol.isEmpty() ? number + " " + c : symbol + number;
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                p.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ opening

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("refresh") && sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&7Loading prices from Tebex..."));
            final CommandSender to = sender;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    refresh();
                    final String problem = lastRefreshProblem;
                    final int count = packages.size();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            to.sendMessage(Text.color(problem == null
                                    ? "&aLoaded " + count + " packages from Tebex."
                                    : "&cCouldn't load prices: " + problem));
                        }
                    });
                }
            });
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open the store menu. Use /prisonstore refresh to reload prices.");
            return true;
        }
        open((Player) sender, args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "main");
        return true;
    }

    /** Opens the menu for /buy (and the other open-commands) before Tebex sees the command. */
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
            label = label.substring(colon + 1);
        }
        for (String c : config.getStringList("open-commands")) {
            String name = c.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.equals(label) && parts.length == 1) {
                event.setCancelled(true);
                open(event.getPlayer(), "main");
                return;
            }
        }
    }

    void open(Player player, String page) {
        ConfigurationSection p = config.getConfigurationSection("menus." + page);
        if (p == null) {
            p = config.getConfigurationSection("menus.main");
            page = "main";
        }
        if (p == null) {
            player.sendMessage(Text.color("&cThe store menu isn't set up. Check store.yml."));
            return;
        }
        Menu menu = new Menu(page);
        int rows = Math.max(1, Math.min(6, p.getInt("rows", 5)));
        menu.inventory = Bukkit.createInventory(menu, rows * 9, Text.color(player, p.getString("title", "Store")));
        menu.render(player, p);
        player.openInventory(menu.inventory);
        sound(player, "open");
    }

    private void openLater(final Player player, final String page) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    open(player, page);
                }
            }
        });
    }

    // ------------------------------------------------------------------ the menu

    private final class Menu implements InventoryHolder {
        final String page;
        Inventory inventory;
        final Map<Integer, ConfigurationSection> entries = new HashMap<Integer, ConfigurationSection>();
        final Map<Integer, String> buttons = new HashMap<Integer, String>();

        Menu(String page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void render(Player player, ConfigurationSection p) {
            int size = inventory.getSize();
            // background, then a border around the edge in alternating colors
            ItemStack filler = pane(config.getString("decoration.filler", "BLACK_STAINED_GLASS_PANE"));
            List<String> borderMats = config.getStringList("decoration.border");
            for (int i = 0; i < size; i++) {
                int row = i / 9;
                int col = i % 9;
                boolean edge = row == 0 || row == size / 9 - 1 || col == 0 || col == 8;
                if (edge && !borderMats.isEmpty()) {
                    inventory.setItem(i, pane(borderMats.get((row + col) % borderMats.size())));
                } else {
                    inventory.setItem(i, filler);
                }
            }

            ConfigurationSection all = p.getConfigurationSection("items");
            if (all != null) {
                for (String key : all.getKeys(false)) {
                    ConfigurationSection e = all.getConfigurationSection(key);
                    if (e == null) {
                        continue;
                    }
                    int slot = e.getInt("slot", -1);
                    if (slot < 0 || slot >= size) {
                        continue;
                    }
                    Map<String, String> ph = new HashMap<String, String>();
                    ph.put("{price}", priceText(e));
                    ph.put("{player}", player.getName());
                    ItemStack item = ItemTemplate.of(e, config.getConfigurationSection("default-item")).build(player, ph, null);
                    if (e.isString("texture")) {
                        ItemTemplate.applyTexture(item, e.getString("texture"));
                    }
                    inventory.setItem(slot, item);
                    entries.put(slot, e);
                }
            }

            if (!"main".equals(page)) {
                button(player, "back", size);
            }
            button(player, "close", size);
        }

        private void button(Player player, String name, int size) {
            ConfigurationSection b = config.getConfigurationSection("buttons." + name);
            if (b == null) {
                return;
            }
            int slot = b.getInt("slot", -1);
            if (slot < 0) {
                slot = size + slot; // negative = counted from the end (-5 = middle of the bottom row)
            }
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, ItemTemplate.of(b).build(player, null, null));
                buttons.put(slot, name);
            }
        }

        void click(Player player, int slot) {
            String button = buttons.get(slot);
            if ("close".equals(button)) {
                player.closeInventory();
                return;
            }
            if ("back".equals(button)) {
                ConfigurationSection p = config.getConfigurationSection("menus." + page);
                openLater(player, p == null ? "main" : p.getString("back", "main"));
                return;
            }
            ConfigurationSection e = entries.get(slot);
            if (e == null) {
                return;
            }
            String open = e.getString("open", "");
            if (open != null && !open.isEmpty()) {
                sound(player, "click");
                openLater(player, open);
                return;
            }
            String link = e.getString("link", "");
            long pkg = e.getLong("package", 0);
            if ((link == null || link.isEmpty()) && pkg > 0) {
                link = "package/" + pkg; // a package id with no link opens that package's page
            }
            if (link == null) {
                return;
            }
            player.closeInventory();
            sound(player, "click");
            sendLink(player, url(link), e.getString("name", ""));
        }
    }

    private String url(String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        String base = config.getString("store-url", "https://example.tebex.io");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (link.isEmpty()) {
            return base;
        }
        return base + (link.startsWith("/") ? link : "/" + link);
    }

    /** Sends link-message; any line with {url} or {click} opens the link when clicked. */
    private void sendLink(Player player, String url, String name) {
        String cleanName = name == null ? "" : name;
        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        @SuppressWarnings("deprecation")
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(Text.color(player, "&eOpen " + url)));
        for (String line : config.getStringList("link-message")) {
            boolean clickable = line.contains("{url}") || line.contains("{click}");
            String text = line.replace("{package}", cleanName).replace("{url}", url).replace("{click}", "");
            BaseComponent[] parts = TextComponent.fromLegacyText(Text.color(player, text));
            if (clickable) {
                for (BaseComponent c : parts) {
                    c.setClickEvent(click);
                    c.setHoverEvent(hover);
                }
            }
            player.spigot().sendMessage(parts);
        }
    }

    private ItemStack pane(String material) {
        Material m = Material.matchMaterial(material == null ? "" : material);
        ItemStack it = new ItemStack(m != null && m.isItem() ? m : Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(" ");
        it.setItemMeta(meta);
        return it;
    }

    private void sound(Player player, String key) {
        String name = config.getString("sounds." + key, "");
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
            // unknown sound
        }
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < event.getView().getTopInventory().getSize() && event.getWhoClicked() instanceof Player) {
            ((Menu) holder).click((Player) event.getWhoClicked(), event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
