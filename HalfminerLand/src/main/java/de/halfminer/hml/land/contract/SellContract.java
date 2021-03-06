package de.halfminer.hml.land.contract;

import de.halfminer.hml.land.Land;
import de.halfminer.hms.util.Utils;
import org.bukkit.entity.Player;

public class SellContract extends AbstractContract {

    private final double cost;


    public SellContract(Player sellingPlayer, Land landToSell) {
        super(sellingPlayer, landToSell);

        if (landToSell.isFreeLand()) {
            this.cost = 0d;
        } else {
            double lastCostStorage = landStorage.getLandPlayer(landToSell.getOwner()).getHighestCost();
            double sellRefundMultiplier = hml.getConfig().getDouble("priceFormula.sellRefundMultiplier", .8d);

            this.cost = Utils.roundDouble(lastCostStorage * sellRefundMultiplier);
        }
    }

    @Override
    void fulfill(Land land) {

        boolean isFreeLand = land.isFreeLand();
        land.setOwner(null);

        if (cost != 0d) {
            hms.getHooksHandler().addMoney(player, cost);
        }

        if (!isFreeLand) {
            landStorage.getLandPlayer(player).removeHighestCost();
            hml.getLogger().info(player.getName() + " received $" + cost + " for selling land at [" + land + "]");
        } else {
            hml.getLogger().info(player.getName() + " sold his free land at [" + land + "]");
        }
    }

    @Override
    public double getCost() {
        return cost;
    }
}
