package de.halfminer.hms.cmd;

import de.halfminer.hms.enums.HandlerType;
import de.halfminer.hms.enums.ModuleType;
import de.halfminer.hms.handlers.HanBossBar;
import de.halfminer.hms.handlers.HanTitles;
import de.halfminer.hms.util.Language;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("unused")
public class Cmdchat extends HalfminerCommand {

    private final HanTitles titles = (HanTitles) hms.getHandler(HandlerType.TITLES);
    private CommandSender sender;
    private String message;

    public Cmdchat() {
        this.permission = "hms.chat";
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {

        this.sender = sender;

        if (!sender.hasPermission("hms.chat.advanced")) {
            clearChat();
            return;
        }

        if (args.length > 0) {

            if (args[0].equalsIgnoreCase("clear")) {

                clearChat();

            } else if (args[0].equalsIgnoreCase("countdown") && args.length == 2) {

                final int countdown;
                try {
                    countdown = Integer.decode(args[1]);
                } catch (NumberFormatException e) {
                    showUsage();
                    return;
                }
                if (countdown > 30) showUsage();
                else {

                    sender.sendMessage(Language.getMessagePlaceholders("commandChatCountdownStarted", true, "%PREFIX%",
                            "Chat", "%COUNT%", String.valueOf(countdown)));

                    final HanBossBar bar = (HanBossBar) hms.getHandler(HandlerType.BOSS_BAR);
                    final BukkitTask task = hms.getServer().getScheduler().runTaskTimer(hms, new Runnable() {

                        int count = countdown;
                        @Override
                        public void run() {

                            if (count < 0) return;
                            bar.broadcastBar(Language.getMessagePlaceholders("commandChatCountdown", false, "%COUNT%",
                                    String.valueOf(count)),
                                    BarColor.GREEN, BarStyle.SOLID, 35, (double) count / countdown);
                            if (count-- == 0) {
                                for (Player p : hms.getServer().getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                                }
                            }
                        }
                    }, 0, 20L);

                    hms.getServer().getScheduler().runTaskLater(hms, new Runnable() {
                        @Override
                        public void run() {

                            task.cancel();
                            bar.removeBroadcastBar();
                        }
                    }, 20 * (countdown + 4));
                }

            } else if (args[0].equalsIgnoreCase("globalmute")) {

                boolean active = storage.getBoolean("sys.globalmute");
                storage.set("sys.globalmute", !active);

                if (active) {
                    hms.getServer().broadcast(Language.getMessagePlaceholders("commandChatGlobalmuteOff",
                            true, "%PREFIX%", "Globalmute"), "hms.default");
                } else {
                    hms.getServer().broadcast(Language.getMessagePlaceholders("commandChatGlobalmuteOn",
                            true, "%PREFIX%", "Globalmute"), "hms.default");
                }

            } else if (args[0].equalsIgnoreCase("set") && args.length > 1){

                String message = Language.arrayToString(args, 1, true).replace("\\n", "\n");
                storage.set("sys.chatmessage", message);
                storage.set("sys.chatmessagetime", (System.currentTimeMillis() / 1000));
                sender.sendMessage(Language.getMessagePlaceholders("commandChatMessageSet", true, "%PREFIX%", "Chat",
                        "%MESSAGE%", message));

            } else {

                if (verifyMessage()) {

                    if (args[0].equalsIgnoreCase("title")) {

                        int time = 1;
                        Player sendTo = null;

                        if (args.length > 1) {

                            try {
                                time = Integer.decode(args[1]);
                            } catch (NumberFormatException e) {
                                time = 1;
                            }

                            if (args.length > 2) sendTo = hms.getServer().getPlayer(args[2]);
                        }

                        String sendToString;
                        if (sendTo == null) sendToString = Language.getMessage("commandChatAll");
                        else sendToString = sendTo.getName();

                        sender.sendMessage(Language.getMessagePlaceholders("commandChatTitle", true, "%PREFIX%", "Chat",
                                "%SENDTO%", sendToString, "%TIME%", String.valueOf(time), "%MESSAGE%", message));

                        titles.sendTitle(sendTo, message, 10, time * 20 - 20, 10);

                    } else if (args[0].equalsIgnoreCase("news")) {

                        storage.set("sys.news", message);
                        hms.getModule(ModuleType.MOTD).reloadConfig();
                        if (sender instanceof Player) {
                            titles.sendTitle((Player) sender, Language.getMessagePlaceholders("modTitlesNewsFormat",
                                    false, "%NEWS%", message), 40, 180, 40);
                        }
                        sender.sendMessage(Language.getMessagePlaceholders("commandChatNewsSetTo", true,
                                "%PREFIX%", "Chat"));

                    } else if (args[0].equalsIgnoreCase("send")) {

                        String sendToString;

                        if (args.length > 1) {

                            Player player = hms.getServer().getPlayer(args[1]);
                            if (player != null) {

                                player.sendMessage(message);
                                sendToString = player.getName();
                            } else {

                                sender.sendMessage(Language.getMessagePlaceholders("playerNotOnline", true,
                                        "%PREFIX%", "Chat"));
                                return;
                            }
                        } else {

                            hms.getServer().broadcast(message, "hms.default");
                            sendToString = Language.getMessage("commandChatAll");
                        }
                        sender.sendMessage(Language.getMessagePlaceholders("commandChatSend", true, "%PREFIX%", "Chat",
                                "%SENDTO%", sendToString));

                    }  else showUsage();
                }
            }
        } else showUsage();
    }

    /**
     * Ensures that the message has been set, while setting it as a field var
     *
     * @return true if a message has been set
     */
    private boolean verifyMessage() {

        message = storage.getString("sys.chatmessage");

        if (message.length() == 0) {

            sender.sendMessage(Language.getMessagePlaceholders("commandChatMessageNotSet", true,
                    "%PREFIX%", "Chat"));
            return false;
        } else {

            //Clear message if it is old, only keep it for 10 minutes
            long time = storage.getLong("sys.chatmessagetime");
            if (time + 600 < (System.currentTimeMillis() / 1000)) {

                message = "";
                storage.set("sys.chatmessage", null);

                sender.sendMessage(Language.getMessagePlaceholders("commandChatMessageNotSet", true,
                        "%PREFIX%", "Chat"));

                return false;
            } else return true;
        }
    }

    private void clearChat() {

        String whoCleared = sender.getName();
        if (whoCleared.equals("CONSOLE")) whoCleared = Language.getMessage("consoleName");

        String clearMessage = "";
        for (int i = 0; i < 100; i++) clearMessage += ChatColor.RESET + " \n";

        for (Player player : hms.getServer().getOnlinePlayers()) {

            if (!player.hasPermission("hms.chat.advanced")) player.sendMessage(clearMessage);

            player.sendMessage(Language.getMessagePlaceholders("commandChatCleared", true, "%PREFIX%", "Chat",
                    "%PLAYER%", whoCleared));
        }

        hms.getLogger().info(Language.getMessagePlaceholders("commandChatClearedLog", false, "%PLAYER%", whoCleared));
    }

    private void showUsage() {
        sender.sendMessage(Language.getMessagePlaceholders("commandChatUsage", true, "%PREFIX%", "Chat"));
    }

}
