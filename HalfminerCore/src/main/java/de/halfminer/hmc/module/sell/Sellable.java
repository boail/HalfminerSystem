package de.halfminer.hmc.module.sell;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Class encapsulating an item that is up for sale, an it's necessary metadata,
 * managed by {@link SellableMap}. Also handles it's current cycle based data, as
 * stored in current {@link SellCycle} and determines revenue from given sell amount.
 */
public interface Sellable {

    SellableGroup getGroup();

    Material getMaterial();

    String getStateString();

    int getBaseUnitAmount();

    Map<UUID, Integer> getAmountSoldMap();

    long getCurrentUnitAmount(Player player);

    int getAmountUntilNextIncrease(Player player);

    int getAmountSoldTotal();

    void setState(String state);

    void doReset();

    boolean isSimiliar(Sellable sellable);

    void copyStateFromSellable(Sellable toCopy);

    ItemStack getItemStack();

    String getMessageName();

    /**
     * Stack is matching if it is the same Material.
     *
     * @param itemStack item to compare
     * @return true if stacks match, false else
     */
    boolean isMatchingStack(ItemStack itemStack);

    double getRevenue(Player hasSold, int amountSold);

    /**
     * Gets the player and amount who sold most. This information is not persistent across restarts.
     *
     * @return {@link Map.Entry}, where key is {@link UUID} of player who sold most and value the amount as {@link Integer}
     */
    Map.Entry<UUID, Integer> soldMostBy();
}
