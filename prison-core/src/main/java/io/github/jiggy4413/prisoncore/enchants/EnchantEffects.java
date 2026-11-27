package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.mines.MineManager;
import io.github.jiggy4413.prisoncore.tokens.Tokens;
import io.github.jiggy4413.prisoncore.util.Text;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * What the prison enchants do (everything that isn't a normal Minecraft enchant):
 * sword: Bleed, Freeze, Executioner, Thunder, Bounty Hunter, Head Hunter (Lifesteal is in Enchants);
 * armor: Dodge, Last Stand, Speed; pickaxe: Key Finder, Money Finder, XP Boost.
 * Each enchant's strength per level comes from its "strength:" list in enchants.yml.
 */
public final class EnchantEffects implements Listener {

    private final Enchants enchants;
    private final MineManager mines;
    private final Tokens tokens;
    private BukkitTask effectTask;

    /** Key Finder limits, per player: when the last key was found, and how many today. */
    private final Map<UUID, Long> lastKey = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> keysToday = new HashMap<UUID, Integer>();
    private LocalDate keyDay = LocalDate.now();
    /** Last Stand cooldown and Bounty Hunter "same victim" cooldown. */
    private final Map<UUID, Long> lastStandAt = new HashMap<UUID, Long>();
    private final Map<String, Long> bountyAt = new HashMap<String, Long>();

    public EnchantEffects(Enchants enchants, MineManager mines, Tokens tokens) {
        this.enchants = enchants;
        this.mines = mines;
        this.tokens = tokens;
    }

    public void start() {
        stop();
        effectTask = Bukkit.getScheduler().runTaskTimer(enchants.plugin(), new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    setMessage(p);
                    int speed = worn(p, "speed");
                    if (speed > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, speed - 1, true, false, false), true);
                    }
                }
            }
        }, 40L, 40L);
    }

    /** Full set the player had at the last check, to tell them when the set bonus turns on or off. */
    private final java.util.Map<java.util.UUID, String> lastSet = new java.util.HashMap<java.util.UUID, String>();

    private void setMessage(Player p) {
        String now = ArmorSets.fullSet(p);
        String before = lastSet.get(p.getUniqueId());
        if (now == null ? before == null : now.equals(before)) {
            return;
        }
        if (now == null) {
            lastSet.remove(p.getUniqueId());
        } else {
            lastSet.put(p.getUniqueId(), now);
        }
        org.bukkit.configuration.ConfigurationSection s = enchants.config().getConfigurationSection("set-bonus");
        String msg = now != null
                ? (s == null ? "&a&lSET BONUS ACTIVE" : s.getString("active", "&a&lSET BONUS ACTIVE"))
                : (s == null ? "&7Set bonus off" : s.getString("inactive", "&7Set bonus off"));
        if (msg != null && !msg.isEmpty()) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(io.github.jiggy4413.prisoncore.util.Text.color(msg)));
        }
    }

    public void stop() {
        lastSet.clear();
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
    }

    private ConfigurationSection settings(String name) {
        ConfigurationSection s = enchants.config().getConfigurationSection("enchant-settings." + name);
        return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
    }

    private Enchants.Def def(Enchants.Category c, String id) {
        return enchants.def(c, id);
    }

    private double strength(Enchants.Category c, String id, int level) {
        Enchants.Def d = def(c, id);
        return d == null ? 0 : d.effect(level);
    }

    private static boolean roll(double percent) {
        return percent > 0 && ThreadLocalRandom.current().nextDouble() * 100 < percent;
    }

    /** Highest level of an armor enchant across the four pieces the player wears. */
    private int worn(Player p, String id) {
        Enchants.Def d = def(Enchants.Category.ARMOR, id);
        if (d == null) {
            return 0;
        }
        int best = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack it = p.getInventory().getItem(slot);
            if (Enchants.category(it) == Enchants.Category.ARMOR && ArmorSets.active(p, it)) {
                best = Math.max(best, enchants.level(it, d));
            }
        }
        return best;
    }

    /** The player who caused the damage (directly, or by shooting). */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return null;
    }

    // ================================================================== sword: damage changes

    /** Executioner and Thunder change how much damage a hit does, so they run before armor math. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player p = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        int exec = enchants.heldLevel(p, Enchants.Category.SWORD, "executioner");
        if (exec > 0) {
            double max = target.getAttribute(Attribute.MAX_HEALTH).getValue();
            double below = settings("executioner").getDouble("below-health-percent", 30);
            if (target.getHealth() <= max * below / 100.0) {
                event.setDamage(event.getDamage() * (1 + strength(Enchants.Category.SWORD, "executioner", exec) / 100.0));
            }
        }

        int pierce = enchants.heldLevel(p, Enchants.Category.SWORD, "armor-pierce");
        if (pierce > 0 && roll(strength(Enchants.Category.SWORD, "armor-pierce", pierce))) {
            // this hit ignores armor points and protection enchants
            if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
            }
            if (event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
                event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
            }
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.6f);
            target.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
            enchants.actionBar(p, settings("armor-pierce").getString("message", "&b&lARMOR PIERCED!"));
        }

        int thunder = enchants.heldLevel(p, Enchants.Category.SWORD, "thunder");
        if (thunder > 0 && roll(strength(Enchants.Category.SWORD, "thunder", thunder))) {
            target.getWorld().strikeLightningEffect(target.getLocation()); // looks only, no fire
            event.setDamage(event.getDamage() + settings("thunder").getDouble("bonus-damage", 3));
        }
    }

    // ================================================================== sword + armor: after the hit

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitEffects(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player p = (Player) event.getDamager();
        final LivingEntity target = (LivingEntity) event.getEntity();

        int swift = enchants.heldLevel(p, Enchants.Category.SWORD, "swiftness");
        if (swift > 0 && roll(strength(Enchants.Category.SWORD, "swiftness", swift))) {
            int ticks = Math.max(1, settings("swiftness").getInt("seconds", 3)) * 20;
            int amp = Math.max(1, settings("swiftness").getInt("speed-level", 2)) - 1;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, amp, false, true, true), true);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.6f);
        }

        int bleed = enchants.heldLevel(p, Enchants.Category.SWORD, "bleed");
        if (bleed > 0 && roll(strength(Enchants.Category.SWORD, "bleed", bleed))) {
            final double perTick = settings("bleed").getDouble("damage-per-second", 1);
            final int seconds = Math.max(1, settings("bleed").getInt("seconds", 3));
            new BukkitRunnable() {
                int left = seconds;

                @Override
                public void run() {
                    if (left-- <= 0 || target.isDead() || !target.isValid()) {
                        cancel();
                        return;
                    }
                    target.damage(perTick); // no attacker, so it can't set off other enchants
                    target.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, target.getLocation().add(0, 1, 0),
                            8, 0.2, 0.4, 0.2, Material.REDSTONE_BLOCK.createBlockData());
                }
            }.runTaskTimer(enchants.plugin(), 20L, 20L);
            if (target instanceof Player) {
                enchants.actionBar((Player) target, "&cYou're bleeding!");
            }
        }

        int freeze = enchants.heldLevel(p, Enchants.Category.SWORD, "freeze");
        if (freeze > 0 && roll(strength(Enchants.Category.SWORD, "freeze", freeze))) {
            int ticks = Math.max(1, settings("freeze").getInt("seconds", 2)) * 20;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, false, true, true), true);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
        }
    }

    /** Dodge: a chance to take no damage from another player's hit (or arrow). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDodge(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || attacker(event.getDamager()) == null) {
            return;
        }
        Player victim = (Player) event.getEntity();
        int dodge = worn(victim, "dodge");
        if (dodge > 0 && roll(strength(Enchants.Category.ARMOR, "dodge", dodge))) {
            event.setCancelled(true);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.4f);
            enchants.actionBar(victim, "&bDodged!");
        }
    }

    /** Last Stand: Strength and Resistance for a few seconds when health drops low. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLowHealth(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getEntity();
        int lvl = worn(p, "last-stand");
        if (lvl <= 0) {
            return;
        }
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        double after = p.getHealth() - event.getFinalDamage();
        ConfigurationSection s = settings("last-stand");
        if (after <= 0 || after > max * s.getDouble("below-health-percent", 25) / 100.0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastStandAt.get(p.getUniqueId());
        if (last != null && now - last < s.getLong("cooldown-seconds", 60) * 1000L) {
            return;
        }
        lastStandAt.put(p.getUniqueId(), now);
        int ticks = (int) Math.round(strength(Enchants.Category.ARMOR, "last-stand", lvl) * 20);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0, false, true, true), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0, false, true, true), true);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.2f);
        enchants.actionBar(p, "&6&lLAST STAND!");
    }

    // ================================================================== bows: Freeze and Ender Shot

    private final Map<UUID, Long> enderShotAt = new HashMap<UUID, Long>();

    private org.bukkit.NamespacedKey arrowKey(String name) {
        return new org.bukkit.NamespacedKey(enchants.plugin(), "arrow_" + name);
    }

    /** Remembers the bow's enchants on the arrow, since the arrow outlives the bow being held. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Projectile)) {
            return;
        }
        Player p = (Player) event.getEntity();
        ItemStack bow = event.getBow();
        if (Enchants.category(bow) != Enchants.Category.SWORD) {
            return;
        }
        org.bukkit.persistence.PersistentDataContainer data = event.getProjectile().getPersistentDataContainer();
        Enchants.Def freeze = def(Enchants.Category.SWORD, "freeze");
        int fl = freeze == null ? 0 : enchants.level(bow, freeze);
        if (fl > 0) {
            data.set(arrowKey("freeze"), org.bukkit.persistence.PersistentDataType.INTEGER, fl);
        }
        Enchants.Def ender = def(Enchants.Category.SWORD, "ender-shot");
        int enderLevel = ender == null ? 0 : enchants.level(bow, ender);
        if (enderLevel > 0) {
            ConfigurationSection s = settings("ender-shot");
            if (s.getBoolean("only-while-sneaking", true) && !p.isSneaking()) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = enderShotAt.get(p.getUniqueId());
            // the enchant's strength is its cooldown in seconds (20/15/10); an old 1-level setup falls back to cooldown-seconds
            double perLevel = ender.effect(enderLevel);
            long cooldown = (long) ((perLevel > 1 ? perLevel : s.getDouble("cooldown-seconds", 10)) * 1000L);
            if (last != null && now - last < cooldown) {
                enchants.actionBar(p, "&7Ender Shot ready in &f" + ((cooldown - (now - last)) / 1000 + 1) + "s");
                return;
            }
            enderShotAt.put(p.getUniqueId(), now);
            data.set(arrowKey("ender"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 0.8f, 1f);
        }
    }

    /** Freeze from a bow: arrows that hit can slow the target. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Integer fl = event.getDamager().getPersistentDataContainer().get(arrowKey("freeze"), org.bukkit.persistence.PersistentDataType.INTEGER);
        if (fl != null && roll(strength(Enchants.Category.SWORD, "freeze", fl))) {
            LivingEntity target = (LivingEntity) event.getEntity();
            int ticks = Math.max(1, settings("freeze").getInt("seconds", 2)) * 20;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, false, true, true), true);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
        }
    }

    /** Ender Shot: the shooter is teleported where the arrow lands, like an ender pearl. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrowLand(org.bukkit.event.entity.ProjectileHitEvent event) {
        Projectile arrow = event.getEntity();
        if (!arrow.getPersistentDataContainer().has(arrowKey("ender"), org.bukkit.persistence.PersistentDataType.BYTE)
                || !(arrow.getShooter() instanceof Player)) {
            return;
        }
        Player p = (Player) arrow.getShooter();
        if (!p.isOnline() || !p.getWorld().equals(arrow.getWorld())) {
            return;
        }
        org.bukkit.Location to;
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            to = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
        } else if (event.getHitEntity() != null) {
            to = event.getHitEntity().getLocation();
        } else {
            to = arrow.getLocation();
        }
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        // ENDER_PEARL cause, so region plugins (WorldGuard's enderpearl flag) can block it like a pearl
        p.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, to.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        arrow.remove();
    }

    // ================================================================== sword: kills

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        int bounty = enchants.heldLevel(killer, Enchants.Category.SWORD, "bounty-hunter");
        if (bounty > 0) {
            String key = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = bountyAt.get(key);
            long cooldown = settings("bounty-hunter").getLong("same-player-cooldown-seconds", 300) * 1000L;
            if (last == null || now - last >= cooldown) {
                bountyAt.put(key, now);
                long amount = Math.round(strength(Enchants.Category.SWORD, "bounty-hunter", bounty));
                if (amount > 0) {
                    tokens.add(killer, amount);
                    killer.sendMessage(Text.color(killer, "&6Bounty Hunter: &e+" + Tokens.format(amount) + " tokens"));
                }
            }
        }
        int heads = enchants.heldLevel(killer, Enchants.Category.SWORD, "head-hunter");
        if (heads > 0 && roll(strength(Enchants.Category.SWORD, "head-hunter", heads))) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(victim);
            meta.setDisplayName(Text.color("&e" + victim.getName() + "'s Head"));
            head.setItemMeta(meta);
            event.getDrops().add(head);
        }
    }

    // ================================================================== pickaxe: mining

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || mines.mineAt(event.getBlock()) == null) {
            return; // these only work in mines
        }
        keyFinder(p);
        moneyFinder(p);
        int xp = enchants.heldLevel(p, Enchants.Category.PICKAXE, "xp-boost");
        if (xp > 0 && roll(strength(Enchants.Category.PICKAXE, "xp-boost", xp))) {
            p.giveExp(1); // goes through Mending like any other XP
        }
    }

    /**
     * Key Finder: a rare chance per block for a crate key. To stop fast pickaxes from farming keys,
     * there's a cooldown after every key and a daily limit per player.
     */
    private void keyFinder(Player p) {
        int lvl = enchants.heldLevel(p, Enchants.Category.PICKAXE, "key-finder");
        if (lvl <= 0) {
            return;
        }
        ConfigurationSection s = settings("key-finder");
        LocalDate today = LocalDate.now();
        if (!today.equals(keyDay)) {
            keyDay = today;
            keysToday.clear();
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastKey.get(id);
        if (last != null && now - last < s.getLong("cooldown-minutes", 15) * 60000L) {
            return;
        }
        Integer count = keysToday.get(id);
        if (count != null && count >= s.getInt("max-per-day", 6)) {
            return;
        }
        if (!roll(strength(Enchants.Category.PICKAXE, "key-finder", lvl))) {
            return;
        }
        List<String> commands = s.getStringList("commands");
        List<String> names = s.getStringList("key-names");
        if (commands.isEmpty()) {
            return;
        }
        String cmd = commands.get(Math.min(lvl, commands.size()) - 1).replace("{player}", p.getName());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        lastKey.put(id, now);
        keysToday.put(id, count == null ? 1 : count + 1);
        String keyName = names.isEmpty() ? "a crate key" : names.get(Math.min(lvl, names.size()) - 1);
        p.sendMessage(Text.color(p, s.getString("message", "&6&lKEY FINDER! &7You found {key}&7!").replace("{key}", keyName)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
    }

    private Object economy;
    private Method deposit;
    private boolean economyChecked;

    /** Money Finder: a small chance per block to find a money bonus (paid through Vault). */
    private void moneyFinder(Player p) {
        int lvl = enchants.heldLevel(p, Enchants.Category.PICKAXE, "money-finder");
        if (lvl <= 0 || !roll(settings("money-finder").getDouble("chance-percent", 0.5))) {
            return;
        }
        double amount = strength(Enchants.Category.PICKAXE, "money-finder", lvl);
        if (amount <= 0 || !pay(p, amount)) {
            return;
        }
        enchants.actionBar(p, "&aMoney Finder: +$" + new java.text.DecimalFormat("#,##0").format(amount));
    }

    private boolean pay(Player p, double amount) {
        if (!economyChecked || economy == null) {
            economyChecked = true;
            try {
                Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    deposit = eco.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
                }
            } catch (Throwable ignored) {
                economy = null;
            }
        }
        if (economy == null) {
            return false;
        }
        try {
            deposit.invoke(economy, p, amount);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
