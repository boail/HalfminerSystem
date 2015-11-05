package de.halfminer.hms.cmd;

import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.util.Language;
import de.halfminer.hms.util.StatsType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Cmdverkauf extends BaseCommand {

    private Player player;
    private Economy econ;
    private String[] args;
    private Material toBeSold;
    private int loopCount = 0;
    private int sellCountTotal = 0;

    public Cmdverkauf() {
        this.permission = "hms.verkauf";
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {

        econ = HalfminerSystem.getEconomy();
        this.args = args;

        if (econ == null) {
            sender.sendMessage(Language.getMessagePlaceholders("commandVerkaufNoVault", true, "%PREFIX%", "Verkauf"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(Language.getMessagePlaceholders("notAPlayer", true, "%PREFIX%", "Verkauf"));
            return;
        }

        player = (Player) sender;

        if (args.length > 0) {

            switch (args[0].toLowerCase()) {
                case "cactus":
                    toBeSold = Material.CACTUS;
                    break;
                case "wheat":
                    toBeSold = Material.WHEAT;
                    break;
                case "pumpkin":
                    toBeSold = Material.PUMPKIN;
                    break;
                case "brownmushroom":
                    toBeSold = Material.BROWN_MUSHROOM;
                    break;
                case "redmushroom":
                    toBeSold = Material.RED_MUSHROOM;
                    break;
                case "melon":
                    toBeSold = Material.MELON;
                    break;
                case "cocoa":
                    toBeSold = Material.COCOA;
                    break;
                case "potato":
                    toBeSold = Material.POTATO;
                    break;
                case "carrot":
                    toBeSold = Material.CARROT;
                    break;
                case "sugarcane":
                    toBeSold = Material.SUGAR_CANE;
                    break;
                case "netherwart":
                    toBeSold = Material.NETHER_STALK;
                    break;
                default:
                    toBeSold = null;
                    break;
            }

            if (toBeSold != null) {

                sellLoop();
            } else {

                sender.sendMessage(Language.getMessagePlaceholders("commandVerkaufUsage", true, "%PREFIX%", "Verkauf"));
            }

        } else {
            sender.sendMessage(Language.getMessagePlaceholders("commandVerkaufUsage", true, "%PREFIX%", "Verkauf"));
        }
    }

    private void sellLoop() {

        Inventory playerInv =  player.getInventory();
        HashMap<Integer, ? extends ItemStack> items = playerInv.all(toBeSold);

        int sellCount = 0;

        for (Map.Entry<Integer, ? extends ItemStack> entry : items.entrySet()) {
            sellCount += entry.getValue().getAmount();
            playerInv.setItem(entry.getKey(), null);
        }

        player.updateInventory();
        sellCountTotal += sellCount;

        if (++loopCount < 5 && sellCount > 0) {

            hms.getServer().getScheduler().scheduleSyncDelayedTask(hms, new Runnable() {
                @Override
                public void run() {
                    sellLoop();
                }
            }, 20L);

        } else {

            if (sellCountTotal > 0) {

                //get rank multiplier
                double multiplier = 1.0d;
                if (player.hasPermission("hms.level.5")) multiplier = 2.5d;
                else if (player.hasPermission("hms.level.4")) multiplier = 2.0d;
                else if (player.hasPermission("hms.level.3")) multiplier = 1.75d;
                else if (player.hasPermission("hms.level.2")) multiplier = 1.5d;
                else if (player.hasPermission("hms.level.1")) multiplier = 1.25d;

                //calculate revenue
                int baseValue = hms.getConfig().getInt("command.verkauf." + args[0].toLowerCase(), 1000);
                double revenue = (sellCountTotal / (double) baseValue) * multiplier;

                //deposit and round
                econ.depositPlayer(player, revenue);
                storage.incrementStatsDouble(player, StatsType.REVENUE, revenue);
                revenue = Math.round(revenue * 100) / 100.0d;

                //print message
                String materialFriendly = Language.makeMaterialStringFriendly(toBeSold);
                player.sendMessage(Language.getMessagePlaceholders("commandVerkaufSuccess", true, "%PREFIX%", "Verkauf",
                        "%MATERIAL%", materialFriendly, "%MONEY%", String.valueOf(revenue),
                        "%AMOUNT%", String.valueOf(sellCountTotal)));

                hms.getLogger().info(Language.getMessagePlaceholders("commandVerkaufSuccessLog", false, "%PLAYER%",
                        player.getName(), "%MATERIAL%", materialFriendly, "%MONEY%", String.valueOf(revenue),
                        "%AMOUNT%", String.valueOf(sellCountTotal)));
            } else {

                player.sendMessage(Language.getMessagePlaceholders("commandVerkaufNotInInv", true, "%PREFIX%", "Verkauf",
                        "%MATERIAL%", Language.makeMaterialStringFriendly(toBeSold)));
            }
        }
    }
}
