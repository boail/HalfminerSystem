package de.halfminer.hmc.module;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.halfminer.hms.manageable.Sweepable;
import de.halfminer.hms.util.Message;
import de.halfminer.hms.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * - Logs and notifies about possible wall glitching
 * - Detects dismount glitches, forces player to spawn
 * - Override Spigot teleport safety between worlds
 * - Prevents glitching with chorus fruit, instead teleports down
 * - Kills players above the netherroof
 */
@SuppressWarnings("unused")
public class ModGlitchProtection extends HalfminerModule implements Listener, Sweepable {

    private Set<Material> protectedMaterial;
    private final Cache<Player, Boolean> lastGlitchAlert = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(4, TimeUnit.SECONDS)
            .weakKeys()
            .build();

    private BukkitTask checkIfOverNether;


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveGlitchCheck(PlayerMoveEvent e) {

        if (protectedMaterial.contains(e.getFrom().getBlock().getType())
                && !e.getPlayer().hasPermission("hmc.bypass.glitchcheck")
                && Math.round(e.getFrom().getY()) == e.getFrom().getBlockY()
                && lastGlitchAlert.getIfPresent(e.getPlayer()) == null) {

            Message.create("modGlitchProtectionMove", hmc, "AntiGlitch")
                    .addPlaceholder("%PREFIX%", "Warnung")
                    .addPlaceholder("%PLAYER%", e.getPlayer().getName())
                    .addPlaceholder("%LOCATION%", Utils.getStringFromLocation(e.getTo()))
                    .addPlaceholder("%WORLD%", e.getTo().getWorld().getName())
                    .addPlaceholder("%MATERIAL%",
                            Utils.makeStringFriendly(e.getFrom().getBlock().getType().toString()))
                    .broadcast("hmc.bypass.glitchcheck", true);
            lastGlitchAlert.put(e.getPlayer(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismountGlitchCheck(VehicleExitEvent e) {

        if (!(e.getExited() instanceof Player)) return;

        final Player p = (Player) e.getExited();
        if (p.hasPermission("hmc.bypass.glitchcheck") || p.isDead()) return;

        final Location loc = e.getVehicle().getLocation();
        final World w = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (int i = 0; i < 3; i++) {
            if (protectedMaterial.contains(w.getBlockAt(x, y + i, z).getType())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleportPreventGlitch(PlayerTeleportEvent e) {

        final Player p = e.getPlayer();
        final Location to = e.getTo();

        if (p.hasPermission("hmc.bypass.teleportcheck")) return;

        if (!e.getFrom().getWorld().equals(to.getWorld())) {

            // override Spigot default teleport safety between worlds
            scheduler.runTaskLater(hmc, () -> {
                if (p.getLocation().getWorld().equals(to.getWorld())
                        && p.getLocation().distance(to) > 1.0d) {
                    p.teleport(to);
                }
            }, 1L);
        } else if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)) {

            Location current = p.getLocation();
            World world = current.getWorld();
            int xValue = current.getBlockX();
            int zValue = current.getBlockZ();

            int yValue = current.getBlockY();
            if (yValue > 255) yValue = world.getHighestBlockYAt(xValue, zValue);
            else {
                while (world.getBlockAt(xValue, yValue, zValue).getType().equals(Material.AIR) && yValue > 0)
                    yValue--;
            }

            // set destination to next block under player
            to.setX(current.getX());
            to.setY(yValue + 1);
            to.setZ(current.getZ());
            to.setYaw(current.getYaw());
            to.setPitch(current.getPitch());
        }
    }

    @Override
    public void sweep() {
        lastGlitchAlert.cleanUp();
    }

    @Override
    public void loadConfig() {

        protectedMaterial = Utils.stringListToMaterialSet(
                hmc.getConfig().getStringList("glitchProtection.protectedMaterial"));

        boolean isNetherCheckEnabled = hmc.getConfig().getBoolean("glitchProtection.netherRoofCheck", true);
        if (isNetherCheckEnabled && checkIfOverNether == null) {

            checkIfOverNether = scheduler.runTaskTimer(hmc, () -> {
                for (Player p : server.getOnlinePlayers()) {
                    Location loc = p.getLocation();
                    if (!p.hasPermission("hmc.bypass.nethercheck")
                            && !p.isDead()
                            && loc.getWorld().getEnvironment().equals(World.Environment.NETHER)
                            && loc.getBlockY() > 127) {
                        p.setHealth(0.0d);
                        Message.create("modGlitchProtectionNether", hmc, "AntiGlitch").send(p);
                        Message.create("modGlitchProtectionNetherNotify", hmc, "AntiGlitch")
                                .addPlaceholder("%PLAYER%", p.getName())
                                .addPlaceholder("%LOCATION%", Utils.getStringFromLocation(loc))
                                .broadcast("hmc.bypass.nethercheck", true);
                    }
                }
            }, 100L, 100L);
        } else if (!isNetherCheckEnabled && checkIfOverNether != null) {
            checkIfOverNether.cancel();
            checkIfOverNether = null;
        }
    }
}
