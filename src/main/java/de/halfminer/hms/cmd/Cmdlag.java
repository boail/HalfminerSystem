package de.halfminer.hms.cmd;

import de.halfminer.hms.enums.ModuleType;
import de.halfminer.hms.modules.ModTps;
import de.halfminer.hms.util.MessageBuilder;
import de.halfminer.hms.util.NMSUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * - Information if player or server lags
 * - View other players latency/ping
 */
@SuppressWarnings("unused")
public class Cmdlag extends HalfminerCommand {

    public Cmdlag() {
        this.permission = "hms.lag";
    }

    @Override
    public void execute() {

        Player player;
        if (args.length > 0) {

            if (!sender.hasPermission("hms.lag.others")) {
                sendNoPermissionMessage("Lag");
                return;
            }

            //get player to lookup
            player = server.getPlayer(args[0]);

            if (player == null) {
                MessageBuilder.create(hms, "playerNotOnline", "Lag").sendMessage(sender);
                return;
            }

        } else if (isPlayer) {
            player = this.player;
        } else {
            sendNotAPlayerMessage("Lag");
            return;
        }

        // get latency and tps
        int ping = NMSUtils.getPing(player);
        double tps = ((ModTps) hms.getModule(ModuleType.TPS)).getTps();

        boolean playerIsLagging = true;
        String pingColored;
        if (ping > 1000 || ping < 0) {
            // ping not known (e.g. just logged in)
            pingColored = ChatColor.DARK_RED + "> 1000";
        } else {
            pingColored = String.valueOf(ping);
            if (ping > 200) pingColored = ChatColor.RED + pingColored;
            else if (ping > 100) pingColored = ChatColor.YELLOW + pingColored;
            else {
                pingColored = ChatColor.GREEN + pingColored;
                playerIsLagging = false;
            }
        }

        ServerStatus serverLagStatus = ServerStatus.STABLE;
        String tpsColored = String.valueOf(tps);
        if (tps < 12.0d) {
            tpsColored = ChatColor.RED + tpsColored;
            serverLagStatus = ServerStatus.LAGGING;
        } else if (tps < 16.0d) {
            tpsColored = ChatColor.YELLOW + tpsColored;
            serverLagStatus = ServerStatus.UNSTABLE;
        } else tpsColored = ChatColor.GREEN + tpsColored;

        // Send ping and tps information to player
        MessageBuilder.create(hms, "cmdLagPlayerInfo", "Lag")
                .addPlaceholderReplace("%PLAYER%", player.getName())
                .addPlaceholderReplace("%LATENCY%", pingColored)
                .sendMessage(sender);
        MessageBuilder.create(hms, "cmdLagServerInfo", "Lag")
                .addPlaceholderReplace("%TPS%", tpsColored)
                .sendMessage(sender);

        // determines the summary message, only shown when viewing own status
        if (player.equals(this.player)) {
            String messageKey;
            if (serverLagStatus == ServerStatus.STABLE && !playerIsLagging) messageKey = "cmdLagStable";
            else if (serverLagStatus == ServerStatus.UNSTABLE) messageKey = "cmdLagServerUnstable";
            else if (serverLagStatus == ServerStatus.LAGGING) messageKey = "cmdLagServerLag";
            else messageKey = "cmdLagPlayerLag";
            MessageBuilder.create(hms, messageKey, "Lag").sendMessage(sender);
        }
    }

    private enum ServerStatus {
        STABLE,
        UNSTABLE,
        LAGGING
    }
}