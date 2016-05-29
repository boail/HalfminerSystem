package de.halfminer.hms.cmd;

import de.halfminer.hms.enums.HandlerType;
import de.halfminer.hms.enums.ModuleType;
import de.halfminer.hms.handlers.HanBossBar;
import de.halfminer.hms.handlers.HanTitles;
import de.halfminer.hms.modules.ModChatManager;
import de.halfminer.hms.util.Language;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * - Chat manipulation tools
 * - Toggle Globalmute
 * - Clear chat
 * - Title broadcast
 * - Bossbar broadcast
 * - Countdown via bossbar
 * - Send custom messages to player or broadcast
 * - Set news and motd message
 */
@SuppressWarnings("unused")
public class Cmdchat extends HalfminerCommand {

    private final HanBossBar bossBar = (HanBossBar) hms.getHandler(HandlerType.BOSSBAR);
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

                    sender.sendMessage(Language.getMessagePlaceholders("cmdChatCountdownStarted", true, "%PREFIX%",
                            "Chat", "%COUNT%", String.valueOf(countdown)));

                    final HanBossBar bar = (HanBossBar) hms.getHandler(HandlerType.BOSSBAR);
                    final BukkitTask task = scheduler.runTaskTimer(hms, new Runnable() {

                        int count = countdown;
                        @Override
                        public void run() {

                            if (count < 0) return;
                            bar.broadcastBar(Language.getMessagePlaceholders("cmdChatCountdown", false, "%COUNT%",
                                    String.valueOf(count)),
                                    BarColor.GREEN, BarStyle.SOLID, 35, (double) count / countdown);
                            if (count-- == 0) {
                                for (Player p : hms.getServer().getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                                }
                            }
                        }
                    }, 0, 20L);

                    scheduler.runTaskLater(hms, () -> {

                        task.cancel();
                        bar.removeBar();
                    }, 20 * (countdown + 4));
                }

            } else if (args[0].equalsIgnoreCase("globalmute")) {

                ((ModChatManager) hms.getModule(ModuleType.CHAT_MANAGER)).toggleGlobalmute();

            } else if (args[0].equalsIgnoreCase("set") && args.length > 1){

                String message = Language.arrayToString(args, 1, true).replace("\\n", "\n");
                storage.set("chatmessage", message);
                storage.set("chatmessagetime", (System.currentTimeMillis() / 1000));
                sender.sendMessage(Language.getMessagePlaceholders("cmdChatMessageSet", true, "%PREFIX%", "Chat",
                        "%MESSAGE%", message));

            } else {

                if (verifyMessage()) {

                    if (args[0].equalsIgnoreCase("title") || (args[0].equalsIgnoreCase("bossbar"))) {

                        boolean useBossbar = args[0].equalsIgnoreCase("bossbar");

                        int time = 1;
                        Player sendTo = null;

                        if (args.length > 1) {

                            try {
                                time = Integer.decode(args[1]);
                                if (time < 1) time = 1;
                            } catch (NumberFormatException e) {
                                time = 1;
                            }

                            if (args.length > 2) sendTo = server.getPlayer(args[2]);
                        }

                        String recipientName;
                        if (sendTo == null) recipientName = Language.getMessage("cmdChatAll");
                        else recipientName = sendTo.getName();

                        String notifySender = useBossbar ? "cmdChatBossbar" : "cmdChatTitle";

                        sender.sendMessage(Language.getMessagePlaceholders(notifySender, true, "%PREFIX%", "Chat",
                                "%SENDTO%", recipientName, "%TIME%", String.valueOf(time), "%MESSAGE%", message));

                        if (useBossbar) {

                            BarColor color = BarColor.PURPLE;
                            int potentialColor = 3;
                            if (sendTo == null) potentialColor = 2;
                            if (args.length > potentialColor) {
                                for (BarColor colors : BarColor.values()) {
                                    if (colors.toString().equalsIgnoreCase(args[potentialColor])) {
                                        color = colors;
                                        break;
                                    }
                                }
                            }

                            if (sendTo != null) bossBar.sendBar(sendTo, message, color, BarStyle.SOLID, time);
                            else bossBar.broadcastBar(message, color, BarStyle.SOLID, time);

                        } else {
                            if (message.startsWith("\n")) message = " " + message;
                            titles.sendTitle(sendTo, message, 10, time * 20 - 20, 10);
                        }

                    } else if (args[0].equalsIgnoreCase("news")) {

                        storage.set("news", message);
                        hms.getModule(ModuleType.MOTD).loadConfig();
                        if (sender instanceof Player) {
                            bossBar.sendBar((Player) sender, Language.getMessagePlaceholders("modTitlesNewsFormat",
                                    false, "%NEWS%", message), BarColor.YELLOW, BarStyle.SOLID, 5);
                            titles.sendTitle((Player) sender, " \n" + message, 10, 100, 10);
                        }
                        sender.sendMessage(Language.getMessagePlaceholders("cmdChatNewsSetTo", true,
                                "%PREFIX%", "Chat"));

                    } else if (args[0].equalsIgnoreCase("send")) {

                        String sendToString;

                        if (args.length > 1) {

                            Player player = server.getPlayer(args[1]);
                            if (player != null) {

                                player.sendMessage(message);
                                sendToString = player.getName();
                            } else {

                                sender.sendMessage(Language.getMessagePlaceholders("playerNotOnline", true,
                                        "%PREFIX%", "Chat"));
                                return;
                            }
                        } else {

                            server.broadcast(message, "hms.default");
                            sendToString = Language.getMessage("cmdChatAll");
                        }
                        sender.sendMessage(Language.getMessagePlaceholders("cmdChatSend", true, "%PREFIX%", "Chat",
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

        message = storage.getString("chatmessage");

        if (message.length() == 0) {

            sender.sendMessage(Language.getMessagePlaceholders("cmdChatMessageNotSet", true,
                    "%PREFIX%", "Chat"));
            return false;
        } else {

            //Clear message if it is old, only keep it for 10 minutes
            long time = storage.getLong("chatmessagetime");
            if (time + 600 < (System.currentTimeMillis() / 1000)) {

                message = "";
                storage.set("chatmessage", null);

                sender.sendMessage(Language.getMessagePlaceholders("cmdChatMessageNotSet", true,
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

        for (Player player : server.getOnlinePlayers()) {

            if (!player.hasPermission("hms.chat.advanced")) player.sendMessage(clearMessage);

            player.sendMessage(Language.getMessagePlaceholders("cmdChatCleared", true, "%PREFIX%", "Chat",
                    "%PLAYER%", whoCleared));
        }

        hms.getLogger().info(Language.getMessagePlaceholders("cmdChatClearedLog", false, "%PLAYER%", whoCleared));
    }

    private void showUsage() {
        sender.sendMessage(Language.getMessagePlaceholders("cmdChatUsage", true, "%PREFIX%", "Chat"));
    }
}
