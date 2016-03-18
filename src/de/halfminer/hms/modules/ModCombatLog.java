package de.halfminer.hms.modules;

import de.halfminer.hms.util.Language;
import de.halfminer.hms.util.TitleSender;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Disables the possibility to logout during combat
 * - Tags players when hitting/being hit
 * - Shows health and name of attacker/victim via title
 * - Combatlogging causes instant death
 * - Shows titles containing time left in fight
 * - Untags players after timer runs out, player logs out or a player is killed
 * - Disables armor switching, commands and enderpearls from being used during fight
 */
@SuppressWarnings("unused")
public class ModCombatLog extends HalfminerModule implements Listener {

    private final Map<String, String> lang = new HashMap<>();
    private final Map<Player, BukkitTask> tagged = Collections.synchronizedMap(new HashMap<Player, BukkitTask>());
    private boolean broadcastLog;
    private int tagTime;

    public ModCombatLog() {
        reloadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeathUntag(PlayerDeathEvent e) {

        Player victim = e.getEntity().getPlayer();
        Player killer = e.getEntity().getKiller();

        untagPlayer(victim);
        if (killer != null) untagPlayer(killer);
    }

    @EventHandler
    public void logoutCheckIfInCombat(PlayerQuitEvent e) {

        if (tagged.containsKey(e.getPlayer())) {
            untagPlayer(e.getPlayer());

            if (broadcastLog)
                hms.getServer().broadcast(Language.placeholderReplace(lang.get("loggedOut"), "%PLAYER%", e.getPlayer().getName()), "hms.default");

            if (e.getPlayer().getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent e2 = (EntityDamageByEntityEvent) e.getPlayer().getLastDamageCause();
                if (e2.getDamager() instanceof Player)
                    untagPlayer((Player) e2.getDamager());
            }

            e.getPlayer().setHealth(0.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPTagPlayer(EntityDamageByEntityEvent e) {

        if (e.getEntity() instanceof Player) {


            Player victim = (Player) e.getEntity();
            Player attacker = null;

            if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
            else if (e.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) e.getDamager();
                if (projectile.getShooter() instanceof Player) attacker = (Player) projectile.getShooter();
            }

            if (attacker != null && attacker != victim && !attacker.isDead() && !victim.isDead()) {
                tagPlayer(victim, attacker, (int) attacker.getHealth());
                tagPlayer(attacker, victim, (int) Math.ceil(victim.getHealth() - e.getFinalDamage()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClickDenyArmorChange(InventoryClickEvent e) {

        if (isTagged((Player) e.getWhoClicked())
                && e.getSlot() >= 36 && e.getSlot() <= 39) {
            e.getWhoClicked().sendMessage(lang.get("noArmorChange"));
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandCheckIfBlocked(PlayerCommandPreprocessEvent e) {

        if (isTagged(e.getPlayer())) {
            e.getPlayer().sendMessage(lang.get("noCommand"));
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnderpearlCheckIfBlocked(PlayerInteractEvent e) {

        if (isTagged(e.getPlayer()) && e.hasItem() && e.getItem().getType() == Material.ENDER_PEARL
                && ((e.getAction() == Action.RIGHT_CLICK_BLOCK) || (e.getAction() == Action.RIGHT_CLICK_AIR))) {
            e.getPlayer().sendMessage(lang.get("noEnderpearl"));
            e.getPlayer().updateInventory();
            e.setCancelled(true);
        }
    }

    private void tagPlayer(final Player p, final Player other, final int otherHealth) {

        if (p.hasPermission("hms.bypass.combatlog")) return;

        if (isTagged(p)) tagged.get(p).cancel();
        else p.sendMessage(lang.get("tagged"));

        final int healthScale = (int) other.getHealthScale();

        tagged.put(p, hms.getServer().getScheduler().runTaskTimerAsynchronously(hms, new Runnable() {

            final String symbols = lang.get("symbols");
            int time = tagTime;

            @Override
            public void run() {

                if (time == tagTime) {

                    TitleSender.sendActionBar(p, Language.placeholderReplace(lang.get("health"),
                            "%PLAYER%", other.getName(), "%HEALTH%", String.valueOf(otherHealth),
                            "%MAXHEALTH%", String.valueOf(healthScale)));

                    time--;
                    return;
                }

                // build the progressbar
                int timePercentage = (int) Math.round((time / (double) tagTime) * 10);
                String progressBar = "" + ChatColor.DARK_RED + ChatColor.STRIKETHROUGH;
                boolean switchedColors = false;
                for (int i = 0; i < 10; i++) {
                    if (timePercentage-- < 1 && !switchedColors) {
                        progressBar += "" + ChatColor.GRAY + ChatColor.STRIKETHROUGH;
                        switchedColors = true; //only append color code once
                    }
                    progressBar += symbols;
                }

                String message = Language.placeholderReplace(lang.get("countdown"), "%TIME%", String.valueOf(time),
                        "%PROGRESSBAR%", progressBar);

                if (time-- > 0) TitleSender.sendActionBar(p, message);
                else untagPlayer(p);
            }
        }, 0L, 20L));
    }

    private void untagPlayer(Player p) {

        if (!isTagged(p)) return;

        tagged.get(p).cancel();
        TitleSender.sendActionBar(p, lang.get("untagged"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_PLING, 1, 2f);

        tagged.remove(p);
    }

    private boolean isTagged(Player p) {
        return tagged.containsKey(p);
    }

    @Override
    public void reloadConfig() {

        broadcastLog = hms.getConfig().getBoolean("combatLog.broadcastLog", true);
        tagTime = hms.getConfig().getInt("combatLog.tagTime", 15);

        lang.clear();
        lang.put("tagged", Language.getMessagePlaceholders("modCombatLogTagged", true, "%PREFIX%", "PvP", "%TIME%", "" + tagTime));
        lang.put("health", Language.getMessage("modCombatLogHealth"));
        lang.put("countdown", Language.getMessage("modCombatLogCountdown"));
        lang.put("symbols", Language.getMessage("modCombatLogProgressSymbols"));
        lang.put("untagged", Language.getMessage("modCombatLogUntagged"));
        lang.put("loggedOut", Language.getMessagePlaceholders("modCombatLogLoggedOut", true, "%PREFIX%", "PvP"));
        lang.put("noArmorChange", Language.getMessagePlaceholders("modCombatLogNoArmorChange", true, "%PREFIX%", "PvP"));
        lang.put("noCommand", Language.getMessagePlaceholders("modCombatLogNoCommand", true, "%PREFIX%", "PvP"));
        lang.put("noEnderpearl", Language.getMessagePlaceholders("modCombatLogNoEnderpearl", true, "%PREFIX%", "PvP"));
    }
}
