package io.github.jiggy4413.prisoncore.enchants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The Valentines weapon enchants (crate-only, 5 levels each; the strength per level is in enchants.yml).
 * Thornheart: Rosebleed (sword), Lacerate (axe), Impale (spear), Reap (scythe), Thorn Arrow (bow), Thornwall (shield).
 * Cupid: Lovestruck (sword), Heartsplitter (axe), Smitten (spear), Soul Harvest (scythe), Cupid's Arrow (bow),
 * Sanctuary (shield). The pickaxe ones (Creeping Vines, Lucky Charm) are with the other pickaxe enchants.
 */
public final class WeaponEnchants implements Listener {

    private final Enchants enchants;
    /** Who is bleeding from Rosebleed / Thorn Arrow, and until when (Lacerate hits these harder). */
    private final Map<UUID, Long> bleedingUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Long> sanctuaryAt = new HashMap<UUID, Long>();

    public WeaponEnchants(Enchants enchants) {
        this.enchants = enchants;
    }

    private ConfigurationSection settings(String name) {
        ConfigurationSection s = enchants.config().getConfigurationSection("enchant-settings." + name);
        return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
    }

    private double strength(String id, int level) {
        Enchants.Def d = enchants.def(Enchants.Category.SWORD, id);
        return d == null ? 0 : d.effect(level);
    }

    private int held(Player p, String id) {
        return enchants.heldLevel(p, Enchants.Category.SWORD, id);
    }

    /** Level of an enchant on a shield the player is holding in either hand. */
    private int shield(Player p, String id) {
        Enchants.Def d = enchants.def(Enchants.Category.SWORD, id);
        if (d == null) {
            return 0;
        }
        int best = 0;
        for (ItemStack it : new ItemStack[] {p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()}) {
            if (it != null && it.getType() == Material.SHIELD) {
                best = Math.max(best, enchants.level(it, d));
            }
        }
        return best;
    }

    private static boolean roll(double percent) {
        return percent > 0 && ThreadLocalRandom.current().nextDouble() * 100 < percent;
    }

    private static boolean melee(EntityDamageEvent.DamageCause c) {
        return c == EntityDamageEvent.DamageCause.ENTITY_ATTACK || c == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }

    private void effect(LivingEntity t, PotionEffectType type, double seconds, int amp) {
        t.addPotionEffect(new PotionEffect(type, (int) Math.round(seconds * 20), amp, false, true, true), true);
    }

    // ================================================================== bleeding (Rosebleed, Thorn Arrow)

    boolean bleeding(LivingEntity e) {
        Long until = bleedingUntil.get(e.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Starts bleeding, or adds time to a bleed that's already running. */
    private void bleed(final LivingEntity target, String settingsName) {
        ConfigurationSection s = settings(settingsName);
        final double perSecond = s.getDouble("damage-per-second", 1);
        long now = System.currentTimeMillis();
        boolean already = bleeding(target);
        long add = (already ? s.getLong("extra-seconds", 2) : s.getLong("seconds", 3)) * 1000L;
        long until = Math.max(now, already ? bleedingUntil.get(target.getUniqueId()) : now) + add;
        long cap = now + s.getLong("max-seconds", 8) * 1000L;
        bleedingUntil.put(target.getUniqueId(), Math.min(until, cap));
        if (target instanceof Player) {
            enchants.actionBar((Player) target, already ? "&4Your bleeding gets worse!" : "&cYou're bleeding!");
        }
        if (already) {
            return;   // the running timer keeps going until the new end time
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bleeding(target) || target.isDead() || !target.isValid()) {
                    bleedingUntil.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                target.damage(perSecond);   // no attacker, so it can't set off other enchants
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0),
                        8, 0.2, 0.4, 0.2, Material.REDSTONE_BLOCK.createBlockData());
            }
        }.runTaskTimer(enchants.plugin(), 20L, 20L);
    }

    // ================================================================== melee: damage changes first

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity) || !melee(event.getCause())) {
            return;
        }
        Player p = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        int lac = held(p, "lacerate");
        if (lac > 0 && bleeding(target)) {
            event.setDamage(event.getDamage() * (1 + strength("lacerate", lac) / 100.0));
        }
        int split = held(p, "heartsplitter");
        if (split > 0) {
            double max = target.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (target.getHealth() >= max - 0.01) {
                event.setDamage(event.getDamage() * (1 + strength("heartsplitter", split) / 100.0));
                target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
            }
        }
    }

    // ================================================================== melee: after the hit

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitEffects(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity) || !melee(event.getCause())) {
            return;
        }
        Player p = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        int rose = held(p, "rosebleed");
        if (rose > 0 && roll(strength("rosebleed", rose))) {
            bleed(target, "rosebleed");
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_BREAK, 0.8f, 0.8f);
        }
        int imp = held(p, "impale");
        if (imp > 0 && roll(strength("impale", imp))) {
            ConfigurationSection s = settings("impale");
            effect(target, PotionEffectType.SLOWNESS, s.getDouble("seconds", 1.5), Math.max(1, s.getInt("slowness-level", 4)) - 1);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 0.9f, 0.7f);
            if (target instanceof Player) {
                enchants.actionBar((Player) target, "&cYou've been impaled!");
            }
        }
        int love = held(p, "lovestruck");
        if (love > 0 && roll(strength("lovestruck", love))) {
            effect(target, PotionEffectType.WEAKNESS, settings("lovestruck").getDouble("seconds", 3), 0);
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3);
            if (target instanceof Player) {
                enchants.actionBar((Player) target, "&dYou're lovestruck!");
            }
        }
        int smit = held(p, "smitten");
        if (smit > 0 && roll(strength("smitten", smit))) {
            double secs = settings("smitten").getDouble("seconds", 2);
            effect(target, PotionEffectType.SLOWNESS, secs, 0);
            effect(p, PotionEffectType.SPEED, secs, 0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.8f);
        }
        int soul = held(p, "soul-harvest");
        if (soul > 0) {
            double heal = event.getFinalDamage() * strength("soul-harvest", soul) / 100.0;
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + heal));
        }
    }

    /** Reap: heal when you kill a player with the scythe. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        int reap = held(killer, "reap");
        if (reap <= 0) {
            return;
        }
        double max = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
        killer.setHealth(Math.min(max, killer.getHealth() + strength("reap", reap) * 2));   // strength is in hearts
        killer.getWorld().playSound(killer.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 0.6f, 0.6f);
        enchants.actionBar(killer, "&4Reaped!");
    }

    // ================================================================== bows

    private org.bukkit.NamespacedKey arrowKey(String name) {
        return new org.bukkit.NamespacedKey(enchants.plugin(), "arrow_" + name.replace('-', '_'));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Projectile)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            return;
        }
        for (String id : new String[] {"thorn-arrow", "cupids-arrow"}) {
            Enchants.Def d = enchants.def(Enchants.Category.SWORD, id);
            int lvl = d == null ? 0 : enchants.level(bow, d);
            if (lvl > 0) {
                event.getProjectile().getPersistentDataContainer().set(arrowKey(id), PersistentDataType.INTEGER, lvl);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        org.bukkit.persistence.PersistentDataContainer data = event.getDamager().getPersistentDataContainer();
        LivingEntity target = (LivingEntity) event.getEntity();
        Integer thorn = data.get(arrowKey("thorn-arrow"), PersistentDataType.INTEGER);
        if (thorn != null && roll(strength("thorn-arrow", thorn))) {
            bleed(target, "thorn-arrow");
        }
        Integer cupid = data.get(arrowKey("cupids-arrow"), PersistentDataType.INTEGER);
        if (cupid != null && roll(strength("cupids-arrow", cupid))) {
            ConfigurationSection s = settings("cupids-arrow");
            effect(target, PotionEffectType.GLOWING, s.getDouble("glow-seconds", 5), 0);
            effect(target, PotionEffectType.SLOWNESS, s.getDouble("slow-seconds", 2), 0);
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3);
        }
    }

    // ================================================================== shields

    /** Thornwall and Sanctuary: when a shield blocks a melee hit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getEntity();
        if (!p.isBlocking() || !event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                || event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) >= 0) {
            return;   // the shield didn't stop this hit
        }
        int wall = shield(p, "thornwall");
        if (wall > 0 && event.getDamager() instanceof LivingEntity && melee(event.getCause())) {
            LivingEntity hitter = (LivingEntity) event.getDamager();
            org.bukkit.damage.DamageSource src = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.THORNS)
                    .withCausingEntity(p).withDirectEntity(p).build();
            hitter.damage(strength("thornwall", wall) * 2, src);   // strength is in hearts
            org.bukkit.util.Vector push = hitter.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 0.0001) {
                hitter.setVelocity(push.normalize().multiply(settings("thornwall").getDouble("knockback", 0.6)).setY(0.25));
            }
            hitter.getWorld().playSound(hitter.getLocation(), Sound.ENCHANT_THORNS_HIT, 0.9f, 0.8f);
        }
        int sanct = shield(p, "sanctuary");
        if (sanct > 0) {
            long now = System.currentTimeMillis();
            Long last = sanctuaryAt.get(p.getUniqueId());
            if (last == null || now - last >= settings("sanctuary").getLong("cooldown-seconds", 8) * 1000L) {
                sanctuaryAt.put(p.getUniqueId(), now);
                effect(p, PotionEffectType.REGENERATION, strength("sanctuary", sanct), 0);   // strength is seconds
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        bleedingUntil.remove(event.getPlayer().getUniqueId());
        sanctuaryAt.remove(event.getPlayer().getUniqueId());
    }
}
