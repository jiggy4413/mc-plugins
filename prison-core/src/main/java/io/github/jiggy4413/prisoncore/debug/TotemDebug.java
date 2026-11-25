package io.github.jiggy4413.prisoncore.debug;

import io.github.jiggy4413.prisoncore.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /prisoncore debug totems: finds out why totems of undying don't save players.
 * While on, every plugin listening to the totem event is watched, and each time a player would
 * die the staff who turned it on (and the console) see whether Minecraft found a totem, which
 * plugin cancelled it, or that no totem check happened at all (void, /kill, health set to 0).
 */
public final class TotemDebug implements Listener {

    private static final EventExecutor NOTHING = new EventExecutor() {
        @Override
        public void execute(Listener listener, Event event) {
            // never used: Watched.callEvent runs the real listener
        }
    };

    private final JavaPlugin plugin;
    private boolean on;
    private final Set<UUID> watchers = new HashSet<UUID>();
    private final Map<EntityResurrectEvent, List<String>> changes = new WeakHashMap<EntityResurrectEvent, List<String>>();
    private final Map<EntityResurrectEvent, Boolean> startedCancelled = new WeakHashMap<EntityResurrectEvent, Boolean>();
    private final Map<UUID, Long> lastCheck = new HashMap<UUID, Long>();

    public TotemDebug(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Wraps another plugin's listener to see whether it cancels or un-cancels the totem. */
    private final class Watched extends RegisteredListener {
        final RegisteredListener inner;

        Watched(RegisteredListener inner) {
            super(inner.getListener(), NOTHING, inner.getPriority(), inner.getPlugin(), inner.isIgnoringCancelled());
            this.inner = inner;
        }

        @Override
        public void callEvent(Event event) throws EventException {
            if (!(event instanceof EntityResurrectEvent)) {
                inner.callEvent(event);
                return;
            }
            EntityResurrectEvent r = (EntityResurrectEvent) event;
            List<String> list = changes.get(r);
            if (list == null) {
                list = new ArrayList<String>();
                changes.put(r, list);
                startedCancelled.put(r, r.isCancelled());
            }
            boolean before = r.isCancelled();
            inner.callEvent(event);
            boolean after = r.isCancelled();
            if (before != after) {
                list.add("&f" + inner.getPlugin().getName() + " &7(" + inner.getListener().getClass().getSimpleName()
                        + ", " + inner.getPriority() + ") " + (after ? "&cCANCELLED the totem" : "&aallowed the totem"));
            }
        }
    }

    // ------------------------------------------------------------------ command

    public void command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("totems")) {
            sender.sendMessage(Text.color("&cUsage: /prisoncore debug totems"));
            return;
        }
        if (on) {
            stop();
            sender.sendMessage(Text.color("&7Totem debug &coff&7."));
            return;
        }
        start();
        if (sender instanceof Player) {
            watchers.add(((Player) sender).getUniqueId());
        }
        sender.sendMessage(Text.color("&7Totem debug &aon&7. Plugins listening to totems:"));
        int n = 0;
        for (RegisteredListener rl : EntityResurrectEvent.getHandlerList().getRegisteredListeners()) {
            if (rl.getListener() == this) {
                continue;
            }
            RegisteredListener real = rl instanceof Watched ? ((Watched) rl).inner : rl;
            sender.sendMessage(Text.color(" &8- &f" + real.getPlugin().getName() + " &7("
                    + real.getListener().getClass().getSimpleName() + ", " + real.getPriority() + ")"));
            n++;
        }
        if (n == 0) {
            sender.sendMessage(Text.color(" &8- &7none (no plugin touches totems)"));
        }
        sender.sendMessage(Text.color("&7Now hold a totem and take deadly damage (lava or a fall, not /kill or the void)."
                + " Run the command again to turn it off."));
    }

    private void start() {
        HandlerList handlers = EntityResurrectEvent.getHandlerList();
        for (RegisteredListener rl : handlers.getRegisteredListeners()) {
            if (rl.getListener() == this || rl instanceof Watched) {
                continue;
            }
            handlers.unregister(rl);
            handlers.register(new Watched(rl));
        }
        on = true;
    }

    /** Puts every plugin's original listener back. Also called when PrisonCore shuts down. */
    public void stop() {
        if (!on) {
            return;
        }
        HandlerList handlers = EntityResurrectEvent.getHandlerList();
        for (RegisteredListener rl : handlers.getRegisteredListeners()) {
            if (rl instanceof Watched) {
                handlers.unregister(rl);
                handlers.register(((Watched) rl).inner);
            }
        }
        on = false;
        watchers.clear();
        changes.clear();
        startedCancelled.clear();
        lastCheck.clear();
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent event) {
        if (!on || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getEntity();
        lastCheck.put(p.getUniqueId(), System.currentTimeMillis());
        List<String> list = changes.get(event);
        Boolean started = startedCancelled.get(event);
        boolean startedOff = started != null ? started : event.isCancelled();

        report("&6&lTOTEM CHECK &f" + p.getName() + " &7(" + cause(p) + ", holding " + hands(p) + ")");
        report(startedOff
                ? "&7Minecraft: &cno totem in either hand"
                : "&7Minecraft: &atotem found, would save them");
        if (list != null) {
            for (String line : list) {
                report("&7 then " + line);
            }
        }
        report(event.isCancelled() ? "&7Result: &c&lNOT SAVED" : "&7Result: &a&lSAVED");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!on) {
            return;
        }
        Player p = event.getEntity();
        Long last = lastCheck.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 1000) {
            return; // the totem check above already explained it
        }
        report("&6&lTOTEM CHECK &f" + p.getName() + " &7died (" + cause(p) + ", holding " + hands(p) + ")");
        report("&cNo totem check happened at all. &7Minecraft skips totems for the void and /kill, and a plugin"
                + " that kills by setting health to 0 skips them too.");
    }

    private static String cause(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        return last == null ? "unknown damage" : last.getCause().name().toLowerCase().replace('_', ' ');
    }

    private static String hands(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return "nothing";
        }
        boolean main = eq.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING;
        boolean off = eq.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
        return main && off ? "totems in both hands" : main ? "a totem in main hand" : off ? "a totem in offhand" : "no totem";
    }

    private void report(String line) {
        String colored = Text.color(line);
        plugin.getLogger().info(org.bukkit.ChatColor.stripColor(colored));
        for (UUID id : watchers) {
            Player w = Bukkit.getPlayer(id);
            if (w != null) {
                w.sendMessage(colored);
            }
        }
    }
}
