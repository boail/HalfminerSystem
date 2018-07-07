package de.halfminer.hml.cmd;

import de.halfminer.hml.land.Land;
import de.halfminer.hml.land.contract.AbstractContract;
import de.halfminer.hml.land.contract.BuyContract;
import de.halfminer.hms.util.MessageBuilder;
import de.halfminer.hms.util.StringArgumentSeparator;

import java.util.List;

public class Cmdbuy extends LandCommand {


    public Cmdbuy() {
        super("buy");
    }

    @Override
    public void execute() {

        if (!isPlayer) {
            sendNotAPlayerMessage();
            return;
        }

        Land landToBuy = board.getLandAt(player);

        boolean buyAsServer = args.length > 0
                && args[0].equalsIgnoreCase("server")
                && player.hasPermission("hml.cmd.buy.server");

        // check config buy restrictions
        if (!buyAsServer) {

            List<String> worldRestrictions = hml.getConfig().getStringList("buyLimits.worldRestrictions");
            String worldName = landToBuy.getWorld().getName();
            for (String minimumCoordinate : worldRestrictions) {

                StringArgumentSeparator separator = new StringArgumentSeparator(minimumCoordinate, ',');
                if (separator.getArgument(0).equals(worldName)) {

                    int minimumCoordinateInt = separator.getArgumentInt(1);

                    // check if world is disabled
                    if (minimumCoordinateInt < 0 && !player.hasPermission("hml.bypass.buydisabledworld")) {
                        MessageBuilder.create("cmdBuyNotBuyableWorld", hml).sendMessage(player);
                        return;
                    }

                    if (!player.hasPermission("hml.bypass.minimumcoordinates")) {

                        // check if minimum coordinate requirement is met
                        if (landToBuy.getXLandCorner() < minimumCoordinateInt
                                && landToBuy.getXLandCorner() > -minimumCoordinateInt
                                && landToBuy.getZLandCorner() < minimumCoordinateInt
                                && landToBuy.getZLandCorner() > -minimumCoordinateInt) {

                            MessageBuilder.create("cmdBuyNotBuyableCoordinate", hml)
                                    .addPlaceholderReplace("%MINIMUMCOORDS%", minimumCoordinateInt)
                                    .sendMessage(player);
                            return;
                        }
                    }

                    break;
                }
            }
        }

        // check status
        Land.BuyableStatus status = landToBuy.getBuyableStatus();

        if (status.equals(Land.BuyableStatus.ALREADY_OWNED)) {

            MessageBuilder.create("cmdBuyAlreadyOwned" + (landToBuy.isOwner(player) ? "Self" : ""), hml)
                    .addPlaceholderReplace("%PLAYER%", landToBuy.getOwnerName())
                    .sendMessage(player);
            return;
        }

        if (!buyAsServer) {

            if (status.equals(Land.BuyableStatus.LAND_NOT_BUYABLE)) {
                MessageBuilder.create("cmdBuyNotBuyable", hml).sendMessage(player);
                return;
            }

            if (status.equals(Land.BuyableStatus.OTHER_PLAYERS_ON_LAND)) {
                MessageBuilder.create("cmdBuyNotBuyableNotVacant", hml).sendMessage(player);
                return;
            }
        }

        // get/create contract
        BuyContract contract = null;
        if (contractManager.hasContract(player)) {
            AbstractContract absContract = contractManager.getContract(player, landToBuy);
            if (absContract instanceof BuyContract) {
                contract = (BuyContract) absContract;
            }
        }

        int freeLandsMax = hml.getLandStorage().getLandPlayer(player).getFreeLands();
        if (buyAsServer || player.hasPermission("hml.cmd.buy.free")) {
            freeLandsMax = Integer.MAX_VALUE;
        }

        int freeLandsOwned = 0;
        int paidLandsOwned = 0;
        for (Land land : board.getLands(player)) {
            if (land.isFreeLand()) {
                freeLandsOwned++;
            } else {
                paidLandsOwned++;
            }
        }

        if (contract == null) {

            if (freeLandsOwned < freeLandsMax) {
                contract = new BuyContract(player, landToBuy);
            } else {
                contract = new BuyContract(player, landToBuy, paidLandsOwned);
            }

            if (buyAsServer) {
                contract.setCanBeFulfilled();
            }

            contractManager.setContract(contract);
        }

        // check money
        double money = hms.getHooksHandler().getMoney(player);
        double cost = contract.getCost();
        if (cost > money) {
            MessageBuilder.create("notEnoughMoney", hml)
                    .addPlaceholderReplace("%COST%", cost)
                    .sendMessage(player);
            return;
        }

        if (contract.canBeFulfilled()) {

            if (landToBuy.isAbandoned() && !landToBuy.isFreeLand()) {
                landToBuy.removeTeleport();
                hml.getLandStorage().getLandPlayer(landToBuy.getOwner()).removeHighestCost();
            }

            // buy land
            contractManager.fulfillContract(contract);
            String messageKey = "cmdBuySuccess" + (buyAsServer ? "AsServer" : (contract.isFreeBuy() ? "Free" : ""));
            MessageBuilder.create(messageKey, hml)
                    .addPlaceholderReplace("%COST%", cost)
                    .addPlaceholderReplace("%FREELANDSOWNED%", freeLandsOwned + 1)
                    .addPlaceholderReplace("%FREELANDSMAX%", freeLandsMax == Integer.MAX_VALUE ? "-" : freeLandsMax)
                    .sendMessage(player);

            if (buyAsServer) {
                landToBuy.setServerLand(true);
            }

        } else {

            board.showChunkParticles(player, landToBuy);
            contract.setCanBeFulfilled();

            MessageBuilder.create("cmdBuyConfirm" + (contract.isFreeBuy() ? "Free" : ""), hml)
                    .addPlaceholderReplace("%COST%", cost)
                    .sendMessage(player);
        }
    }
}
