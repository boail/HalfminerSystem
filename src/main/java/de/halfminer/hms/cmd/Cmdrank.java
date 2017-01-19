package de.halfminer.hms.cmd;

import de.halfminer.hms.cmd.abs.HalfminerPersistenceCommand;
import de.halfminer.hms.exception.CachingException;
import de.halfminer.hms.exception.PlayerNotFoundException;
import de.halfminer.hms.util.CustomAction;
import de.halfminer.hms.util.HalfminerPlayer;
import de.halfminer.hms.util.MessageBuilder;
import de.halfminer.hms.util.StringArgumentSeparator;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * - Give out ranks to players
 * - Executes a custom action to give out rewards and run custom commands
 *   - If player is offline waits until he is back online to execute action
 *   - If action fails executes custom fallback action
 *   - Add custom parameters that will be multiplied by a custom amount per rank (see config)
 *     - Possibility to deduct reward multipliers for previous ranks
 * - Prevents giving out same or lower rank
 */
@SuppressWarnings("unused")
public class Cmdrank extends HalfminerPersistenceCommand {

    private UUID uuidToReward;
    private String rankName;
    private List<Integer> multiplierList;
    private int multiplierValue;

    public Cmdrank() {
        this.permission = "hms.rank";
    }

    @Override
    protected void execute() {

        if (args.length < 2) {
            MessageBuilder.create(hms, "cmdRankUsage", "Rank").sendMessage(sender.get());
            return;
        }

        Player playerToReward = server.getPlayerExact(args[0]);
        if (playerToReward == null) {
            try {
                HalfminerPlayer p = storage.getPlayer(args[0]);
                uuidToReward = p.getUniqueId();
                MessageBuilder.create(hms, "cmdRankNotOnline", "Rank")
                        .addPlaceholderReplace("%PLAYER%", args[0])
                        .sendMessage(sender.get());

                setPersistenceOwner(uuidToReward);
                setPersistent(PersistenceMode.EVENT_PLAYER_JOIN);
            } catch (PlayerNotFoundException e) {
                e.sendNotFoundMessage(sender.get(), "Rank");
                return;
            }
        } else uuidToReward = playerToReward.getUniqueId();

        multiplierList = new ArrayList<>();
        for (String level : hms.getConfig().getStringList("command.rank.rankNamesAndMultipliers")) {

            StringArgumentSeparator current = new StringArgumentSeparator(level, ',');

            if (!current.meetsLength(2)) {
                sendInvalidRankConfig(level);
                return;
            }

            String currentRank = current.getArgument(0);
            int multiplier = current.getArgumentInt(1);
            if (multiplier < 1) {
                sendInvalidRankConfig(level);
                return;
            }
            if (currentRank.equalsIgnoreCase(args[1])) {
                rankName = currentRank;
                multiplierValue = multiplier;
            }
            multiplierList.add(multiplier);
        }

        if (rankName == null) {
            MessageBuilder.create(hms, "cmdRankInvalidRankCommand", "Rank").sendMessage(sender.get());
            setPersistent(PersistenceMode.NONE);
            return;
        }

        if (playerToReward != null) {
            execute(null);
        }
    }

    @Override
    public boolean execute(Event e) {

        Player toReward = server.getPlayer(uuidToReward);

        int playerLevel = 0;
        while (toReward.hasPermission("hms.level." + (playerLevel + 1))) {
            playerLevel++;
        }

        List<Integer> baseAmounts = hms.getConfig().getIntegerList("command.rank.baseAmountValues");
        List<Integer> multipliedAmounts = new ArrayList<>();
        for (Integer base : baseAmounts) {
            multipliedAmounts.add(base * multiplierValue);
        }

        if (playerLevel > 0) {
            // check if new level is lower/same as old one
            int multiplierOfPreviousRank = multiplierList.get(playerLevel - 1);
            if (multiplierOfPreviousRank >= multiplierValue) {
                MessageBuilder send = MessageBuilder.create(hms, "cmdRankNewLevelSameOrLower", "Rank")
                        .addPlaceholderReplace("%PLAYER%", toReward.getName())
                        .addPlaceholderReplace("%NEWRANK%", rankName);
                sendAndLogMessageBuilder(send);
                return true;
            }

            if (hms.getConfig().getBoolean("command.rank.deductPreviousRanks")) {
                for (int i = 0; i < multipliedAmounts.size(); i++) {
                    int current = multipliedAmounts.get(i);
                    int alreadyGiven = baseAmounts.get(i) * multiplierOfPreviousRank;
                    System.out.println(current + " " + alreadyGiven);
                    multipliedAmounts.set(i, current - alreadyGiven);
                }
            }
        }

        String actionName = hms.getConfig().getString("command.rank.actionToExecute");
        boolean actionHasFailed = true;
        try {
            CustomAction action = new CustomAction(actionName, hms, storage);
            addPlaceholdersToAction(action, multipliedAmounts);
            actionHasFailed = !action.runAction(toReward);
        } catch (CachingException e1) {
            logActionNotFound(toReward, actionName);
        }

        if (actionHasFailed) {
            String actionOnFail = hms.getConfig().getString("command.rank.actionToExecuteOnFail");
            try {
                if (actionOnFail.length() > 0) {
                    CustomAction failAction = new CustomAction(actionOnFail, hms, storage);
                    addPlaceholdersToAction(failAction, multipliedAmounts);
                    failAction.runAction(toReward);
                }
            } catch (CachingException e1) {
                logActionNotFound(toReward, actionOnFail);
            }
        }

        return true;
    }

    private void sendInvalidRankConfig(String level) {
        MessageBuilder.create(hms, "cmdRankInvalidRankConfig", "Rank")
                .addPlaceholderReplace("%INVALIDINPUT%", level)
                .sendMessage(sender.get());
        setPersistent(PersistenceMode.NONE);
    }

    private void addPlaceholdersToAction(CustomAction action, List<Integer> multipliedAmounts) {
        action.addPlaceholderForNextRun("%PARAM1%", rankName);
        for (int i = 0; i < multipliedAmounts.size(); i++) {
            action.addPlaceholderForNextRun("%PARAM" + (i + 2) + "%", String.valueOf(multipliedAmounts.get(i)));
        }
    }

    private void logActionNotFound(Player toReward, String actionName) {
        MessageBuilder notify = MessageBuilder.create(hms, "cmdRankActionNotFound", "Rank")
                .addPlaceholderReplace("%PLAYER%", toReward.getName())
                .addPlaceholderReplace("%ACTIONNAME%", actionName);
        sendAndLogMessageBuilder(notify);
    }

    private void sendAndLogMessageBuilder(MessageBuilder toSendAndLog) {
        toSendAndLog.logMessage(Level.SEVERE);
        if (sender.get() != null) toSendAndLog.sendMessage(sender.get());
    }
}