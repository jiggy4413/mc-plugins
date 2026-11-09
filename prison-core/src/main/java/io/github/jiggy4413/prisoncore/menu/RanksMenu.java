package io.github.jiggy4413.prisoncore.menu;

import io.github.jiggy4413.prisoncore.PrisonCore;
import io.github.jiggy4413.prisoncore.rankup.Ladder;
import io.github.jiggy4413.prisoncore.rankup.LadderEntry;
import io.github.jiggy4413.prisoncore.rankup.RankupBridge;
import io.github.jiggy4413.prisoncore.util.ItemTemplate;
import io.github.jiggy4413.prisoncore.util.Text;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The /ranks menu for one player: a Ranks tab and a Prestiges tab, with pages.
 * Everything it shows comes from ranks-menu.yml and Rankup's live data.
 */
public final class RanksMenu implements InventoryHolder {

    public enum Tab { RANKS, PRESTIGES }

    private enum Action {
        RANKUP, PRESTIGE, PRESTIGE_NOT_ELIGIBLE, LOCKED, COMPLETED, CURRENT,
        PREVIOUS_PAGE, NEXT_PAGE, RANKS_TAB, PRESTIGES_TAB, CLOSE
    }

    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##");

    private final PrisonCore plugin;
    private final Player player;
    private Tab tab;
    /** -1 = jump to the page with the player's current rank. */
    private int page = -1;
    private Inventory inventory;
    private final Map<Integer, Action> actions = new HashMap<Integer, Action>();

    public RanksMenu(PrisonCore plugin, Player player, Tab tab) {
        this.plugin = plugin;
        this.player = player;
        this.tab = tab;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        player.openInventory(inventory);
        sound("open");
    }

    // ================================================================== drawing

    private void render() {
        FileConfiguration c = plugin.getMenuConfig();
        RankupBridge rankup = plugin.getRankup();

        int rows = Math.max(1, Math.min(6, c.getInt("rows", 6)));
        int size = rows * 9;
        List<Integer> slots = parseSlots(c.getString("rank-slots", "10-16,19-25,28-34,37-43"), size);
        int perPage = Math.max(1, slots.size());

        Ladder ranks = rankup.ranks(player);
        Ladder prestiges = rankup.prestigesAvailable() ? rankup.prestiges(player) : null;
        boolean prestigeTab = tab == Tab.PRESTIGES;
        Ladder shown = prestigeTab ? prestiges : ranks;

        int count = shown == null ? 0 : shown.entries.size();
        int pages = Math.max(1, (count + perPage - 1) / perPage);
        if (page < 0) {
            page = shown == null ? 0 : focusIndex(shown) / perPage;
        }
        page = Math.max(0, Math.min(pages - 1, page));

        String title = c.getString(prestigeTab ? "titles.prestiges" : "titles.ranks", prestigeTab ? "Prestiges" : "Ranks")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(pages));
        inventory = Bukkit.createInventory(this, size, Text.color(player, title));
        actions.clear();

        // ranks / prestiges
        if (shown == null) {
            place(c.getConfigurationSection("buttons.prestiges-disabled"), size, null, null);
        } else {
            int start = page * perPage;
            for (int k = 0; k < perPage && start + k < count; k++) {
                LadderEntry entry = shown.entries.get(start + k);
                int slot = slots.get(k);
                inventory.setItem(slot, entryItem(entry, shown, prestigeTab));
                actions.put(slot, actionFor(entry, shown, prestigeTab));
            }
        }

        // info item
        ConfigurationSection info = c.getConfigurationSection("info");
        if (info != null) {
            place(info, size, infoPlaceholders(ranks, prestiges), null);
        }

        // buttons
        if (page > 0) {
            place(c.getConfigurationSection("buttons.previous-page"), size, null, Action.PREVIOUS_PAGE);
        }
        if (page < pages - 1) {
            place(c.getConfigurationSection("buttons.next-page"), size, null, Action.NEXT_PAGE);
        }
        placeTab(c.getConfigurationSection("buttons.ranks-tab"), size, !prestigeTab, Action.RANKS_TAB);
        placeTab(c.getConfigurationSection("buttons.prestiges-tab"), size, prestigeTab, Action.PRESTIGES_TAB);
        place(c.getConfigurationSection("buttons.close"), size, null, Action.CLOSE);

        // filler for every empty slot
        ConfigurationSection fillerSection = c.getConfigurationSection("filler");
        if (fillerSection != null) {
            ItemStack filler = ItemTemplate.of(fillerSection).build(player, null, null);
            for (int i = 0; i < size; i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
    }

    private ItemStack entryItem(LadderEntry entry, Ladder ladder, boolean prestige) {
        FileConfiguration c = plugin.getMenuConfig();
        String state = entry.state.name().toLowerCase();
        ConfigurationSection own = ownSection(prestige, entry.id);

        ItemTemplate template = ItemTemplate.of(
                own == null ? null : own.getConfigurationSection(state),
                own,
                prestige ? c.getConfigurationSection("prestige-states." + state) : null,
                c.getConfigurationSection("states." + state));

        Map<String, String> ph = new LinkedHashMap<String, String>();
        ph.put("{rank}", entry.id);
        ph.put("{display}", display(entry, prestige));
        ph.put("{position}", String.valueOf(entry.position));
        ph.put("{total}", String.valueOf(ladder.entries.size()));
        ph.put("{cost}", cost(entry));
        ph.put("{progress}", String.valueOf((int) Math.floor(entry.progress() * 100)));
        ph.put("{progress_bar}", progressBar(entry.progress()));
        ph.put("{status}", c.getString("status." + state, state));
        ph.put("{player}", player.getName());

        Map<String, List<String>> multi = new HashMap<String, List<String>>();
        multi.put("{requirements}", requirementLines(entry));

        return template.build(player, ph, multi);
    }

    private Map<String, String> infoPlaceholders(Ladder ranks, Ladder prestiges) {
        FileConfiguration c = plugin.getMenuConfig();
        String none = c.getString("none", "None");
        String max = c.getString("max", "MAX");

        Map<String, String> ph = new LinkedHashMap<String, String>();
        ph.put("{player}", player.getName());
        ph.put("{current_rank}", ranks.current == null ? none : display(ranks.current, false));
        ph.put("{next_rank}", ranks.next == null ? (ranks.current == null ? none : max) : display(ranks.next, false));
        if (prestiges == null) {
            ph.put("{current_prestige}", none);
            ph.put("{next_prestige}", none);
        } else {
            ph.put("{current_prestige}", prestiges.current == null ? none : display(prestiges.current, true));
            ph.put("{next_prestige}", prestiges.next == null ? max : display(prestiges.next, true));
        }
        double progress = ranks.next == null ? (ranks.current == null ? 0 : 1) : ranks.next.progress();
        ph.put("{progress}", String.valueOf((int) Math.floor(progress * 100)));
        ph.put("{progress_bar}", progressBar(progress));
        return ph;
    }

    private void place(ConfigurationSection section, int size, Map<String, String> ph, Action action) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= size) {
            return;
        }
        Map<String, String> placeholders = ph != null ? ph : Collections.singletonMap("{player}", player.getName());
        inventory.setItem(slot, ItemTemplate.of(section).build(player, placeholders, null));
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    private void placeTab(ConfigurationSection section, int size, boolean active, Action action) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= size) {
            return;
        }
        ItemTemplate template = ItemTemplate.of(section);
        if (active) {
            template.withGlow(true);
        }
        inventory.setItem(slot, template.build(player, Collections.singletonMap("{player}", player.getName()), null));
        actions.put(slot, action);
    }

    // ================================================================== clicking

    public void handleClick(int rawSlot) {
        Action action = actions.get(rawSlot);
        if (action == null) {
            return;
        }
        FileConfiguration c = plugin.getMenuConfig();
        switch (action) {
            case RANKUP:
                sound("click");
                runAfterClose("rankup");
                break;
            case PRESTIGE:
                sound("click");
                runAfterClose("prestige");
                break;
            case PRESTIGE_NOT_ELIGIBLE:
                deny(c.getString("messages.prestige-not-eligible"));
                break;
            case LOCKED:
                deny(c.getString("messages.locked"));
                break;
            case COMPLETED:
                message(c.getString("messages.completed"));
                break;
            case CURRENT:
                Ladder ranks = plugin.getRankup().ranks(player);
                if (tab == Tab.RANKS && ranks.next == null) {
                    message(c.getString("messages.max-rank"));
                }
                break;
            case PREVIOUS_PAGE:
                page--;
                sound("page");
                reopenLater();
                break;
            case NEXT_PAGE:
                page++;
                sound("page");
                reopenLater();
                break;
            case RANKS_TAB:
                switchTab(Tab.RANKS);
                break;
            case PRESTIGES_TAB:
                switchTab(Tab.PRESTIGES);
                break;
            case CLOSE:
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        player.closeInventory();
                    }
                });
                break;
            default:
                break;
        }
    }

    private Action actionFor(LadderEntry entry, Ladder ladder, boolean prestige) {
        switch (entry.state) {
            case NEXT:
                if (!prestige) {
                    return Action.RANKUP;
                }
                return ladder.canAdvance ? Action.PRESTIGE : Action.PRESTIGE_NOT_ELIGIBLE;
            case CURRENT:
                return Action.CURRENT;
            case COMPLETED:
                return Action.COMPLETED;
            default:
                return Action.LOCKED;
        }
    }

    private void switchTab(Tab newTab) {
        if (tab == newTab) {
            return;
        }
        tab = newTab;
        page = -1;
        sound("click");
        reopenLater();
    }

    /** Opening a new inventory inside a click event is unsafe, so wait one tick. */
    private void reopenLater() {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                render();
                player.openInventory(inventory);
            }
        });
    }

    /** Closes the menu and runs a Rankup command as the player, so Rankup's own checks apply. */
    private void runAfterClose(final String command) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                player.performCommand(command);
            }
        });
    }

    // ================================================================== helpers

    /** Styled name: "display" from ranks-menu.yml, else Rankup's display-name, else the id. */
    private String display(LadderEntry entry, boolean prestige) {
        ConfigurationSection own = ownSection(prestige, entry.id);
        if (own != null && own.isString("display")) {
            return own.getString("display");
        }
        return entry.rankupDisplayName;
    }

    /** The rank's own section under "ranks:" / "prestiges:", matched ignoring case. */
    private ConfigurationSection ownSection(boolean prestige, String id) {
        ConfigurationSection all = plugin.getMenuConfig().getConfigurationSection(prestige ? "prestiges" : "ranks");
        if (all == null || id == null) {
            return null;
        }
        for (String key : all.getKeys(false)) {
            if (key.equalsIgnoreCase(id) && all.isConfigurationSection(key)) {
                return all.getConfigurationSection(key);
            }
        }
        return null;
    }

    private String cost(LadderEntry entry) {
        double money = entry.moneyCost();
        if (money < 0) {
            return plugin.getMenuConfig().getString("free-cost", "Free");
        }
        return plugin.getRankup().formatMoney(money);
    }

    private List<String> requirementLines(LadderEntry entry) {
        FileConfiguration c = plugin.getMenuConfig();
        List<String> lines = new ArrayList<String>();
        if (entry.requirements.isEmpty()) {
            lines.add("  &7" + c.getString("none", "None"));
            return lines;
        }
        for (LadderEntry.Requirement r : entry.requirements) {
            String name = c.getString("requirement-names." + r.name, r.name);
            if (r.sub != null && !r.sub.isEmpty()) {
                name = name + " (" + r.sub + ")";
            }
            String format;
            if (r.progressive) {
                format = c.getString(r.met ? "requirement-line.met" : "requirement-line.unmet", "  {name}: {done}/{total}");
            } else {
                format = c.getString(r.met ? "requirement-line.simple-met" : "requirement-line.simple-unmet", "  {name}");
            }
            double done = Math.max(0, r.total - r.remaining);
            lines.add(format
                    .replace("{name}", name)
                    .replace("{done}", amount(r, done))
                    .replace("{total}", amount(r, r.total))
                    .replace("{remaining}", amount(r, r.remaining))
                    .replace("{percent}", String.valueOf((int) Math.floor(r.progress() * 100)))
                    .replace("{mark}", r.met ? "✔" : "✖"));
        }
        return lines;
    }

    private String amount(LadderEntry.Requirement r, double value) {
        return r.isMoney() ? plugin.getRankup().formatMoney(value) : NUMBER.format(value);
    }

    private String progressBar(double progress) {
        FileConfiguration c = plugin.getMenuConfig();
        int length = Math.max(1, c.getInt("progress-bar.length", 20));
        String symbol = c.getString("progress-bar.symbol", "|");
        int filled = (int) Math.round(progress * length);
        StringBuilder sb = new StringBuilder(c.getString("progress-bar.filled", "&a"));
        for (int i = 0; i < filled; i++) {
            sb.append(symbol);
        }
        sb.append(c.getString("progress-bar.empty", "&8"));
        for (int i = filled; i < length; i++) {
            sb.append(symbol);
        }
        return sb.toString();
    }

    /** Index of the entry the menu should open on (current or next rank). */
    private static int focusIndex(Ladder ladder) {
        LadderEntry focus = ladder.current != null ? ladder.current : ladder.next;
        int i = focus == null ? -1 : ladder.entries.indexOf(focus);
        return Math.max(0, i);
    }

    /** "10-16, 19-25" -> [10..16, 19..25], ignoring anything outside the menu. */
    static List<Integer> parseSlots(String spec, int size) {
        TreeSet<Integer> seen = new TreeSet<Integer>();
        List<Integer> out = new ArrayList<Integer>();
        if (spec == null) {
            return out;
        }
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                int from;
                int to;
                int dash = part.indexOf('-');
                if (dash > 0) {
                    from = Integer.parseInt(part.substring(0, dash).trim());
                    to = Integer.parseInt(part.substring(dash + 1).trim());
                } else {
                    from = to = Integer.parseInt(part);
                }
                for (int s = Math.min(from, to); s <= Math.max(from, to); s++) {
                    if (s >= 0 && s < size && seen.add(s)) {
                        out.add(s);
                    }
                }
            } catch (NumberFormatException ignored) {
                // skip bad entries
            }
        }
        return out;
    }

    private void message(String text) {
        if (text != null && !text.isEmpty()) {
            player.sendMessage(Text.color(player, text));
        }
    }

    private void deny(String text) {
        message(text);
        sound("deny");
    }

    private void sound(String key) {
        String name = plugin.getMenuConfig().getString("sounds." + key, "");
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase()), 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound in ranks-menu.yml: " + name);
        }
    }
}
