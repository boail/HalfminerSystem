package de.halfminer.hms.modules;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class ModPvP extends HalfminerModule implements Listener {

    private Map<Player, Long> lastShot;

    public ModPvP() {
        reloadConfig();
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onEat(PlayerItemConsumeEvent e) {

        final Player p = e.getPlayer();
        if (p.hasPermission("hms.bypass.pvp")) return;

        ItemStack item = e.getItem();
        if (item.getType() == Material.GOLDEN_APPLE && item.getDurability() == 1) {

            updateEffect(p, PotionEffectType.REGENERATION, 300, 3);
        } else if (item.getType() == Material.POTION
                && (item.getDurability() == 8201 || item.getDurability() == 8265 || item.getDurability() == 8233)) {

            nerfStrength(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onPotionSplash(PotionSplashEvent e) {

        for (PotionEffect effect : e.getPotion().getEffects()) {
            if (effect.getType().equals(PotionEffectType.INCREASE_DAMAGE)) {

                for (LivingEntity entity : e.getAffectedEntities()) {
                    if (entity instanceof Player) {
                        Player p = (Player) entity;
                        if (!p.hasPermission("hms.bypass.pvp"))
                            nerfStrength((Player) entity);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void antiBowSpam(EntityShootBowEvent e) {

        if (!e.getEntity().hasPermission("hms.bypass.pvp") && e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            long currentTime = System.currentTimeMillis();
            if (lastShot.containsKey(p) && lastShot.get(p) + 1000 > currentTime) {
                e.setCancelled(true);
            } else lastShot.put(p, currentTime);
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void deathSounds(PlayerDeathEvent e) {

        e.setDeathMessage("");

        //Heal and play sound
        final Player killer = e.getEntity().getKiller();
        final Player died = e.getEntity();
        if (killer != null && killer != e.getEntity()) {

            killer.setHealth(killer.getMaxHealth());
            hms.getServer().getScheduler().runTaskLaterAsynchronously(hms, new Runnable() {
                @Override
                public void run() {
                    killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1.0f, 2.0f);
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1.0f, 0.5f);
                }
            }, 5);

        } else {
            died.playSound(e.getEntity().getLocation(), Sound.AMBIENCE_CAVE, 1.0f, 1.4f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void teleportRemoveJump(PlayerTeleportEvent e) {

        if (!e.getPlayer().hasPermission("hms.bypass.pvp")) {
            e.getPlayer().removePotionEffect(PotionEffectType.JUMP);
            e.getPlayer().removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        }
    }

    private void updateEffect(final Player player, final PotionEffectType effect, final int time, final int amplifier) {

        hms.getServer().getScheduler().runTask(hms, new Runnable() {
            @Override
            public void run() {
                player.removePotionEffect(effect);
                player.addPotionEffect(new PotionEffect(effect, time, amplifier));
            }
        });
    }

    private void nerfStrength(final Player player) {

        final PotionEffect oldEffect = getStrengthFromPlayer(player);

        hms.getServer().getScheduler().runTask(hms, new Runnable() {
            @Override
            public void run() {

                PotionEffect newEffect = getStrengthFromPlayer(player);

                if (newEffect != null
                        && (oldEffect == null || !isSameEffect(oldEffect, newEffect))) {

                    int nerfRatio = 3;
                    int amplifier = newEffect.getAmplifier();
                    if (amplifier > 0) nerfRatio *= 6;

                    updateEffect(player, PotionEffectType.INCREASE_DAMAGE,
                            newEffect.getDuration() / nerfRatio, amplifier);
                }
            }
        });
    }

    private boolean isSameEffect(PotionEffect oldEff, PotionEffect newEff) {
        int oldDuration = oldEff.getDuration();
        int oldDurationBounds = oldDuration - 20;
        int newDuration = newEff.getDuration();

        return oldDuration > newDuration
                && newDuration > oldDurationBounds
                && oldEff.getAmplifier() == newEff.getAmplifier()
                && oldEff.getType().equals(newEff.getType());
    }

    private PotionEffect getStrengthFromPlayer(Player player) {

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.INCREASE_DAMAGE)) return effect;
        }

        return null;
    }

    public void reloadConfig() {
        lastShot = new HashMap<>();
    }
}
