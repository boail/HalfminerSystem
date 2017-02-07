package de.halfminer.hmb.data;

import de.halfminer.hmb.HalfminerBattle;
import de.halfminer.hmb.arena.abs.Arena;
import de.halfminer.hmb.enums.BattleState;
import de.halfminer.hmb.enums.GameModeType;
import de.halfminer.hmb.mode.GlobalMode;
import de.halfminer.hms.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulating player specific battle data, such as his state, inventory, survival data (inventory, health..) and
 * the players arena and game partners
 */
class BattlePlayer {

    private static final HalfminerBattle hmb = HalfminerBattle.getInstance();
    private final UUID baseUUID;

    private BattleState state = BattleState.IDLE;
    private GameModeType gameMode;
    private long lastStateChange = System.currentTimeMillis();
    private PlayerData data = null;

    private Arena arena = null;
    private List<BattlePlayer> gamePartners = null;

    BattlePlayer(Player p) {
        this.baseUUID = p.getUniqueId();
    }

    Player getBase() {
        return Bukkit.getPlayer(baseUUID);
    }

    BattleState getState() {
        if (state.equals(BattleState.QUEUE_COOLDOWN)) {
            GlobalMode global = (GlobalMode) HalfminerBattle.getInstance().getGameMode(GameModeType.GLOBAL);
            if (lastStateChange + (global.getQueueCooldownSeconds() * 1000) < System.currentTimeMillis()) {
                setState(BattleState.IDLE);
            }
        }
        return state;
    }

    void setState(BattleState state) {
        setState(state, null);
    }

    void setState(BattleState state, GameModeType gameMode) {
        this.state = state;
        this.gameMode = gameMode;
        this.lastStateChange = System.currentTimeMillis();

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

    void storeData() {
        data = new PlayerData();
    }

    void restorePlayer(boolean restoreInventory) {

        Player player = getBase();
        if (data == null)
            throw new RuntimeException("Could not restore player " + player.getName() + " as data was not set");

        if (player.isDead() && player.isOnline()) {
            try {
                Bukkit.getScheduler().runTaskLater(hmb, () -> {
                    player.spigot().respawn();
                    restore(player, restoreInventory);
                }, 2L);
            } catch (IllegalPluginAccessException e) {
                // exception is thrown when trying to respawn dead player while shutting down
                restore(player, restoreInventory);
            }
        } else {
            restore(player, restoreInventory);
        }
    }

    private void restore(Player player, boolean restoreInventory) {

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
            hmb.getLogger().warning("Player " + player.getName()
                    + " could not be healed properly, see stacktrace for information");
            e.printStackTrace();
        }

        player.setWalkSpeed(data.walkSpeed);
        player.setGameMode(data.minecraftGamemode);

        if (restoreInventory) restoreInventory();

        if (!player.teleport(data.loc)) {
            hmb.getLogger().warning("Player " + player.getName()
                    + " could not be teleported to his original location at " + Utils.getStringFromLocation(data.loc));
        }
        data = null;
    }

    void restoreInventory() {
        if (data != null) {
            Player player = getBase();
            player.closeInventory();
            player.getInventory().setContents(data.inventory);
            player.updateInventory();
        }
    }

    void setBattlePartners(List<BattlePlayer> players) {
        gamePartners = players;
    }

    List<BattlePlayer> getGamePartners() {
        return gamePartners;
    }

    void setArena(Arena arena) {
        this.arena = arena;
        this.gameMode = arena.getGameMode();
    }

    Arena getArena() {
        return arena;
    }

    GameModeType getGameMode() {
        return gameMode;
    }

    private class PlayerData {

        private final Location loc;
        private final ItemStack[] inventory;

        private final double health;
        private final int foodLevel;
        private final float foodSaturation;
        private final float foodExhaustion;
        private final Collection<PotionEffect> potionEffects;

        private final GameMode minecraftGamemode;
        private final float walkSpeed;

        PlayerData() {

            Player player = getBase();

            player.leaveVehicle();
            loc = player.getLocation();

            player.closeInventory();
            inventory = player.getInventory().getContents();

            if (((GlobalMode) hmb.getGameMode(GameModeType.GLOBAL)).isSaveInventoryToDisk()) {

                String fileName = player.getName() + String.valueOf(System.currentTimeMillis() / 1000) + ".yml";

                File path = new File(hmb.getDataFolder() + File.separator + "inventories");
                boolean pathExists = path.exists();
                if (!pathExists) {
                    pathExists = path.mkdir();
                }

                if (pathExists) {
                    File file = new File(path, fileName);
                    try {
                        if (file.createNewFile()) {
                            YamlConfiguration configFile = new YamlConfiguration();
                            configFile.set("inventory", inventory);
                            configFile.save(file);
                        }
                    } catch (IOException e) {
                        hmb.getLogger().warning("Could not write inventory to disk with filename " + fileName);
                        e.printStackTrace();
                    }
                } else hmb.getLogger().warning("Could not create sub folder in plugin directory");
            }

            health = Math.min(20.0d, player.getHealth());
            foodLevel = player.getFoodLevel();
            foodSaturation = player.getSaturation();
            foodExhaustion = player.getExhaustion();
            potionEffects = player.getActivePotionEffects();

            minecraftGamemode = player.getGameMode();
            walkSpeed = player.getWalkSpeed();
        }
    }
}