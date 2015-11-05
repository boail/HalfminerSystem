package de.halfminer.hms.modules;

import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.util.Language;
import de.halfminer.hms.util.StatsType;
import de.halfminer.hms.util.TitleSender;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModTitles extends HalfminerModule implements Listener {

    private final Map<UUID, Integer> killStreaks = new HashMap<>();
    private final Map<UUID, Integer> deathStreaks = new HashMap<>();

    private final Map<Player, Double> balances = new ConcurrentHashMap<>();
    private int playercount = hms.getServer().getOnlinePlayers().size();


    public ModTitles() {
        reloadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void joinTitles(PlayerJoinEvent e) {

        final Player joined = e.getPlayer();

        final double balance = getBalance(joined);
        balances.put(joined, balance);

        playercount++;
        updateTablists();

        if (!storage.getStatsBoolean(joined, StatsType.NEUTP_USED)) {
            TitleSender.sendTitle(joined, Language.getMessagePlaceholders("modTitlesNewPlayerFormat", false,
                    "%PLAYER%", joined.getName()), 10, 200, 10);
        } else {



            hms.getServer().getScheduler().runTaskAsynchronously(hms, new Runnable() {
                @Override
                public void run() {
                    TitleSender.sendTitle(joined, Language.getMessagePlaceholders("modTitlesJoinFormat", false,
                            "%BALANCE%", String.valueOf(balance), "%PLAYERCOUNT%", String.valueOf(playercount)), 10, 100, 10);

                    try {
                        Thread.sleep(6000L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }

                    TitleSender.sendTitle(joined, Language.getMessagePlaceholders("modTitlesNewsFormat",
                            false, "%NEWS%", storage.getString("sys.news")), 40, 120, 40);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void leaveTitles(PlayerQuitEvent e) {
        playercount--;
        balances.remove(e.getPlayer());
        updateTablists();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void deathActionbarSender(PlayerDeathEvent e) {

        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {

            UUID killerUid = killer.getUniqueId();
            UUID victimUid = victim.getUniqueId();

            deathStreaks.remove(killerUid);
            killStreaks.remove(victimUid);

            int killerStreak;
            int victimStreak;

            if (killStreaks.containsKey(killerUid)) killerStreak = killStreaks.get(killerUid) + 1;
            else killerStreak = 1;

            if (deathStreaks.containsKey(victimUid)) victimStreak = deathStreaks.get(victimUid) + 1;
            else victimStreak = 1;

            killStreaks.put(killerUid, killerStreak);
            deathStreaks.put(victimUid, victimStreak);

            if (killerStreak > 4) {
                TitleSender.sendActionBar(null, Language.getMessagePlaceholders("modTitlesKillStreak", false,
                        "%PLAYER%", killer.getName(), "%STREAK%", String.valueOf(killerStreak)));
            }
            if (victimStreak > 4) {
                TitleSender.sendActionBar(null, Language.getMessagePlaceholders("modTitlesDeathStreak", false,
                        "%PLAYER%", victim.getName(), "%STREAK%", String.valueOf(victimStreak)));
            }
        }
    }

    private void updateTablists() {
        hms.getServer().getScheduler().runTaskAsynchronously(hms, new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<Player, Double> entry : balances.entrySet()) {
                    double balance = entry.getValue();
                    balance = Math.round(balance * 100.0d) / 100.0d;

                    TitleSender.setTablistHeaderFooter(entry.getKey(), Language.getMessagePlaceholders("modTitlesTablist",
                            false, "%BALANCE%", String.valueOf(balance), "%PLAYERCOUNT%", String.valueOf(playercount)));
                }
            }
        });
    }

    private double getBalance(Player player) {
        Economy econ = HalfminerSystem.getEconomy();
        double balance;
        if (econ != null) {
            balance = Math.round(econ.getBalance(player) * 100.0d) / 100.0d;
        } else balance = 0.0d;
        return balance;
    }

    @Override
    public void reloadConfig() {

        hms.getServer().getScheduler().scheduleSyncRepeatingTask(hms, new Runnable() {
            @Override
            public void run() {
                balances.clear();
                for (Player player : hms.getServer().getOnlinePlayers()) {
                    balances.put(player, getBalance(player));
                }
                updateTablists();
            }
        }, 0, 100);
    }
}
