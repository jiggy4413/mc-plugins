package io.github.jiggy4413.prisoncore.mines;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs PrisonCore's mines: reads mines.yml, keeps a live count of how much of each mine is mined,
 * and refills mines in small batches (one mine at a time) when they reach their reset percentage
 * or their timer runs out.
 */
public final class MineManager {

    public static final String FILE = "mines.yml";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    /** True when mines.yml couldn't be read at startup; saving would then wipe the user's file. */
    private boolean loadFailed;
    private final Map<String, Mine> mines = new LinkedHashMap<String, Mine>();
    /** Read-only copy for other threads (PlaceholderAPI may ask from async scoreboards). */
    private volatile Map<String, Mine> snapshot = Collections.emptyMap();
    /** mines.yml's last-modified time when we loaded or saved it. */
    private long fileStamp;

    private final Deque<Mine> queue = new ArrayDeque<Mine>();
    private ResetJob job;
    private BukkitTask tickTask;
    private int tickCounter;
    private int recountCursor;

    // settings
    private double defaultResetAt;
    private int defaultTimerMinutes;
    private double defaultTimerMinimum;
    private int warningSeconds;
    private long budgetNanos;
    private int recountSeconds;
    private boolean resetOnStartup;
    private boolean bossBarEnabled;
    private int bossBarRange;

    public MineManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE);
    }

    // =================================================================== loading

    /** Loads mines.yml. Returns a problem description, or null when it loaded fine. */
    public String load() {
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
            plugin.getLogger().severe(FILE + " has a mistake: " + msg);
            if (config != null) {
                loadFailed = true;   // don't let /pmine overwrite the user's edits with old settings
                return FILE + " has a mistake, kept the old settings: " + msg;
            }
            loaded = new YamlConfiguration();
            loadFailed = true;
        } 
        if (loaded.getConfigurationSection("mines") != null || loaded.getConfigurationSection("settings") != null) {
            loadFailed = false;
        }
        fileStamp = file.lastModified();
        // finish a running reset before swapping settings
        finishCurrentJob();

        Map<String, Mine> old = new LinkedHashMap<String, Mine>(mines);
        removeBossBars();
        mines.clear();
        queue.clear();
        config = loaded;
        readSettings();

        ConfigurationSection section = config.getConfigurationSection("mines");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Mine mine = readMine(key.toLowerCase(Locale.ROOT), s);
                Mine before = old.get(mine.id);
                if (before != null && before.sameRegionAs(mine)) {
                    mine.airNow = before.airNow;
                    mine.airBaseline = before.airBaseline;
                    mine.lastResetMillis = before.lastResetMillis;
                }
                mines.put(mine.id, mine);
            }
        }
        publish();
        return null;
    }

    private void readSettings() {
        ConfigurationSection s = settings();
        defaultResetAt = clamp(s.getDouble("reset-at-percent", 75), 1, 100);
        defaultTimerMinutes = Math.max(0, s.getInt("timer-minutes", 15));
        defaultTimerMinimum = clamp(s.getDouble("timer-minimum-mined", 5), 0, 100);
        warningSeconds = Math.max(0, s.getInt("warning-seconds", 5));
        budgetNanos = (long) (clamp(s.getDouble("max-ms-per-tick", 8), 1, 40) * 1_000_000L);
        recountSeconds = Math.max(5, s.getInt("recount-seconds", 30));
        resetOnStartup = s.getBoolean("reset-on-startup", false);
        bossBarEnabled = s.getBoolean("bossbar.enabled", true);
        bossBarRange = Math.max(0, s.getInt("bossbar.range", 8));
    }

    private static final ConfigurationSection EMPTY = new org.bukkit.configuration.MemoryConfiguration();

    private ConfigurationSection settings() {
        YamlConfiguration c = config;
        ConfigurationSection s = c == null ? null : c.getConfigurationSection("settings");
        return s != null ? s : EMPTY;
    }

    private void publish() {
        snapshot = Collections.unmodifiableMap(new LinkedHashMap<String, Mine>(mines));
    }

    /** Ids can't contain '.', which YAML would treat as nesting. */
    static String cleanId(String id) {
        return id.toLowerCase(Locale.ROOT).replace(".", "").replace(" ", "");
    }

    private Mine readMine(String id, ConfigurationSection s) {
        Mine m = new Mine(id);
        m.display = s.getString("display", "");
        m.worldName = s.getString("world", "");
        String region = s.getString("region", "");
        int[] r = parseInts(region, 6);
        if (r != null) {
            m.setRegion(r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        String spawn = s.getString("spawn", "");
        if (spawn != null && !spawn.trim().isEmpty()) {
            String[] p = spawn.split(",");
            try {
                double[] v = new double[5];
                for (int i = 0; i < 5; i++) {
                    v[i] = i < p.length ? Double.parseDouble(p[i].trim()) : 0;
                }
                m.spawn = v;
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Mine " + id + ": bad spawn '" + spawn + "', using the top of the mine.");
            }
        }
        m.resetAtPercent = clamp(s.getDouble("reset-at-percent", defaultResetAt), 1, 100);
        m.timerMinutes = Math.max(0, s.getInt("timer-minutes", defaultTimerMinutes));
        m.timerMinimumMined = clamp(s.getDouble("timer-minimum-mined", defaultTimerMinimum), 0, 100);
        m.fillMode = s.getBoolean("fill-mode", false);
        String surface = s.getString("surface", "");
        if (surface != null && !surface.trim().isEmpty()) {
            m.surface = parseBlock(surface);
            if (m.surface == null) {
                plugin.getLogger().warning("Mine " + id + ": unknown surface block '" + surface + "'.");
            }
        }
        ConfigurationSection blocks = s.getConfigurationSection("blocks");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                Material mat = parseBlock(key);
                if (mat == null) {
                    plugin.getLogger().warning("Mine " + id + ": '" + key + "' isn't a block in 1.16.5, skipped.");
                    continue;
                }
                m.composition.add(mat, blocks.getDouble(key));
            }
        }
        if (m.hasRegion) {
            m.airBaseline = Math.round(m.volume() * m.composition.airShare());
        }
        return m;
    }

    /** Accepts STONE, stone, minecraft:stone and minecraft:stone[...]. */
    static Material parseBlock(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        int bracket = n.indexOf('[');
        if (bracket >= 0) {
            n = n.substring(0, bracket);
        }
        Material m = Material.matchMaterial(n);
        return m != null && m.isBlock() ? m : null;
    }

    private static int[] parseInts(String text, int count) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != count) {
            return null;
        }
        int[] out = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    // =================================================================== saving (from commands)

    Mine getOrCreate(String id) {
        String key = cleanId(id);
        Mine m = mines.get(key);
        if (m == null) {
            m = new Mine(key);
            m.resetAtPercent = defaultResetAt;
            m.timerMinutes = defaultTimerMinutes;
            m.timerMinimumMined = defaultTimerMinimum;
            mines.put(key, m);
            publish();
        }
        return m;
    }

    void setRegion(Mine m, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (m == job()) {
            finishCurrentJob();
        }
        queue.remove(m);
        m.queued = false;
        m.warningLeft = -1;
        m.worldName = world.getName();
        m.setRegion(x1, y1, z1, x2, y2, z2);
        m.airBaseline = Math.round(m.volume() * m.composition.airShare());
        m.airNow = -1;
        m.countGeneration++;
        m.counting = false;
        ConfigurationSection s = mineSection(m.id);
        s.set("world", m.worldName);
        s.set("region", m.regionString());
    }

    void setSpawn(Mine m, Location loc) {
        m.spawn = new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        mineSection(m.id).set("spawn", String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
    }

    /** Used by the MineResetLite importer. */
    void importMine(String id, String world, int[] region, Map<Material, Double> blocks, boolean fillMode,
                    Material surface, double[] spawn, Double resetPercent, Integer timerMinutes) {
        Mine m = getOrCreate(id);
        m.composition = new Composition();
        ConfigurationSection s = mineSection(id);
        ConfigurationSection b = s.createSection("blocks");
        for (Map.Entry<Material, Double> e : blocks.entrySet()) {
            m.composition.add(e.getKey(), e.getValue());
            b.set(e.getKey().name(), round2(e.getValue()));
        }
        if (s.getString("display", "").isEmpty()) {
            s.set("display", "&7Mine " + id.toUpperCase(Locale.ROOT));
            m.display = s.getString("display");
        }
        m.fillMode = fillMode;
        s.set("fill-mode", fillMode);
        m.surface = surface;
        s.set("surface", surface == null ? "" : surface.name());
        if (resetPercent != null && resetPercent > 0 && resetPercent <= 100) {
            m.resetAtPercent = resetPercent;
            s.set("reset-at-percent", round2(resetPercent));
        }
        World w = Bukkit.getWorld(world);
        if (w != null) {
            setRegion(m, w, region[0], region[1], region[2], region[3], region[4], region[5]);
        } else {
            m.worldName = world;
            m.setRegion(region[0], region[1], region[2], region[3], region[4], region[5]);
            m.airBaseline = Math.round(m.volume() * m.composition.airShare());
            m.airNow = -1;
            s.set("world", world);
            s.set("region", m.regionString());
        }
        if (spawn != null) {
            m.spawn = spawn;
            s.set("spawn", String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                    spawn[0], spawn[1], spawn[2], spawn[3], spawn[4]));
        }
        if (timerMinutes != null && timerMinutes > 0) {
            m.timerMinutes = timerMinutes;
            s.set("timer-minutes", timerMinutes);
        }
    }

    private ConfigurationSection mineSection(String id) {
        ConfigurationSection s = config.getConfigurationSection("mines." + id);
        return s != null ? s : config.createSection("mines." + id);
    }

    String save() {
        if (loadFailed) {
            return FILE + " has a mistake, so it wasn't overwritten. Fix it, then /prisoncore reload.";
        }
        if (file.exists() && file.lastModified() != fileStamp) {
            return FILE + " was edited since the last reload, so it wasn't overwritten. Run /prisoncore reload, then repeat the command.";
        }
        try {
            config.save(file);
            fileStamp = file.lastModified();
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    // =================================================================== lifecycle

    public void start() {
        stopTask();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 40L, 1L);
        if (resetOnStartup) {
            // give Multiverse time to load the worlds, then refill everything
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    for (Mine m : mines.values()) {
                        queueReset(m);
                    }
                }
            }, 100L);
        }
    }

    /** Finishes a reset that's halfway done, so a mine is never left half full, then stops. */
    public void shutdown() {
        finishCurrentJob();
        stopTask();
        queue.clear();
        removeBossBars();
    }

    private void stopTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void finishCurrentJob() {
        if (job != null) {
            job.step(Long.MAX_VALUE);
            completeJob();
        }
    }

    // =================================================================== the tick

    private void tick() {
        tickCounter++;
        runJob();
        if (tickCounter % 20 == 0) {
            everySecond();
        }
        // spread background counts out: one mine every few ticks
        if (tickCounter % 4 == 0) {
            recountNext();
        }
    }

    private void runJob() {
        if (job != null && tickCounter % 5 == 0) {
            moveOut(job.mine);   // anyone who walked or jumped in during the refill
        }
        if (job == null) {
            Mine next = queue.poll();
            if (next == null) {
                return;
            }
            next.queued = false;
            startJob(next);
            if (job == null) {
                return;
            }
        }
        if (job.step(System.nanoTime() + budgetNanos)) {
            completeJob();
        } else {
            job.mine.resetProgress = job.progress();
        }
    }

    private void startJob(Mine m) {
        World w = m.world();
        if (w == null || !m.hasRegion || m.composition.isEmpty()) {
            if (m.hasRegion) {
                plugin.getLogger().warning("Can't reset mine " + m.id + ": "
                        + (w == null ? "world '" + m.worldName + "' isn't loaded" : "it has no blocks set"));
            }
            m.resetting = false;
            m.lastResetMillis = System.currentTimeMillis();
            return;
        }
        m.resetting = true;
        m.resetProgress = 0;
        m.countGeneration++;          // throw away any count still running
        m.counting = false;
        moveOut(m);
        message(m, "reset-started", null);
        job = new ResetJob(m, w);
    }

    private void completeJob() {
        Mine m = job.mine;
        m.airNow = job.airAfter();
        m.airBaseline = job.airAfter();
        m.lastResetMillis = System.currentTimeMillis();
        m.resetting = false;
        m.resetProgress = 1;
        job = null;
        // anyone still inside (e.g. a mine with no spawn set) stands on the full mine now
        World w = m.world();
        if (w != null) {
            Location top = m.topLocation();
            for (Player p : w.getPlayers()) {
                if (m.isInside(p.getLocation())) {
                    p.teleport(top);
                }
            }
        }
        message(m, "reset-done", null);
    }

    /** Teleports players inside a mine to its spawn, or to the world spawn if none is set. */
    private void moveOut(Mine m) {
        World w = m.world();
        if (w == null) {
            return;
        }
        Location safe = m.spawn != null ? m.safeLocation() : w.getSpawnLocation();
        if (safe == null || m.isInside(safe)) {
            safe = null;   // a spawn inside the mine isn't safe: leave them, they're lifted out at the end
        }
        for (Player p : w.getPlayers()) {
            if (m.isInside(p.getLocation()) && safe != null) {
                p.teleport(safe);
            }
        }
    }

    private Mine job() {
        return job == null ? null : job.mine;
    }

    private void everySecond() {
        long now = System.currentTimeMillis();
        for (Mine m : mines.values()) {
            if (!m.hasRegion || m.resetting || m.queued || m.composition.isEmpty() || m.world() == null) {
                m.warningLeft = -1;
                continue;
            }
            if (m.warningLeft >= 0) {
                if (m.warningLeft == 0) {
                    m.warningLeft = -1;
                    queueReset(m);
                } else {
                    message(m, "reset-warning", String.valueOf(m.warningLeft));
                    m.warningLeft--;
                }
                continue;
            }
            if (m.isCounted() && m.minedPercent() >= m.resetAtPercent) {
                beginWarning(m);
                continue;
            }
            if (m.timerMinutes > 0 && now - m.lastResetMillis >= m.timerMinutes * 60000L) {
                if (m.isCounted() && m.minedPercent() >= m.timerMinimumMined) {
                    beginWarning(m);
                } else {
                    m.lastResetMillis = now;   // barely touched: skip this round
                }
            }
        }
        updateBossBars();
    }

    private void beginWarning(Mine m) {
        if (warningSeconds <= 0) {
            queueReset(m);
        } else {
            m.warningLeft = warningSeconds;
        }
    }

    /** Adds a mine to the reset line. Mines reset one at a time. */
    public void queueReset(Mine m) {
        if (m == null || !m.hasRegion || m.resetting || m.queued || m.composition.isEmpty()) {
            return;
        }
        m.warningLeft = -1;
        m.queued = true;
        queue.add(m);
    }

    // =================================================================== counting

    /** Called by the listener whenever blocks inside a mine change. */
    void blocksChanged(Mine m, int airDelta) {
        if (m.airNow >= 0) {
            m.airNow = Math.max(0, Math.min(m.volume(), m.airNow + airDelta));
        }
        if (m.counting) {
            m.pendingDelta += airDelta;
        }
    }

    private void recountNext() {
        if (mines.isEmpty()) {
            return;
        }
        List<Mine> list = new ArrayList<Mine>(mines.values());
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            recountCursor = (recountCursor + 1) % list.size();
            Mine m = list.get(recountCursor);
            if (!m.hasRegion || m.resetting || m.counting) {
                continue;
            }
            boolean due = !m.isCounted() || now - m.lastCountMillis >= recountSeconds * 1000L;
            if (due && beginCount(m)) {
                return; // one count started this time
            }
        }
    }

    /**
     * Copies the mine's chunks (fast) and counts the air in them on another thread, so it never
     * slows the server. Fixes drift from things that break blocks without events (e.g. some enchants).
     */
    private boolean beginCount(final Mine m) {
        World w = m.world();
        if (w == null) {
            return false;
        }
        int cx1 = m.minX >> 4, cx2 = m.maxX >> 4, cz1 = m.minZ >> 4, cz2 = m.maxZ >> 4;
        boolean firstCount = !m.isCounted();
        final List<ChunkSnapshot> snaps = new ArrayList<ChunkSnapshot>();
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                if (!firstCount && !w.isChunkLoaded(cx, cz)) {
                    // nobody is near this mine, so nothing has changed; check again later
                    m.lastCountMillis = System.currentTimeMillis();
                    return false;
                }
                snaps.add(w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }
        m.counting = true;
        m.pendingDelta = 0;
        m.lastCountMillis = System.currentTimeMillis();
        final int generation = m.countGeneration;
        final int minX = m.minX, minY = m.minY, minZ = m.minZ, maxX = m.maxX, maxY = m.maxY, maxZ = m.maxZ;
        final JavaPlugin owner = plugin;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                long air = 0;
                boolean ok = true;
                try {
                    for (ChunkSnapshot snap : snaps) {
                        int baseX = snap.getX() << 4;
                        int baseZ = snap.getZ() << 4;
                        int x1 = Math.max(minX, baseX), x2 = Math.min(maxX, baseX + 15);
                        int z1 = Math.max(minZ, baseZ), z2 = Math.min(maxZ, baseZ + 15);
                        for (int y = minY; y <= maxY; y++) {
                            for (int x = x1; x <= x2; x++) {
                                for (int z = z1; z <= z2; z++) {
                                    if (snap.getBlockType(x & 15, y, z & 15).isAir()) {
                                        air++;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    ok = false;
                }
                final long counted = air;
                final boolean success = ok;
                if (!owner.isEnabled()) {
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(owner, new Runnable() {
                        @Override
                        public void run() {
                            if (m.countGeneration != generation || !m.counting) {
                                return;   // a reset started meanwhile; its own result wins
                            }
                            m.counting = false;
                            if (success) {
                                m.airNow = Math.max(0, Math.min(m.volume(), counted + m.pendingDelta));
                            } else {
                                owner.getLogger().warning("Couldn't count mine " + m.id + "; will try again.");
                            }
                            m.pendingDelta = 0;
                        }
                    });
                } catch (Throwable ignored) {
                    // the plugin was disabled while counting
                }
            }
        });
        return true;
    }

    // =================================================================== lookups

    public Collection<Mine> mines() {
        return snapshot.values();
    }

    public Mine get(String id) {
        return id == null ? null : snapshot.get(cleanId(id));
    }

    public Mine mineAt(Block block) {
        World w = block.getWorld();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (Mine m : snapshot.values()) {
            if (m.contains(w, x, y, z)) {
                return m;
            }
        }
        return null;
    }

    /** The mine a player is in or standing next to (for boss bars and placeholders). */
    public Mine mineNear(Location loc) {
        for (Mine m : snapshot.values()) {
            if (m.isNear(loc, bossBarRange)) {
                return m;
            }
        }
        return null;
    }

    public ConfigurationSection text() {
        return settings();
    }

    // =================================================================== messages & boss bars

    private void message(Mine m, String key, String seconds) {
        String raw = settings().getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        boolean actionBar = raw.startsWith("[actionbar]");
        String body = actionBar ? raw.substring("[actionbar]".length()).trim() : raw;
        World w = m.world();
        if (w == null) {
            return;
        }
        for (Player p : w.getPlayers()) {
            if (!m.isNear(p.getLocation(), bossBarRange)) {
                continue;
            }
            String text = Text.color(p, body.replace("{mine}", m.display())
                    .replace("{seconds}", seconds == null ? "" : seconds));
            if (actionBar) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
            } else {
                p.sendMessage(text);
            }
        }
    }

    private void updateBossBars() {
        if (!bossBarEnabled) {
            return;
        }
        // which mine each online player is at
        Map<Mine, List<Player>> viewers = new LinkedHashMap<Mine, List<Player>>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Mine m = mineNear(p.getLocation());
            if (m == null) {
                continue;
            }
            List<Player> list = viewers.get(m);
            if (list == null) {
                list = new ArrayList<Player>();
                viewers.put(m, list);
            }
            list.add(p);
        }
        for (Mine m : mines.values()) {
            List<Player> want = viewers.get(m);
            if (want == null || want.isEmpty()) {
                if (m.bossBar != null) {
                    m.bossBar.removeAll();
                }
                continue;
            }
            BossBar bar = m.bossBar;
            if (bar == null) {
                bar = Bukkit.createBossBar("", color(), style());
                m.bossBar = bar;
            }
            fillBossBar(m, bar);
            for (Player p : new ArrayList<Player>(bar.getPlayers())) {
                if (!want.contains(p)) {
                    bar.removePlayer(p);
                }
            }
            for (Player p : want) {
                if (!bar.getPlayers().contains(p)) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    private void fillBossBar(Mine m, BossBar bar) {
        ConfigurationSection s = settings();
        String title;
        double progress;
        if (m.resetting || m.queued) {
            title = s.getString("bossbar.resetting-title", "{mine} &7- &eResetting... {progress}%");
            progress = m.resetting ? m.resetProgress : 0;
        } else if (!m.isCounted()) {
            title = s.getString("bossbar.counting-title", "{mine} &7- &7Checking...");
            progress = 0;
        } else {
            title = s.getString("bossbar.title", "{mine} &7- &f{mined}% &7mined");
            progress = Math.min(1, m.minedPercent() / m.resetAtPercent);
        }
        title = title.replace("{mine}", m.display())
                .replace("{mined}", String.valueOf((int) Math.floor(m.minedPercent())))
                .replace("{remaining}", String.valueOf((int) Math.ceil(100 - m.minedPercent())))
                .replace("{reset-at}", String.valueOf((int) m.resetAtPercent))
                .replace("{progress}", String.valueOf((int) Math.floor(m.resetProgress * 100)))
                .replace("{timer}", formatTimer(m));
        bar.setTitle(Text.color(title));
        bar.setProgress(Math.max(0, Math.min(1, progress)));
    }

    private BarColor color() {
        try {
            return BarColor.valueOf(settings().getString("bossbar.color", "YELLOW").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle style() {
        try {
            return BarStyle.valueOf(settings().getString("bossbar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarStyle.SEGMENTED_10;
        }
    }

    private void removeBossBars() {
        for (Mine m : mines.values()) {
            if (m.bossBar != null) {
                m.bossBar.removeAll();
                m.bossBar = null;
            }
        }
    }

    public String formatTimer(Mine m) {
        long ms = m.millisUntilTimer();
        if (ms < 0) {
            return settings().getString("placeholders.timer-off", "Off");
        }
        long sec = ms / 1000;
        return sec >= 60 ? (sec / 60) + "m " + (sec % 60) + "s" : sec + "s";
    }

    // =================================================================== small helpers

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
