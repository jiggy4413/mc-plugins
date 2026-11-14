package io.github.jiggy4413.prisoncore.mines;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /pmine: set up and manage PrisonCore mines. */
public final class MineCommand implements TabExecutor {

    private static final List<String> SUBS = Arrays.asList(
            "list", "info", "pos1", "pos2", "setregion", "setspawn", "reset", "import");

    private final MineManager manager;
    private final File pluginsFolder;
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();

    public MineCommand(MineManager manager, File pluginsFolder) {
        this.manager = manager;
        this.pluginsFolder = pluginsFolder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            list(sender);
        } else if (sub.equals("info")) {
            Mine m = need(sender, args, label + " info <mine>");
            if (m != null) {
                info(sender, m);
            }
        } else if (sub.equals("pos1") || sub.equals("pos2")) {
            setPos(sender, sub.equals("pos1"));
        } else if (sub.equals("setregion")) {
            setRegion(sender, args, label);
        } else if (sub.equals("setspawn")) {
            setSpawn(sender, args, label);
        } else if (sub.equals("reset")) {
            Mine m = need(sender, args, label + " reset <mine>");
            if (m != null) {
                if (!m.hasRegion) {
                    say(sender, "&cMine " + m.id + " has no region yet. Use /" + label + " setregion " + m.id + ".");
                } else {
                    manager.queueReset(m);
                    say(sender, "&aMine " + m.id + " will reset now (mines reset one at a time).");
                }
            }
        } else if (sub.equals("import")) {
            boolean overwrite = args.length > 1 && args[1].equalsIgnoreCase("overwrite");
            importMrl(sender, overwrite, label);
        } else {
            help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        say(s, "&6&lPrisonCore mines");
        say(s, "&e/" + label + " list &7- every mine and how mined it is");
        say(s, "&e/" + label + " info <mine> &7- details for one mine");
        say(s, "&e/" + label + " pos1 &7/ &epos2 &7- mark the corners (look at a block)");
        say(s, "&e/" + label + " setregion <mine> &7- save the marked corners as the mine");
        say(s, "&e/" + label + " setspawn <mine> &7- where players go when it resets");
        say(s, "&e/" + label + " reset <mine> &7- refill it now");
        say(s, "&e/" + label + " import [overwrite] &7- copy mines from MineResetLite");
        say(s, "&7Blocks, percentages and timers are edited in &fmines.yml&7, then &f/prisoncore reload&7.");
    }

    private void list(CommandSender s) {
        say(s, "&6&lMines");
        for (Mine m : manager.mines()) {
            String state;
            if (!m.hasRegion) {
                state = "&8not set up";
            } else if (m.isResetting()) {
                state = "&eresetting " + (int) (m.resetProgress * 100) + "%";
            } else if (!m.isCounted()) {
                state = "&7checking...";
            } else {
                state = "&f" + m.minedText() + "% &7mined &8(resets at " + (int) m.resetAtPercent() + "%)";
            }
            say(s, "&e" + m.id + " &7" + m.display() + " &8- " + state);
        }
    }

    private void info(CommandSender s, Mine m) {
        say(s, "&6&lMine " + m.id + " &7" + m.display());
        if (!m.hasRegion) {
            say(s, "&7Region: &cnot set &7(use pos1, pos2, setregion)");
        } else {
            say(s, "&7World: &f" + m.worldName + (m.world() == null ? " &c(not loaded)" : ""));
            say(s, "&7Region: &f" + m.regionString() + " &8(" + String.format(Locale.ROOT, "%,d", m.volume()) + " blocks)");
            say(s, "&7Mined: &f" + (m.isCounted() ? m.minedText() + "%" : "checking...")
                    + " &7- resets at &f" + (int) m.resetAtPercent() + "%");
            say(s, "&7Spawn: &f" + (m.spawn == null ? "not set &7(players go to the world spawn on reset)" : "set"));
            say(s, "&7Timer: &f" + (m.timerMinutes > 0 ? m.timerMinutes + " min &7(next in " + manager.formatTimer(m) + ")" : "off"));
        }
        say(s, "&7Fill mode: &f" + m.fillMode + " &7Surface: &f" + (m.surface == null ? "none" : m.surface.name()));
        StringBuilder sb = new StringBuilder("&7Blocks: ");
        if (m.composition.isEmpty()) {
            sb.append("&cnone set");
        }
        for (Map.Entry<org.bukkit.Material, Double> e : m.composition.weights().entrySet()) {
            sb.append("&f").append(e.getKey().name().toLowerCase(Locale.ROOT)).append(" &7")
                    .append(trim(e.getValue())).append(" ");
        }
        say(s, sb.toString());
    }

    private void setPos(CommandSender sender, boolean first) {
        if (!(sender instanceof Player)) {
            say(sender, "&cOnly players can mark corners.");
            return;
        }
        Player p = (Player) sender;
        Block target = p.getTargetBlockExact(10);
        Location loc = target != null ? target.getLocation() : p.getLocation().getBlock().getLocation();
        (first ? pos1 : pos2).put(p.getUniqueId(), loc);
        say(p, "&aCorner " + (first ? 1 : 2) + " set to &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + (target == null ? " &7(your feet: no block in reach)" : ""));
    }

    private void setRegion(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player)) {
            say(sender, "&cOnly players can do that.");
            return;
        }
        if (args.length < 2) {
            say(sender, "&cUse: /" + label + " setregion <mine>");
            return;
        }
        Player p = (Player) sender;
        Location a = pos1.get(p.getUniqueId());
        Location b = pos2.get(p.getUniqueId());
        if (a == null || b == null) {
            say(p, "&cMark both corners first with /" + label + " pos1 and /" + label + " pos2.");
            return;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            say(p, "&cBoth corners must be in the same world.");
            return;
        }
        Mine m = manager.getOrCreate(args[1]);
        manager.setRegion(m, a.getWorld(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
        String err = manager.save();
        say(p, "&aMine " + m.id + " now covers &f" + m.regionString() + " &8("
                + String.format(Locale.ROOT, "%,d", m.volume()) + " blocks)");
        if (m.spawn == null) {
            say(p, "&eTip: stand where players should go during a reset and run /" + label + " setspawn " + m.id
                    + " &7(otherwise they're sent to the world spawn).");
        }
        if (m.composition.isEmpty()) {
            say(p, "&eIt has no blocks yet: add them under mines." + m.id + ".blocks in mines.yml, then /prisoncore reload.");
        }
        if (err != null) {
            say(p, "&cCouldn't save mines.yml: " + err);
        }
    }

    private void setSpawn(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player)) {
            say(sender, "&cOnly players can do that.");
            return;
        }
        Mine m = need(sender, args, label + " setspawn <mine>");
        if (m == null) {
            return;
        }
        manager.setSpawn(m, ((Player) sender).getLocation());
        String err = manager.save();
        say(sender, err == null ? "&aPlayers in mine " + m.id + " will be sent here when it resets." : "&cCouldn't save: " + err);
    }

    private void importMrl(CommandSender sender, boolean overwrite, String label) {
        MrlImporter.Result r = MrlImporter.run(manager, pluginsFolder, overwrite);
        String err = r.imported.isEmpty() ? null : manager.save();
        say(sender, "&6&lMineResetLite import");
        for (String s : r.imported) {
            say(sender, "&a+ " + s);
        }
        for (String s : r.skipped) {
            say(sender, "&e~ skipped " + s);
        }
        for (String s : r.problems) {
            say(sender, "&c! " + s);
        }
        if (r.imported.isEmpty() && r.skipped.isEmpty() && r.problems.isEmpty()) {
            say(sender, "&7No mine files found.");
        }
        if (!r.skipped.isEmpty() && !overwrite) {
            say(sender, "&7Use &f/" + label + " import overwrite &7to replace mines that are already set up.");
        }
        if (!r.imported.isEmpty()) {
            say(sender, "&7Now stop MineResetLite from resetting them too: remove its jar (keep its folder as a backup).");
        }
        if (err != null) {
            say(sender, "&cCouldn't save mines.yml: " + err);
        }
    }

    private Mine need(CommandSender s, String[] args, String usage) {
        if (args.length < 2) {
            say(s, "&cUse: /" + usage);
            return null;
        }
        Mine m = manager.get(args[1]);
        if (m == null) {
            say(s, "&cNo mine called '" + args[1] + "'. See /pmine list.");
        }
        return m;
    }

    private static void say(CommandSender s, String msg) {
        s.sendMessage(Text.color(msg));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("import")) {
                return "overwrite".startsWith(args[1].toLowerCase(Locale.ROOT))
                        ? Collections.singletonList("overwrite") : Collections.<String>emptyList();
            }
            if (Arrays.asList("info", "setregion", "setspawn", "reset").contains(sub)) {
                for (Mine m : manager.mines()) {
                    if (m.id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(m.id);
                    }
                }
            }
        }
        return out;
    }
}
