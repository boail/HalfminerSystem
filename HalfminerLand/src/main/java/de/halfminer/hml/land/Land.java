package de.halfminer.hml.land;

import de.halfminer.hml.LandClass;
import de.halfminer.hml.WorldGuardHelper;
import de.halfminer.hms.handler.storage.DataType;
import de.halfminer.hms.handler.storage.HalfminerPlayer;
import de.halfminer.hms.handler.storage.PlayerNotFoundException;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class Land extends LandClass {

    private static final char PATH_COORDINATE_SPLIT_CHAR = 'z';
    private static final String STORAGE_OWNER = ".owner";
    private static final String STORAGE_IS_FREE = ".isFreeLand";
    private static final String STORAGE_IS_SERVER_LAND = ".isServerLand";
    private static final String STORAGE_TELEPORT = ".teleport";
    private static final String STORAGE_TELEPORT_NAME = ".teleport.name";
    private static final String STORAGE_TELEPORT_LOCATION = ".teleport.location";

    private final WorldGuardHelper wgh;
    private final Chunk chunk;

    private final ConfigurationSection mapSection;
    private final String path;

    private HalfminerPlayer owner;
    private boolean isFreeLand = false;
    private boolean isServerLand = false;

    private String teleportName;
    private Location teleportLocation;

    private int playersOnLand;
    private boolean isAbandoned;


    Land(Chunk chunk, ConfigurationSection mapSection) {

        this.wgh = hml.getWorldGuardHelper();
        this.chunk = chunk;

        this.mapSection = mapSection;
        this.path = chunk.getWorld().getName() + '.' + chunk.getX() + PATH_COORDINATE_SPLIT_CHAR + chunk.getZ();

        // load if data is stored
        if (mapSection.isConfigurationSection(path)) {

            ConfigurationSection landSection = mapSection.getConfigurationSection(path);
            if (landSection.contains(STORAGE_OWNER.substring(1))) {
                String uuidString = landSection.getString(STORAGE_OWNER.substring(1));
                try {
                    owner = hms.getStorageHandler().getPlayer(UUID.fromString(uuidString));
                } catch (PlayerNotFoundException e) {
                    hml.getLogger().warning("Player with UUID " + uuidString + "' not found, skipping land at " + toString());
                }
            }

            isFreeLand = landSection.getBoolean(STORAGE_IS_FREE.substring(1), false);
            isServerLand = landSection.getBoolean(STORAGE_IS_SERVER_LAND.substring(1), false);

            if (landSection.contains(STORAGE_TELEPORT_NAME)) {
                teleportName = landSection.getString(STORAGE_TELEPORT_NAME.substring(1));
                teleportLocation = (Location) landSection.get(STORAGE_TELEPORT_LOCATION.substring(1));
            }
        }

        this.isAbandoned = wgh.isMarkedAbandoned(this);
        boolean abandonmentWasUpdated = updateAbandonmentStatus();

        // refresh region if it didn't happen yet, ensures that if land has no owner it will be deleted
        if (!abandonmentWasUpdated) {
            wgh.updateRegionOfLand(this);
        }
    }

    public BuyableStatus getBuyableStatus() {

        if (!isAbandoned()) {
            if (hasOwner()) {
                return BuyableStatus.ALREADY_OWNED;
            }

            if (!wgh.isLandFree(this)) {
                return BuyableStatus.LAND_NOT_BUYABLE;
            }
        }

        if (playersOnLand > 1) {
            return BuyableStatus.OTHER_PLAYERS_ON_LAND;
        }

        return BuyableStatus.BUYABLE;
    }

    public SellableStatus getSellableStatus(Player player) {

        if (!hasOwner()) {
            return SellableStatus.NO_OWNER;
        }

        if (!isOwner(player)) {
            return SellableStatus.NOT_OWNED;
        }

        if (playersOnLand > 1) {
            return SellableStatus.OTHER_PLAYERS_ON_LAND;
        }

        if (hasTeleportLocation()) {
            return SellableStatus.HAS_TELEPORT;
        }

        return SellableStatus.SELLABLE;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public HalfminerPlayer getOwner() {
        return owner;
    }

    public String getOwnerName() {

        if (hasOwner()) {
            return isServerLand ? hml.getConfig().getString("serverName", "Server") : owner.getName();
        }

        return MessageBuilder.returnMessage("noOwner", hml, false);
    }

    public boolean isOwner(Player player) {
        return hasOwner()
                && player.getUniqueId().equals(owner.getUniqueId())
                || (isServerLand() && player.hasPermission("hml.ownsserverland"));
    }

    public void setOwner(HalfminerPlayer owner) {
        this.owner = owner;
        wgh.updateRegionOfLand(this, false, false);

        updateAbandonmentStatus();

        if (owner != null) {
            mapSection.set(path + STORAGE_OWNER, owner.getUniqueId().toString());
        } else {
            removeTeleport();
            setFreeLand(false);
            setServerLand(false);
            mapSection.set(path, null);
        }
    }

    public void setFreeLand(boolean isFreeLand) {
        this.isFreeLand = isFreeLand;
        mapSection.set(path + STORAGE_IS_FREE, isFreeLand ? true : null);
    }

    public boolean isFreeLand() {
        return isFreeLand || isServerLand;
    }

    public void setServerLand(boolean isServerLand) {
        this.isServerLand = isServerLand;
        mapSection.set(path + STORAGE_IS_SERVER_LAND, isServerLand ? true : null);
    }

    public boolean isServerLand() {
        return isServerLand;
    }

    public boolean isAbandoned() {
        return isAbandoned && !isServerLand;
    }

    public boolean updateAbandonmentStatus() {

        if (hasOwner()) {

            int abandonedAfterSeconds = hml.getConfig().getInt("landAbandonedAfterDays", 21) * 24 * 60 * 60;
            long lastSeen = owner.getLong(DataType.LAST_SEEN);

            if (lastSeen == Long.MAX_VALUE) {
                return setAbandoned(false);
            }

            boolean setAbandoned = lastSeen + abandonedAfterSeconds < (System.currentTimeMillis() / 1000);
            return setAbandoned(setAbandoned);
        } else {
            return setAbandoned(false);
        }
    }

    private boolean setAbandoned(boolean isAbandoned) {
        if (this.isAbandoned != isAbandoned) {
            this.isAbandoned = isAbandoned;
            wgh.updateRegionOfLand(this);
            return true;
        }

        return false;
    }

    public boolean hasTeleportLocation() {
        return teleportLocation != null;
    }

    public String getTeleportName() {
        return teleportName;
    }

    public Location getTeleportLocation() {
        return teleportLocation;
    }

    public void setTeleport(String teleportName, Location teleportLocation) {
        this.teleportName = teleportName;
        this.teleportLocation = teleportLocation;

        if (hasTeleportLocation()) {
            mapSection.set(path + STORAGE_TELEPORT_NAME, teleportName);
            mapSection.set(path + STORAGE_TELEPORT_LOCATION, teleportLocation);
        } else {
            mapSection.set(path + STORAGE_TELEPORT, null);
        }
    }

    public void removeTeleport() {
        setTeleport(null, null);
    }

    void playerEntered() {
        if (playersOnLand == 0 && hasOwner()) {
            updateAbandonmentStatus();
        }

        playersOnLand++;
    }

    /**
     * Method to be called if a player leaves this land.
     */
    void playerLeft() {

        if (playersOnLand > 0) {
            playersOnLand--;
        } else {
            hml.getLogger().warning("playerLeft() method called for already empty land (" + toString() + ")");
        }
    }

    boolean canBeRemoved() {
        return !hasOwner() && playersOnLand == 0;
    }

    boolean isNeighbour(Land land) {
        int distanceX = Math.abs(land.getChunk().getX() - this.chunk.getX());
        int distanceZ = Math.abs(land.getChunk().getZ() - this.chunk.getZ());
        return distanceX <= 1 && distanceZ <= 1 && (distanceX == 0 || distanceZ == 0);
    }

    /**
     * Check if a player can interact on this land.
     *
     * @param player to check
     * @return true if land is not owned, abandoned, player is land owner or added as member of land
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasPermission(Player player) {
        return !hasOwner()
                || isOwner(player)
                || isAbandoned()
                || wgh.getMemberList(this).getUniqueIds().contains(player.getUniqueId());
    }

    public Set<UUID> getMemberSet() {
        return wgh.getMemberList(this).getUniqueIds();
    }

    public boolean addMember(UUID toAdd) {
        return wgh.addMemberToRegion(this, toAdd);
    }

    public boolean removeMember(UUID toRemove) {
        return wgh.removeMemberFromRegion(this, toRemove);
    }

    @Override
    public String toString() {
        return chunk.getWorld().getName() + ",x" + chunk.getX() + ",z" + chunk.getZ();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Land land = (Land) o;
        return toString().equals(land.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }


    public enum BuyableStatus {
        ALREADY_OWNED,
        LAND_NOT_BUYABLE,
        OTHER_PLAYERS_ON_LAND,
        BUYABLE
    }

    public enum SellableStatus {
        NO_OWNER,
        NOT_OWNED,
        OTHER_PLAYERS_ON_LAND,
        HAS_TELEPORT,
        SELLABLE
    }
}
