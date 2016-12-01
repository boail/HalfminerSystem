package de.halfminer.hms.modules;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.halfminer.hms.enums.AttackSpeed;
import de.halfminer.hms.interfaces.Sweepable;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * - Halves PvP cooldown
 *   - Reduces damage immunity
 * - Strength potions damage nerfed
 * - Bow spamming disabled
 * - Disable hitting self with bow
 * - Killstreak via actionbar
 * - Sounds on kill/death
 * - Remove effects on teleport
 */
@SuppressWarnings("unused")
public class ModPvP extends HalfminerModule implements Listener, Sweepable {

    private int thresholdUntilShown;

    private final Cache<Player, Boolean> hasShotBow = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .weakKeys()
            .build();

    private final Map<UUID, Integer> killStreak = new HashMap<>();

    @EventHandler
    public void onInteractSetAttackSpeed(PlayerInteractEvent e) {
        if (e.getHand() != null && e.getHand().equals(EquipmentSlot.HAND))
            setAttackSpeed(e.getPlayer(), e.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryScrollSetAttackSpeed(PlayerItemHeldEvent e) {
        setAttackSpeed(e.getPlayer(), e.getPlayer().getInventory().getItem(e.getNewSlot()));
    }

    @EventHandler
    public void onLeaveResetAttackSpeed(PlayerQuitEvent e) {
        e.getPlayer().getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(AttackSpeed.getDefaultSpeed());
    }

    private void setAttackSpeed(Player p, ItemStack stack) {

        double setTo = AttackSpeed.getDefaultSpeed();

        if (stack != null) setTo += AttackSpeed.getSpeed(stack.getType());
        else setTo *= 2;

        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(setTo);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttackReduceStrengthAndLowerImmunity(EntityDamageByEntityEvent e) {

        if (!(e.getEntity() instanceof Player)) return;

        final Player damagee = (Player) e.getEntity();
        Player damager = e.getDamager() instanceof Player ? (Player) e.getDamager() : null;

        if ((damager != null && damager.hasPermission("hms.bypass.pvp"))
                || damagee.hasPermission("hms.bypass.pvp")) return;

        // reduce damage immunity
        scheduler.runTaskLater(hms, () -> {
            if (!e.isCancelled())
                // damage immunity only passes until 10/20 has been reached
                damagee.setNoDamageTicks(16);
        }, 0L);

        if (damager != null) {

            // nerf strength potions
            for (PotionEffect effect : damager.getActivePotionEffects()) {
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

        } else if (e.getDamager() instanceof Projectile) {
            // prevent self hit with bow
            e.setCancelled(e.getEntity().equals(((Projectile) e.getDamager()).getShooter()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void preventSelfHitWithBowCombust(EntityCombustByEntityEvent e) {

        if (e.getEntity() instanceof Player && e.getCombuster() instanceof Projectile)
            e.setCancelled(e.getEntity().equals(((Projectile) e.getCombuster()).getShooter()));
    }

    @EventHandler(ignoreCancelled = true)
    public void antiBowSpam(EntityShootBowEvent e) {

        if (!e.getEntity().hasPermission("hms.bypass.pvp") && e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            long currentTime = System.currentTimeMillis();
            if (hasShotBow.getIfPresent(p) != null) {
                e.setCancelled(true);
            } else hasShotBow.put(p, true);
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
            scheduler.runTaskLaterAsynchronously(hms, () -> {
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }, 5);

            UUID killerUid = killer.getUniqueId();
            killStreak.remove(victim.getUniqueId());

            final int streak;
            if (killStreak.containsKey(killerUid)) streak = this.killStreak.get(killerUid) + 1;
            else streak = 1;

            killStreak.put(killerUid, streak);

            if (streak > thresholdUntilShown || streak % 5 == 0) {
                scheduler.runTaskLater(hms, () -> titleHandler.sendActionBar(null,
                        MessageBuilder.create(hms, "modPvPKillStreak")
                                .addPlaceholderReplace("%PLAYER%", killer.getName())
                                .addPlaceholderReplace("%STREAK%", String.valueOf(streak))
                                .returnMessage()), 0L);
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
        hasShotBow.cleanUp();
    }
}
