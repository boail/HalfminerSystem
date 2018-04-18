package de.halfminer.hml.cmd;

import de.halfminer.hml.WorldGuardHelper;
import de.halfminer.hml.land.Land;
import de.halfminer.hms.handler.storage.HalfminerPlayer;
import de.halfminer.hms.handler.storage.PlayerNotFoundException;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Cmdhml extends LandCommand {

    private static final String STORAGE_FREE_LANDS = ".freetotal";


    public Cmdhml() {
        super("hml");
    }

    @Override
    protected void execute() {

        if (args.length < 1) {
            showUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":

                hml.reload();
                MessageBuilder.create("pluginReloaded", "Land")
                        .addPlaceholderReplace("%PLUGINNAME%", hml.getName())
                        .sendMessage(sender);
                break;
            case "save":

                hml.saveLandStorage();
                MessageBuilder.create("cmdHmlSave", hml).sendMessage(sender);
                break;
            case "status":

                Set<Land> allOwnedLands = board.getOwnedLandSet();
                int totalLands = allOwnedLands.size();
                int totalTeleports = 0;
                int totalFree = 0;
                int totalAbandoned = 0;
                Map<World, Integer> worldCountMap = new HashMap<>();

                for (Land land : allOwnedLands) {

                    World world = land.getChunk().getWorld();
                    if (!worldCountMap.containsKey(world)) {
                        worldCountMap.put(world, 1);
                    } else {
                        worldCountMap.put(world, worldCountMap.get(world) + 1);
                    }

                    if (land.hasTeleportLocation()) {
                        totalTeleports++;
                    }

                    if (land.isFreeLand()) {
                        totalFree++;
                    }

                    if (land.isAbandoned()) {
                        totalAbandoned++;
                    }
                }

                StringBuilder worldListBuilder = new StringBuilder();
                for (Map.Entry<World, Integer> worldIntegerEntry : worldCountMap.entrySet()) {
                    worldListBuilder.append(MessageBuilder.create("cmdHmlStatusWorldListEntry", hml)
                            .togglePrefix()
                            .addPlaceholderReplace("%WORLD%", worldIntegerEntry.getKey().getName())
                            .addPlaceholderReplace("%AMOUNT%", String.valueOf(worldIntegerEntry.getValue()))
                            .returnMessage()).append(" ");
                }

                MessageBuilder.create("cmdHmlStatus", hml)
                        .addPlaceholderReplace("%TOTALLANDS%", String.valueOf(totalLands))
                        .addPlaceholderReplace("%TOTALTELEPORTS%", String.valueOf(totalTeleports))
                        .addPlaceholderReplace("%TOTALFREE%", String.valueOf(totalFree))
                        .addPlaceholderReplace("%TOTALABANDONED%", String.valueOf(totalAbandoned))
                        .addPlaceholderReplace("%TOTALWORLDLIST%", worldListBuilder.toString().trim())
                        .sendMessage(sender);
                break;
            case "forcewgrefresh":

                MessageBuilder.create("cmdHmlRefreshStarted", hml).sendMessage(sender);

                WorldGuardHelper wgh = hml.getWorldGuardHelper();
                for (Land land : board.getOwnedLandSet()) {
                    wgh.updateRegionOfLand(land, true);
                }

                MessageBuilder.create("cmdHmlRefreshDone", hml).sendMessage(sender);
                break;
            case "free":

                if (args.length < 2) {
                    showUsage();
                    return;
                }

                HalfminerPlayer toEdit;
                try {
                    toEdit = hms.getStorageHandler().getPlayer(args[1]);
                } catch (PlayerNotFoundException e) {
                    e.sendNotFoundMessage(sender, "Land");
                    return;
                }

                int setTo = -1;
                if (args.length > 2) {
                    try {
                        setTo = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {}
                }

                ConfigurationSection landStorageRoot = hml.getLandStorage().getRootSection();
                String key = toEdit.getUniqueId().toString() + STORAGE_FREE_LANDS;
                int setFreeAmount = landStorageRoot.getInt(key, 0);
                int hasFreeAmount = board.getLands(toEdit.getUniqueId())
                        .stream()
                        .filter(Land::isFreeLand)
                        .collect(Collectors.toList())
                        .size();

                if (setTo >= 0) {
                    landStorageRoot.set(key, setTo > 0 ? setTo : null);
                    hml.getLogger().info("Set free lands for " + toEdit.getName() + " from "
                            + setFreeAmount + " to " + setTo);
                }

                MessageBuilder.create(setTo >= 0 ? "cmdHmlFreeSet" : "cmdHmlFreeShow", hml)
                        .addPlaceholderReplace("%PLAYER%", toEdit.getName())
                        .addPlaceholderReplace("%HASFREE%", String.valueOf(hasFreeAmount))
                        .addPlaceholderReplace("%CURRENTFREE%", String.valueOf(setFreeAmount))
                        .addPlaceholderReplace("%SETFREE%", String.valueOf(setTo))
                        .sendMessage(sender);

                break;
            default:
                showUsage();
        }
    }

    private void showUsage() {
        MessageBuilder.create("cmdHmlUsage", hml)
                .addPlaceholderReplace("%VERSION%", hml.getDescription().getVersion())
                .sendMessage(sender);
    }
}