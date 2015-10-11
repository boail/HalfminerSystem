package de.halfminer.hms.cmd;

import de.halfminer.hms.util.Language;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;


@SuppressWarnings("unused")
public class Cmdlag extends BaseCommand {

    public Cmdlag() {
        this.permission = "hms.lag";
    }

    @Override
    public void run(CommandSender sender, Command cmd, String label, String[] args) {

        CraftPlayer player;
        boolean showSummary = false;
        if (args.length > 0) { //get other player

            if (!sender.hasPermission("hms.lag.others")) {
                sender.sendMessage(Language.getMessagePlaceholderReplace("noPermission", true, "%PREFIX%", "Lag"));
                return;
            }

            Player toGet = hms.getServer().getPlayer(args[0]);

            if (toGet != null) {
                player = (CraftPlayer) toGet;
            } else {
                sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagPlayerNotOnline", true, "%PREFIX%", "Lag"));
                return;
            }

        } else if (sender instanceof Player) {
            player = (CraftPlayer) sender;
            showSummary = true;
        } else {
            sender.sendMessage(Language.getMessage("notAPlayer"));
            return;
        }

        //get values and send message
        int ping = player.getHandle().ping;
        double tps = hms.getModTps().getTps();
        if (ping > 2000) { //weird behaviour in ping value, causing it to show extremely high numbers under certain circumstances
            sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagPlayerError", true, "%PREFIX%", "Lag"));
            return;
        }
        sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagPlayerInfo", true, "%PREFIX%", "Lag", "%PLAYER%", player.getName(), "%LATENCY%", String.valueOf(ping)));
        sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagServerInfo", true, "%PREFIX%", "Lag", "%TPS%", String.valueOf(hms.getModTps().getTps())));

        if (showSummary) { //determines the summary message, only shown when viewing own status
            int statusServerLag = 0;
            boolean statusPlayerLag = false;

            if (tps < 12.0d) statusServerLag = 2;
            else if (tps < 16.0d) statusServerLag = 1;
            if (ping > 100) statusPlayerLag = true;

            if (statusServerLag == 0 && !statusPlayerLag)
                sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagStable", true, "%PREFIX%", "Lag"));
            else if (statusServerLag == 1)
                sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagServerUnstable", true, "%PREFIX%", "Lag"));
            else if (statusServerLag == 2)
                sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagServerLag", true, "%PREFIX%", "Lag"));
            else
                sender.sendMessage(Language.getMessagePlaceholderReplace("commandLagPlayerLag", true, "%PREFIX%", "Lag"));
        }
    }
}