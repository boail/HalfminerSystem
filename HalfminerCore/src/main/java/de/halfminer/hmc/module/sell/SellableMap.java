package de.halfminer.hmc.module.sell;

import de.halfminer.hmc.CoreClass;
import de.halfminer.hms.util.MessageBuilder;
import de.halfminer.hms.util.Pair;
import de.halfminer.hms.util.StringArgumentSeparator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

/**
 * Class managing {@link Sellable Sellables} read from config and the current cycle they are in,
 * aswell as storing/reading current cycle from cold storage and kicking off new cycles on demand.
 */
public class SellableMap extends CoreClass {

    private int cycleTimeSeconds;
    private double priceAdjustMultiplier;
    private double priceVarianceFactor;
    private int unitsUntilIncrease;
    private Map<Integer, List<Sellable>> sellables = new HashMap<>();

    private List<Sellable> cycleSellables;
    private Map<Pair<Material, Short>, Sellable> cycleSellablesLookup;

    private long cycleExpiry;
    private BukkitTask nextCycleTask;


    public SellableMap() {
        super(false);

        // store current cycle every 15 minutes
        scheduler.runTaskTimerAsynchronously(hmc, this::storeCurrentCycle, 18000L, 18000L);
    }

    public List<Sellable> getCycleSellables() {
        return cycleSellables;
    }

    public Sellable getSellableAtSlot(int slotId) {
        return slotId < cycleSellables.size() && slotId >= 0 ? cycleSellables.get(slotId) : null;
    }

    public Sellable getSellableFromItemStack(ItemStack item) {
        return cycleSellablesLookup.get(new Pair<>(item.getType(), item.getDurability()));
    }

    public void configReloaded(ConfigurationSection sellableSection, int cycleTimeSeconds,
                               double priceAdjustMultiplier, double priceVarianceFactor, int unitsUntilIncrease) {

        this.cycleTimeSeconds = Math.max(cycleTimeSeconds, 10);
        this.priceAdjustMultiplier = Math.max(priceAdjustMultiplier, 0.01d);
        this.priceVarianceFactor = Math.max(priceVarianceFactor, 0.0d);
        this.unitsUntilIncrease = Math.max(unitsUntilIncrease, 1);

        storeCurrentCycle();

        sellables = new HashMap<>();
        clearCurrentCycle();

        for (String group : sellableSection.getKeys(false)) {
            int groupAsInt;
            try {
                groupAsInt = Integer.parseInt(group);
            } catch (NumberFormatException e) {
                MessageBuilder.create("modSellMapLogGroupInvalid", hmc)
                        .addPlaceholderReplace("%GROUP%", group)
                        .logMessage(Level.WARNING);
                continue;
            }

            for (String sellable : sellableSection.getStringList(group)) {
                StringArgumentSeparator separator = new StringArgumentSeparator(sellable, ',');
                if (!separator.meetsLength(4)) {
                    MessageBuilder.create("modSellMapLogSellableInvalidFormat", hmc)
                            .addPlaceholderReplace("%SELLABLE%", sellable)
                            .logMessage(Level.WARNING);
                    continue;
                }

                Material material = Material.matchMaterial(separator.getArgument(1));
                short durability = (short) separator.getArgumentInt(2);
                String messageName = separator.getArgument(0);
                int baseUnitAmount = separator.getArgumentIntMinimum(3, 1);

                if (material != null) {
                    Sellable currentSellable = new Sellable(
                            this, groupAsInt, material, durability, messageName, baseUnitAmount
                    );

                    if (sellables.containsKey(currentSellable.getGroupId())) {
                        sellables.get(currentSellable.getGroupId()).add(currentSellable);
                    } else {
                        List<Sellable> newList = new ArrayList<>();
                        newList.add(currentSellable);
                        sellables.put(currentSellable.getGroupId(), newList);
                    }
                } else {
                    MessageBuilder.create("modSellMapLogMaterialNotExists", hmc)
                            .addPlaceholderReplace("%MATERIAL%", separator.getArgument(1))
                            .logMessage(Level.WARNING);
                }
            }
        }

        if (sellables.isEmpty()) {
            if (nextCycleTask != null) {
                nextCycleTask.cancel();
            }
            return;
        }

        // read sellables from storage, if old cycle is stored (persistance after full reloads/restarts)
        Object cycleSectionObject = coreStorage.get("sellcycle");
        if (cycleSectionObject instanceof ConfigurationSection) {

            ConfigurationSection cycleSection = (ConfigurationSection) cycleSectionObject;
            long cycleExpiry = cycleSection.getLong("expires");
            if ((System.currentTimeMillis() / 1000) < cycleExpiry) {

                for (String key : cycleSection.getKeys(false)) {

                    if (key.equals("expires")) {
                        continue;
                    }

                    int groupId = cycleSection.getInt(key + ".groupId");
                    Material material = Material.matchMaterial(cycleSection.getString(key + ".material", ""));
                    short durability = (short) cycleSection.getInt(key + ".durability");
                    String stateString = cycleSection.getString(key + ".state");

                    List<Sellable> sellablesInGroup = sellables.get(groupId);
                    if (sellablesInGroup != null && material != null) {

                        for (Sellable sellable : sellablesInGroup) {
                            if (sellable.getMaterial().equals(material) && sellable.getDurability() == durability) {
                                sellable.setState(stateString);
                                addSellableToCurrentCycle(sellable);
                                break;
                            }
                        }

                    } else {
                        clearCurrentCycle();
                        break;
                    }
                }

                if (cycleSellables.isEmpty()) {
                    MessageBuilder.create("modSellMapLogCycleNotLoaded", hmc).logMessage(Level.WARNING);
                } else {
                    this.cycleExpiry = cycleExpiry;
                }

            } else {
                // cycle expired already
                coreStorage.set("sellcycle", null);
            }

        } else if (cycleSectionObject != null) {
            MessageBuilder.create("modSellMapLogCycleInvalidFormat", hmc).logMessage(Level.WARNING);
        }

        if (nextCycleTask == null || cycleSellables.isEmpty()) {
            checkNextCycle();
        }
    }

    public void storeCurrentCycle() {

        if (cycleSellables != null && !cycleSellables.isEmpty()) {

            coreStorage.set("sellcycle", null);
            coreStorage.set("sellcycle.expires", cycleExpiry);

            for (int i = 0; i < cycleSellables.size(); i++) {
                Sellable sellable = cycleSellables.get(i);
                String basePath = "sellcycle." + i + ".";
                coreStorage.set(basePath + "groupId", sellable.getGroupId());
                coreStorage.set(basePath + "material", sellable.getMaterial().toString());
                coreStorage.set(basePath + "durability", sellable.getDurability());
                coreStorage.set(basePath + "state", sellable.getStateString());
            }
        }
    }

    public void forceNewCycle() {
        cycleExpiry = 0L;
        checkNextCycle();
    }

    private void checkNextCycle() {

        long currentTime = System.currentTimeMillis() / 1000;
        long timeUntilNextCycle = cycleExpiry - currentTime;

        // kick off next cycle
        if (timeUntilNextCycle <= 0 || cycleSellables.isEmpty()) {

            clearCurrentCycle();
            timeUntilNextCycle = cycleTimeSeconds;
            cycleExpiry = currentTime + timeUntilNextCycle;

            Random rnd = new Random();
            for (Integer amount : sellables.keySet()) {

                Set<Sellable> group = new HashSet<>(sellables.get(amount));
                for (int leftToAdd = Math.min(amount, group.size()); leftToAdd > 0; leftToAdd--) {

                    int currentElement = 0;
                    int elementToGet = rnd.nextInt(group.size());

                    Iterator<Sellable> iterator = group.iterator();
                    while (iterator.hasNext()) {
                        Sellable current = iterator.next();
                        if (elementToGet == currentElement) {
                            addSellableToCurrentCycle(current);
                            iterator.remove();
                            break;
                        }
                        currentElement++;
                    }
                }
            }

            storeCurrentCycle();
            server.getPluginManager().callEvent(new SellCycleRefreshEvent());
        }

        if (nextCycleTask != null) {
            nextCycleTask.cancel();
        }

        nextCycleTask = scheduler.runTaskLater(hmc, this::checkNextCycle, timeUntilNextCycle * 20);
    }

    public long getCycleTimeLeft() {
        return cycleExpiry - (System.currentTimeMillis() / 1000);
    }

    private void addSellableToCurrentCycle(Sellable toAdd) {
        if (cycleSellables.size() < 27) {
            cycleSellables.add(toAdd);
            cycleSellablesLookup.put(new Pair<>(toAdd.getMaterial(), toAdd.getDurability()), toAdd);
        }
    }

    private void clearCurrentCycle() {
        cycleExpiry = 0L;

        if (cycleSellables != null) {
            cycleSellables.forEach(Sellable::doRandomReset);
        }

        cycleSellables = new ArrayList<>();
        cycleSellablesLookup = new HashMap<>();
    }

    double getPriceAdjustMultiplier() {
        return priceAdjustMultiplier;
    }

    double getPriceVarianceFactor() {
        return priceVarianceFactor;
    }

    int getUnitsUntilIncrease() {
        return unitsUntilIncrease;
    }
}
