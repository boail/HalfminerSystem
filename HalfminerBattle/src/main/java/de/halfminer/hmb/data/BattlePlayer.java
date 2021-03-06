package de.halfminer.hmb.data;

import de.halfminer.hmb.BattleClass;
import de.halfminer.hmb.HalfminerBattle;
import de.halfminer.hmb.arena.abs.Arena;
import de.halfminer.hmb.mode.GlobalMode;
import de.halfminer.hmb.mode.abs.BattleModeType;
import de.halfminer.hms.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.potion.PotionEffect;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Class encapsulating player specific battle data, such as his state, inventory, survival
 * data (inventory, health..) and the players arena and game partners.
 */
class BattlePlayer extends BattleClass {

    private static final Object inventoryWriteLock = new Object();

    private final UUID baseUUID;

    private BattleState state = BattleState.IDLE;
    private BattleModeType battleModeType;
    private long lastStateChange = System.currentTimeMillis();
    private PlayerData data = null;
    private boolean hasDisconnected = false;
    private boolean isUsingOwnEquipment = false;
    private List<PermissionAttachment> permissions;

    private Arena arena = null;
    private List<BattlePlayer> gamePartners = null;

    BattlePlayer(Player p) {
        super(false);
        this.baseUUID = p.getUniqueId();
    }

    Player getBase() {
        return Bukkit.getPlayer(baseUUID);
    }

    BattleState getState() {
        if (state.equals(BattleState.QUEUE_COOLDOWN)) {
            GlobalMode global = (GlobalMode) HalfminerBattle.getInstance().getBattleMode(BattleModeType.GLOBAL);
            if (lastStateChange + (global.getQueueCooldownSeconds() * 1000) < System.currentTimeMillis()) {
                setState(BattleState.IDLE);
            }
        }
        return state;
    }

    void setState(BattleState state) {
        setState(state, null);
    }

    void setState(BattleState state, BattleModeType battleModeType) {
        this.state = state;
        this.lastStateChange = System.currentTimeMillis();
        if (battleModeType != null) {
            this.battleModeType = battleModeType;
        }

        if (state.equals(BattleState.IDLE) || state.equals(BattleState.QUEUE_COOLDOWN)) {

            // remove partners if idling or in queue cooldown
            if (gamePartners != null) {

                for (BattlePlayer p : gamePartners) {
                    List<BattlePlayer> partnersOfPartner = p.getGamePartners();
                    if (partnersOfPartner != null) {
                        partnersOfPartner.remove(this);
                    }
                }
                gamePartners = null;
            }
        }
    }

    BattleModeType getBattleModeType() {
        return battleModeType;
    }

    void setArena(Arena arena) {
        this.arena = arena;
        this.battleModeType = arena.getBattleModeType();

        // add custom permissions
        String path = "battleMode." + arena.getBattleMode().getType().getConfigNode() + ".addPermissions";
        List<String> permissionsString = hmb.getConfig().getStringList(path);
        if (!permissionsString.isEmpty()) {
            Player player = getBase();
            permissions = new ArrayList<>();
            for (String permission : permissionsString) {
                permissions.add(player.addAttachment(hmb, permission, true));
            }
        }
    }

    Arena getArena() {
        return arena;
    }

    void storeData() {
        data = new PlayerData();
    }

    boolean checkAndStoreItemStack(@Nullable ItemStack toCheck) {

        if (arena == null) {
            throw new RuntimeException("checkAndStoreItemStack() called for " + getBase().getName() + " without set arena");
        }

        if (data == null) {
            throw new RuntimeException("checkAndStoreItemStack() called for " + getBase().getName() + " without set data");
        }

        if (isUsingOwnEquipment
                || toCheck == null
                || toCheck.getType().equals(Material.AIR)
                || toCheck.getType().equals(Material.GLASS_BOTTLE)) {
            return false;
        }

        ItemMeta meta = toCheck.getItemMeta();
        if (meta != null && meta.hasLore()) {
            for (String str : meta.getLore()) {
                if (str.contains(arena.getName())) {
                    return false;
                }
            }
        }

        data.extrasToRestore.add(toCheck);
        return true;
    }

    boolean isUsingOwnEquipment() {
        return isUsingOwnEquipment;
    }

    void restorePlayer() {

        Player player = getBase();
        if (data == null) {
            throw new RuntimeException("restorePlayer() called for " + player.getName() + " with no set data");
        }

        // immediately clear inventory
        if (!isUsingOwnEquipment) {
            player.closeInventory();
            for (ItemStack item : player.getInventory().getContents()) {
                checkAndStoreItemStack(item);
            }
            player.getInventory().clear();
        }

        // if dead (and still online) respawn with delay to prevent damage immunity loss glitch
        if (player.isDead() && !hasDisconnected) {
            try {
                scheduler.runTaskLater(hmb, () -> {
                    // don't restore if already ocurred due to logout in between death and task execution
                    if (data != null) {
                        player.spigot().respawn();
                        restore();
                    }
                }, 2L);
            } catch (IllegalPluginAccessException e) {
                // exception is thrown when trying to respawn dead player while shutting down
                restore();
            }
        } else {
            restore();
        }
    }

    private void restore() {

        Player player = getBase();

        try {
            player.setHealth(data.health);
            player.setFoodLevel(data.foodLevel);
            player.setSaturation(data.foodSaturation);
            player.setExhaustion(data.foodExhaustion);
            player.setFireTicks(0);
            for (PotionEffect effect : player.getActivePotionEffects())
                player.removePotionEffect(effect.getType());
            player.addPotionEffects(data.potionEffects);
        } catch (Exception e) {
            hmb.getLogger().log(Level.WARNING, "Player " + player.getName()
                    + " could not be healed properly, see stacktrace for information", e);
        }

        if (!isUsingOwnEquipment) {
            restoreInventory();
        }

        if (!player.teleport(data.loc)) {
            hmb.getLogger().warning("Player " + player.getName()
                    + " could not be teleported to his original location at " + Utils.getStringFromLocation(data.loc));
        }

        if (permissions != null) {
            if (hasDisconnected) {
                // remove with delay on disconnect, as we might kill the player after restoring
                scheduler.runTaskLater(hmb, this::removePermissionAttachments, 1L);
            } else {
                removePermissionAttachments();
            }
        }

        player.setWalkSpeed(data.walkSpeed);
        player.setGameMode(data.gameMode);

        data = null;
        arena = null;
        hasDisconnected = false;
        isUsingOwnEquipment = false;
    }

    void restoreInventory() {

        Player player = getBase();
        if (data == null) {
            throw new RuntimeException("restoreInventory() called for " + player.getName() + " with no set data");
        }

        player.closeInventory();
        player.getInventory().setContents(data.inventory);
        data.extrasToRestore.forEach(player.getInventory()::addItem);
        player.updateInventory();
        isUsingOwnEquipment = true;
    }

    void setHasDisconnected() {
        hasDisconnected = true;
    }

    void setBattlePartners(List<BattlePlayer> players) {
        gamePartners = players;
    }

    List<BattlePlayer> getGamePartners() {
        return gamePartners;
    }

    private void removePermissionAttachments() {
        permissions.forEach(PermissionAttachment::remove);
        permissions = null;
    }

    private class PlayerData {

        private final Location loc;
        private final ItemStack[] inventory;
        private final List<ItemStack> extrasToRestore = new ArrayList<>();

        private final double health;
        private final int foodLevel;
        private final float foodSaturation;
        private final float foodExhaustion;
        private final Collection<PotionEffect> potionEffects;

        private final GameMode gameMode;
        private final float walkSpeed;

        PlayerData() {

            Player player = getBase();

            player.leaveVehicle();
            loc = player.getLocation();

            player.closeInventory();
            inventory = player.getInventory().getContents();

            if (((GlobalMode) hmb.getBattleMode(BattleModeType.GLOBAL)).isSaveInventoryToDisk()) {

                scheduler.runTaskAsynchronously(hmb, () -> {

                    synchronized (inventoryWriteLock) {
                        String fileName = (System.currentTimeMillis() / 1000) + "-" + player.getName() + ".yml";

                        File path = new File(hmb.getDataFolder(), "inventories");
                        boolean pathExists = path.exists();
                        if (!pathExists) {
                            pathExists = path.mkdir();
                        }

                        if (pathExists && path.isDirectory()) {
                            File file = new File(path, fileName);
                            try {
                                if (file.createNewFile()) {
                                    YamlConfiguration configFile = new YamlConfiguration();
                                    configFile.set("inventory", inventory);
                                    configFile.set("uuid", player.getUniqueId().toString());
                                    configFile.save(file);
                                }
                            } catch (IOException e) {
                                hmb.getLogger().log(Level.WARNING,
                                        "Could not write inventory to disk with filename " + fileName, e);
                            }
                        } else hmb.getLogger().warning("Could not create sub folder in plugin directory");
                    }
                });
            }

            health = Math.min(20.0d, player.getHealth());
            foodLevel = player.getFoodLevel();
            foodSaturation = player.getSaturation();
            foodExhaustion = player.getExhaustion();
            potionEffects = player.getActivePotionEffects();

            gameMode = player.getGameMode();
            walkSpeed = player.getWalkSpeed();
        }
    }
}
