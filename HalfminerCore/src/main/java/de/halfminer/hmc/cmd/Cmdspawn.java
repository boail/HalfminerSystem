package de.halfminer.hmc.cmd;

import de.halfminer.hmc.cmd.abs.HalfminerCommand;
import de.halfminer.hmc.enums.ModuleType;
import de.halfminer.hmc.modules.ModRespawn;
import de.halfminer.hms.exception.PlayerNotFoundException;
import de.halfminer.hms.handlers.HanTeleport;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * - Teleport player to spawn
 * - Teleport other players to spawn with permission
 * - Teleport offline players to spawn once they login
 * - Set the spawnpoint (Command /spawn s, only with permission)
 */
@SuppressWarnings("unused")
public class Cmdspawn extends HalfminerCommand {

    private final ModRespawn respawn = (ModRespawn) hmc.getModule(ModuleType.RESPAWN);

    public Cmdspawn() {
        this.permission = "hmc.spawn";
    }

    @Override
    public void execute() {

        if (args.length == 0) teleport(sender, false);
        else {

            if (isPlayer && args[0].equalsIgnoreCase("s") && sender.hasPermission("hmc.spawn.set")) {

                MessageBuilder.create("cmdSpawnSet", hmc, "Spawn").sendMessage(player);
                respawn.setSpawn(player.getLocation());

            } else if (sender.hasPermission("hmc.spawn.others")) {

                Player toTeleport = server.getPlayer(args[0]);
                if (toTeleport != null) {

                    if (toTeleport.equals(sender)) teleport(toTeleport, false);
                    else {

                        teleport(toTeleport, true);
                        MessageBuilder.create("cmdSpawnOthers", hmc, "Spawn")
                                .addPlaceholderReplace("%PLAYER%", toTeleport.getName())
                                .sendMessage(sender);
                    }
                }
                else {
                    try {

                        OfflinePlayer p = storage.getPlayer(args[0]).getBase();
                        MessageBuilder.create(respawn.teleportToSpawnOnJoin(p) ?
                                "cmdSpawnOthersOfflineAdd" : "cmdSpawnOthersOfflineRemove", hmc, "Spawn")
                                .addPlaceholderReplace("%PLAYER%", p.getName())
                                .sendMessage(sender);
                    } catch (PlayerNotFoundException e) {
                        e.sendNotFoundMessage(sender, "Spawn");
                    }
                }
            } else teleport(sender, false);
        }
    }

    private void teleport(CommandSender toTeleport, boolean forced) {

        if (toTeleport instanceof Player) {

            HanTeleport tp = hms.getTeleportHandler();

            if (forced) {
                MessageBuilder.create("modRespawnForced", hmc, "Spawn").sendMessage(toTeleport);
                tp.startTeleport((Player) toTeleport, respawn.getSpawn(), 0);
            }
            else tp.startTeleport((Player) toTeleport, respawn.getSpawn());

        } else sendNotAPlayerMessage("Spawn");
    }
}