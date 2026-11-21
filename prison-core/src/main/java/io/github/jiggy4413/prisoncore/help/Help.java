package io.github.jiggy4413.prisoncore.help;

import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A short, hand-picked /help instead of the server's list of every command.
 * Reads help.yml: each line is a command (click to use it) or a plain text line such as a heading.
 * Commands with a permission are only shown to players who have it.
 */
public final class Help implements CommandExecutor, Listener {

    public static final String FILE = "help.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public Help(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads help.yml. Returns a description of a mistake in it, or null. */
    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
            }
            return FILE + " has a mistake, so /help didn't change: " + f.problem;
        }
        config = f.config;
        return null;
    }

    // ------------------------------------------------------------------ opening

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        show(sender, args.length > 0 ? args[0] : "1");
        return true;
    }

    /** Catches /help (and /?) before the server's own help runs. */
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
            label = label.substring(colon + 1); // "/bukkit:help" counts as "/help"
        }
        boolean match = false;
        for (String c : config.getStringList("open-commands")) {
            String name = c.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.equals(label)) {
                match = true;
                break;
            }
        }
        if (!match) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("prisoncore.help")) {
            return;
        }
        // staff can still reach the full list with /help all
        if (parts.length > 1 && parts[1].equalsIgnoreCase("all") && player.hasPermission("prisoncore.help.all")) {
            event.setMessage("/bukkit:help" + (parts.length > 2 ? " " + parts[2] : ""));
            return;
        }
        event.setCancelled(true);
        show(player, parts.length > 1 ? parts[1] : "1");
    }

    // ------------------------------------------------------------------ showing

    private void show(CommandSender sender, String pageArg) {
        Player player = sender instanceof Player ? (Player) sender : null;
        List<Line> lines = visibleLines(sender);
        int perPage = Math.max(1, config.getInt("lines-per-page", 10));
        int pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        int page;
        try {
            page = Integer.parseInt(pageArg);
        } catch (NumberFormatException e) {
            page = 1;
        }
        page = Math.max(1, Math.min(pages, page));

        for (String h : config.getStringList("header")) {
            sendPlain(sender, player, pagePlaceholders(h, page, pages));
        }
        int from = (page - 1) * perPage;
        for (int i = from; i < Math.min(lines.size(), from + perPage); i++) {
            Line line = lines.get(i);
            if (line.command == null) {
                sendPlain(sender, player, line.text);
            } else {
                sendCommand(sender, player, line);
            }
        }
        if (pages > 1 || config.getBoolean("always-show-footer", false)) {
            for (String f : config.getStringList("footer")) {
                sendFooter(sender, player, f, page, pages);
            }
        }
    }

    private static final class Line {
        String text;      // plain line (heading/blank), or null for a command
        String command;   // e.g. "/warps"
        String description;
    }

    private List<Line> visibleLines(CommandSender sender) {
        List<Line> out = new ArrayList<Line>();
        for (Map<?, ?> m : config.getMapList("lines")) {
            Object perm = m.get("permission");
            if (perm != null && !perm.toString().isEmpty() && !sender.hasPermission(perm.toString())) {
                continue;
            }
            Line line = new Line();
            Object command = m.get("command");
            if (command != null && !command.toString().isEmpty()) {
                String c = command.toString().trim();
                line.command = c.startsWith("/") ? c : "/" + c;
                Object d = m.get("description");
                line.description = d == null ? "" : d.toString();
            } else {
                Object t = m.get("text");
                line.text = t == null ? "" : t.toString();
            }
            out.add(line);
        }
        return out;
    }

    private void sendCommand(CommandSender sender, Player player, Line line) {
        String text = config.getString("format", "&6{command} &8» &7{description}")
                .replace("{command}", line.command).replace("{description}", line.description);
        if (player == null) {
            sender.sendMessage(Text.color(text));
            return;
        }
        BaseComponent[] parts = TextComponent.fromLegacyText(Text.color(player, text));
        // clicking puts the command in the chat box, up to its first <argument>
        String base = line.command;
        int arg = base.indexOf('<');
        int opt = base.indexOf('[');
        if (arg < 0 || (opt >= 0 && opt < arg)) {
            arg = opt;
        }
        boolean hasArgs = arg > 0;
        if (hasArgs) {
            base = base.substring(0, arg).trim();
        }
        boolean run = !hasArgs && config.getString("click", "suggest").equalsIgnoreCase("run");
        ClickEvent click = run ? new ClickEvent(ClickEvent.Action.RUN_COMMAND, base)
                : new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hasArgs ? base + " " : base);
        String hoverText = config.getString("hover", "&eClick to use &6{command}").replace("{command}", line.command)
                .replace("{description}", line.description);
        @SuppressWarnings("deprecation")
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(Text.color(player, hoverText)));
        for (BaseComponent part : parts) {
            part.setClickEvent(click);
            part.setHoverEvent(hover);
        }
        player.spigot().sendMessage(parts);
    }

    private void sendFooter(CommandSender sender, Player player, String template, int page, int pages) {
        String prev = page > 1 ? config.getString("previous", "&e« Previous") : config.getString("previous-none", "&8« Previous");
        String next = page < pages ? config.getString("next", "&eNext »") : config.getString("next-none", "&8Next »");
        String filled = pagePlaceholders(template, page, pages);
        if (player == null) {
            sender.sendMessage(Text.color(filled.replace("{previous}", prev).replace("{next}", next)));
            return;
        }
        // split around {previous} and {next} so those two parts can be clicked
        List<BaseComponent> out = new ArrayList<BaseComponent>();
        String rest = filled;
        while (!rest.isEmpty()) {
            int p = rest.indexOf("{previous}");
            int n = rest.indexOf("{next}");
            int at = p < 0 ? n : (n < 0 ? p : Math.min(p, n));
            if (at < 0) {
                add(out, Text.color(player, rest), null);
                break;
            }
            if (at > 0) {
                add(out, Text.color(player, rest.substring(0, at)), null);
            }
            boolean isPrev = at == p;
            int target = isPrev ? page - 1 : page + 1;
            String label = isPrev ? prev : next;
            add(out, Text.color(player, label), target >= 1 && target <= pages ? "/help " + target : null);
            rest = rest.substring(at + (isPrev ? "{previous}" : "{next}").length());
        }
        player.spigot().sendMessage(out.toArray(new BaseComponent[0]));
    }

    private static void add(List<BaseComponent> out, String legacy, String runCommand) {
        for (BaseComponent c : TextComponent.fromLegacyText(legacy)) {
            if (runCommand != null) {
                c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, runCommand));
            }
            out.add(c);
        }
    }

    private static String pagePlaceholders(String s, int page, int pages) {
        return s.replace("{page}", String.valueOf(page)).replace("{pages}", String.valueOf(pages));
    }

    private void sendPlain(CommandSender sender, Player player, String text) {
        sender.sendMessage(player == null ? Text.color(text) : Text.color(player, text));
    }
}
