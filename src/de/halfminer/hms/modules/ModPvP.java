package de.halfminer.hms.modules;

import de.halfminer.hms.enums.HandlerType;
import de.halfminer.hms.handlers.HanTitles;
import de.halfminer.hms.interfaces.Sweepable;
import de.halfminer.hms.util.Language;
import net.minecraft.server.v1_9_R1.EntityFishingHook;
import net.minecraft.server.v1_9_R1.EntityPlayer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_9_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * - Strength potions damage nerfed
 * - Bow spamming disabled
 * - Killstreak via actionbar
 * - Sounds on kill/death
 * - Remove effects on teleport
 */
@SuppressWarnings("unused")
public class ModPvP extends HalfminerModule implements Listener, Sweepable {

    private final HanTitles titleHandler = (HanTitles) hms.getHandler(HandlerType.TITLES);

    private int thresholdUntilShown;

    private Map<Player, Long> lastBowShot = new HashMap<>();
    private final Map<UUID, Integer> killStreak = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onAttackReduceStrength(EntityDamageByEntityEvent e) {

        Entity damager = e.getDamager();
        if (damager.hasPermission("hms.bypass.pvp")) return;

        if (damager instanceof Player && e.getEntity() instanceof Player) {

            for (PotionEffect effect : ((Player) damager).getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.INCREASE_DAMAGE)) {

                    double newDamage = e.getDamage(EntityDamageEvent.DamageModifier.BASE)
                            - 1.5d * (effect.getAmplifier() + 1);
                    double damageRatio = newDamage / e.getDamage(EntityDamageEvent.DamageModifier.BASE);

                    e.setDamage(EntityDamageEvent.DamageModifier.BASE, newDamage);
                    e.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                            e.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * damageRatio);
                    e.setDamage(EntityDamageEvent.DamageModifier.MAGIC,
                            e.getDamage(EntityDamageEvent.DamageModifier.MAGIC) * damageRatio);
                    e.setDamage(EntityDamageEvent.DamageModifier.RESISTANCE,
                            e.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE) * damageRatio);
                    e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING,
                            e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) * damageRatio);

                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void antiBowSpam(EntityShootBowEvent e) {

        if (!e.getEntity().hasPermission("hms.bypass.pvp") && e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            long currentTime = System.currentTimeMillis();
            if (lastBowShot.containsKey(p) && lastBowShot.get(p) + 1000 > currentTime) {
                e.setCancelled(true);
            } else lastBowShot.put(p, currentTime);
        }
    }

    /*
     * Temporary fix for the rod, will be removed after WorldGuard implements safe
     */
    @EventHandler
    public void disablePullEffect(ProjectileHitEvent e) {

        if (!(e.getEntity() instanceof FishHook)) {
            return;
        }

        Player shooter = (Player) e.getEntity().getShooter();
        EntityPlayer entityPlayer = ((CraftPlayer) shooter).getHandle();
        EntityFishingHook hook = entityPlayer.hookedFish;
        for (Entity entity : e.getEntity().getNearbyEntities(0.35D, 0.35D, 0.35D)) {

            if (((entity instanceof Player)) && (!entity.getName().equals(shooter.getName()))) {

                if (hook != null) {
                    hook.hooked = null;
                    hook.die();
                }
                entityPlayer.hookedFish = null;
                return;
            }
        }
    }

    @EventHandler
    public void deathSoundsHealStreaks(PlayerDeathEvent e) {

        e.setDeathMessage("");

        final Player killer = e.getEntity().getKiller();
        final Player victim = e.getEntity();
        if (killer != null && killer != e.getEntity()) {

            // Heal and play sound
            if (!killer.isDead()) killer.setHealth(killer.getMaxHealth());
            scheduler.runTaskLaterAsynchronously(hms, new Runnable() {
                @Override
                public void run() {
                    killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                }
            }, 5);

            UUID killerUid = killer.getUniqueId();
            killStreak.remove(victim.getUniqueId());

            final int streak;
            if (killStreak.containsKey(killerUid)) streak = this.killStreak.get(killerUid) + 1;
            else streak = 1;

            killStreak.put(killerUid, streak);

            if (streak > thresholdUntilShown || streak % 5 == 0) {
                scheduler.runTaskLater(hms, new Runnable() {
                    @Override
                    public void run() {
                        titleHandler.sendActionBar(null, Language.getMessagePlaceholders("modPvPKillStreak", false,
                                "%PLAYER%", killer.getName(), "%STREAK%", String.valueOf(streak)));
                    }
                }, 0L);
            }
        } else {
            victim.playSound(e.getEntity().getLocation(), Sound.AMBIENT_CAVE, 1.0f, 1.4f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void teleportRemoveEffects(PlayerTeleportEvent e) {

        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (!p.hasPermission("hms.bypass.pvp") && (!from.getWorld().equals(to.getWorld()) || from.distance(to) > 100.0d)) {
            p.removePotionEffect(PotionEffectType.JUMP);
            p.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
            p.removePotionEffect(PotionEffectType.LEVITATION);
        }
    }

    @Override
    public void loadConfig() {

        thresholdUntilShown = hms.getConfig().getInt("pvp.streakThreshold", 30);
    }

    @Override
    public void sweep() {
        lastBowShot = new HashMap<>();
    }
}
