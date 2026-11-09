package io.github.jiggy4413.prisoncore.update;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Swaps the running PrisonCore for a newer jar without restarting the server.
 *
 * How it finds the new jar, in order:
 *   1. another PrisonCore jar in plugins/ (e.g. you uploaded PrisonCore-1.2.0.jar next to the old one)
 *   2. a PrisonCore jar in plugins/update/
 *   3. the current jar file itself, if it was overwritten since the server started
 *
 * Then it unloads this copy (same steps PlugMan uses), deletes the old jar, and loads the new one.
 *
 * IMPORTANT: once the old copy is unloaded, its jar is closed, so this class can't load any other
 * PrisonCore classes. That's why it uses no lambdas or helper classes from the rest of the plugin,
 * and why PrisonCore loads this class (and SwapTask) when it starts.
 */
public final class HotSwap {

    private static final String NAME = "PrisonCore";

    private final JavaPlugin plugin;
    private final File currentJar;
    private final long startModified;
    private final long startLength;

    public HotSwap(JavaPlugin plugin, File currentJar) {
        this.plugin = plugin;
        this.currentJar = currentJar;
        this.startModified = currentJar.lastModified();
        this.startLength = currentJar.length();
        // make sure the task class is loaded now, while the jar is still readable
        SwapTask.class.getName();
    }

    /** The new jar to switch to, or null if there isn't one. */
    public File findUpdate() {
        File pluginsDir = currentJar.getParentFile();
        File newest = null;

        List<File> candidates = new ArrayList<File>();
        addJars(candidates, pluginsDir);
        addJars(candidates, Bukkit.getUpdateFolderFile());

        for (File f : candidates) {
            if (sameFile(f, currentJar) || !isPrisonCore(f)) {
                continue;
            }
            if (newest == null || f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }
        if (newest != null) {
            return newest;
        }
        // overwritten in place?
        if (currentJar.exists() && (currentJar.lastModified() != startModified || currentJar.length() != startLength)
                && isPrisonCore(currentJar)) {
            return currentJar;
        }
        return null;
    }

    /**
     * Checks everything that could fail BEFORE touching the running plugin, then schedules the swap.
     * Returns an error message, or null if the swap was scheduled.
     */
    public String start(File newJar, CommandSender sender) {
        PluginManager pm = Bukkit.getPluginManager();
        try {
            Field pluginsField = field(pm.getClass(), "plugins");
            Field namesField = field(pm.getClass(), "lookupNames");
            if (pluginsField == null || namesField == null) {
                return "This server's plugin manager isn't supported for live updates. Restart instead.";
            }
            if (!(Bukkit.getCommandMap() instanceof SimpleCommandMap)) {
                return "This server's command map isn't supported for live updates. Restart instead.";
            }
            if (!(plugin.getClass().getClassLoader() instanceof URLClassLoader)) {
                return "This server's plugin loader isn't supported for live updates. Restart instead.";
            }
            PluginDescriptionFile desc = plugin.getPluginLoader().getPluginDescription(newJar);
            if (!NAME.equals(desc.getName())) {
                return newJar.getName() + " isn't a PrisonCore jar.";
            }

            SwapTask task = new SwapTask(plugin, currentJar, newJar, desc.getVersion(), pluginsField, namesField, sender);
            // run next tick so the command that triggered this finishes first
            Bukkit.getScheduler().runTask(plugin, task);
            return null;
        } catch (Exception e) {
            return "Can't read " + newJar.getName() + ": " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ the swap itself

    private static final class SwapTask implements Runnable {
        private final JavaPlugin old;
        private final File oldJar;
        private final File newJar;
        private final String newVersion;
        private final Field pluginsField;
        private final Field namesField;
        private final CommandSender sender;

        SwapTask(JavaPlugin old, File oldJar, File newJar, String newVersion, Field pluginsField, Field namesField,
                 CommandSender sender) {
            this.old = old;
            this.oldJar = oldJar;
            this.newJar = newJar;
            this.newVersion = newVersion;
            this.pluginsField = pluginsField;
            this.namesField = namesField;
            this.sender = sender;
        }

        @Override
        public void run() {
            String oldVersion = old.getDescription().getVersion();
            Class<?> oldMain = old.getClass();
            ClassLoader oldLoader = oldMain.getClassLoader();
            PluginManager pm = Bukkit.getPluginManager();
            SimpleCommandMap commandMap = (SimpleCommandMap) Bukkit.getCommandMap();

            try {
                // 1. disable: runs onDisable, removes listeners and tasks
                pm.disablePlugin(old);

                // 2. remove our commands so the new copy can register them again
                Map<String, Command> known = commandMap.getKnownCommands();
                Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
                while (it.hasNext()) {
                    Command cmd = it.next().getValue();
                    if (cmd instanceof PluginCommand && ((PluginCommand) cmd).getPlugin() == old) {
                        cmd.unregister(commandMap);
                        it.remove();
                    }
                }

                // 3. forget the old plugin
                ((List<?>) pluginsField.get(pm)).remove(old);
                Collection<?> names = ((Map<?, ?>) namesField.get(pm)).values();
                while (names.remove(old)) {
                    // remove every alias pointing at it
                }

                // 4. drop cached classes so the new jar's classes are used, then close the old jar
                purgeClassCache(old.getPluginLoader(), oldLoader);
                ((URLClassLoader) oldLoader).close();

                // 5. files: remove the old jar, move the new one into plugins/ if needed
                File pluginsDir = oldJar.getParentFile();
                File target = newJar;
                if (!newJar.getCanonicalFile().equals(oldJar.getCanonicalFile())) {
                    if (!oldJar.delete()) {
                        oldJar.deleteOnExit();
                    }
                    if (!newJar.getParentFile().getCanonicalFile().equals(pluginsDir.getCanonicalFile())) {
                        target = new File(pluginsDir, newJar.getName());
                        if (!newJar.renameTo(target)) {
                            throw new IOException("Couldn't move " + newJar.getName() + " into plugins/");
                        }
                    }
                }

                // 6. load and enable the new copy
                Plugin fresh = pm.loadPlugin(target);
                if (fresh == null) {
                    throw new IllegalStateException("Bukkit refused to load " + target.getName());
                }
                fresh.onLoad();
                pm.enablePlugin(fresh);
                syncCommands();

                if (fresh.getClass() == oldMain) {
                    tell(ChatColor.YELLOW + "PrisonCore reloaded, but the old code is still cached. Restart to be safe.");
                } else {
                    tell(ChatColor.GREEN + "PrisonCore updated " + ChatColor.GRAY + oldVersion + " -> "
                            + ChatColor.GREEN + newVersion + ChatColor.GRAY + " (" + target.getName() + ")");
                }
            } catch (Throwable t) {
                Bukkit.getLogger().severe("[PrisonCore] Live update failed: " + t);
                t.printStackTrace();
                tell(ChatColor.RED + "Live update failed: " + t.getMessage()
                        + ChatColor.GRAY + ". Restart the server to finish loading the new version.");
            }
        }

        private void tell(String message) {
            Bukkit.getConsoleSender().sendMessage("[PrisonCore] " + message);
            if (sender != null && sender != Bukkit.getConsoleSender()) {
                sender.sendMessage(message);
            }
        }

        /** Bukkit keeps a global name -> class cache; remove everything that came from the old jar. */
        private static void purgeClassCache(Object pluginLoader, ClassLoader oldLoader) {
            Class<?> type = pluginLoader.getClass();
            while (type != null && type != Object.class) {
                for (Field f : type.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object value = f.get(pluginLoader);
                        if (!(value instanceof Map)) {
                            continue;
                        }
                        Iterator<?> it = ((Map<?, ?>) value).values().iterator();
                        while (it.hasNext()) {
                            Object v = it.next();
                            if (v instanceof Class && ((Class<?>) v).getClassLoader() == oldLoader) {
                                it.remove();
                            }
                        }
                    } catch (Exception ignored) {
                        // not a map we can edit; skip it
                    }
                }
                type = type.getSuperclass();
            }
        }

        /** Updates command suggestions for online players (Paper/Spigot 1.13+). */
        private static void syncCommands() {
            try {
                Method m = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
                m.setAccessible(true);
                m.invoke(Bukkit.getServer());
            } catch (Exception ignored) {
                // only affects tab completion until players rejoin
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void addJars(List<File> out, File dir) {
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                out.add(f);
            }
        }
    }

    private boolean isPrisonCore(File jar) {
        try {
            return NAME.equals(plugin.getPluginLoader().getPluginDescription(jar).getName());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static Field field(Class<?> type, String name) {
        Class<?> t = type;
        while (t != null && t != Object.class) {
            try {
                Field f = t.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                t = t.getSuperclass();
            }
        }
        return null;
    }
}
