package de.halfminer.hmh;

import de.halfminer.hmh.data.HaroPlayer;
import de.halfminer.hmh.data.HaroStorage;
import de.halfminer.hmh.tasks.TitleUpdateTask;
import de.halfminer.hms.handler.HanStorage;
import de.halfminer.hms.handler.storage.HalfminerPlayer;
import de.halfminer.hms.handler.storage.PlayerNotFoundException;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.util.logging.Level;

/**
 * Listeners for Haro, handles login whitelist, checks if players are initialized, handles death kicking and respawning.
 */
public class HaroListeners extends HaroClass implements Listener {

    private final HanStorage systemStorage = hms.getStorageHandler();
    private final HaroStorage haroStorage = hmh.getHaroStorage();
    private final TitleUpdateTask titleUpdateTask = hmh.getTitleUpdateTask();


    @EventHandler
    public void onLogin(PlayerLoginEvent e) {

        if (e.getPlayer().hasPermission("hmh.admin")) {
            return;
        }

        HalfminerPlayer halfminerPlayer = null;
        try {
            halfminerPlayer = systemStorage.getPlayer(e.getPlayer().getUniqueId());
        } catch (PlayerNotFoundException ignored) {}

        HaroPlayer haroPlayer = halfminerPlayer != null ? haroStorage.getHaroPlayer(halfminerPlayer) : null;
        String disallowMessageKey = null;

        if (haroPlayer == null || !haroPlayer.isAdded()) {
            disallowMessageKey = "listenerNotAdded";
        } else if (haroStorage.isGameRunning()) {

            if (!haroPlayer.hasTimeLeft()) {
                disallowMessageKey = "listenerNoTimeLeft";
            }

            if (haroPlayer.isDead()) {
                disallowMessageKey = "listenerAlreadyDead";
            }
        }

        if (disallowMessageKey != null) {
            String kickMessage = MessageBuilder.returnMessage(disallowMessageKey, hmh, false);
            e.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        HalfminerPlayer hPlayer = hms.getStorageHandler().getPlayer(player);
        HaroPlayer haroPlayer = haroStorage.getHaroPlayer(hPlayer);

        if (haroStorage.isGameRunning() && !player.hasPermission("hmh.admin")) {
            if (!haroPlayer.isInitialized()) {
                haroStorage.initializePlayer(player);
            }

            MessageBuilder.create("listenerJoinedInfo", hmh)
                    .addPlaceholder("TIMELEFT", haroPlayer.getTimeLeftSeconds() / 60)
                    .sendMessage(player);

            MessageBuilder.create("listenerJoinedLogTime", hmh)
                    .addPlaceholder("PLAYER", player.getName())
                    .addPlaceholder("TIMELEFT", haroPlayer.getTimeLeftSeconds())
                    .logMessage(Level.INFO);
        }

        titleUpdateTask.updateTitles();
        hmh.getHaroStorage().playerJoined(haroPlayer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        HaroPlayer haroPlayer = haroStorage.getHaroPlayer(e.getPlayer());
        haroPlayer.setOffline();

        // hide quit message when kicking after dying
        if (haroStorage.isGameRunning() && haroPlayer.isDead()) {
            e.setQuitMessage("");
        }

        titleUpdateTask.updateTitles();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {

        Player player = e.getEntity();
        if (haroStorage.isGameRunning() && !player.hasPermission("hmh.admin")) {

            HaroPlayer haroPlayer = haroStorage.getHaroPlayer(player);
            haroPlayer.setDead(true);

            // kick on next tick, also moves the messages below default death message
            scheduler.runTaskLater(hmh, () -> {
                String kickMessage = MessageBuilder.returnMessage("listenerDeathKick", hmh, false);
                player.kickPlayer(kickMessage);

                MessageBuilder.create("listenerDeathBroadcast", hmh)
                        .addPlaceholder("PLAYER", player.getName())
                        .addPlaceholder("COUNTALIVE", haroStorage.getAddedPlayers(true).size())
                        .broadcastMessage(false);

                if (haroStorage.isGameOver()) {
                    HaroPlayer winner = haroStorage.getAddedPlayers(true).get(0);
                    MessageBuilder.create("listenerGameWonBroadcast", hmh)
                            .addPlaceholder("PLAYER", winner.getName())
                            .broadcastMessage(true);

                    if (winner.isOnline()) {
                        MessageBuilder.create("listenerGameWon", hmh).sendMessage(winner.getBase());
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location spawn = haroStorage.getSpawnPoint();
        if (spawn != null) {
            e.setRespawnLocation(spawn);
        }
    }
}