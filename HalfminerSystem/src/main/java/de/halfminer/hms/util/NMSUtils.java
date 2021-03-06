package de.halfminer.hms.util;

import de.halfminer.hms.HalfminerSystem;
import net.minecraft.server.v1_15_R1.*;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Helper methods that access minecraft server / craftbukkit internals.
 * References need to be updated on minecraft updates.
 * Not using reflection, as the code/performance overhead and worsened readability is not worth it.
 */
public final class NMSUtils {

    private NMSUtils() {}

    public static void setKiller(Player toKill, Player killer) {
        EntityPlayer toKillNMS = ((CraftPlayer) toKill).getHandle();
        toKillNMS.killer = ((CraftPlayer) killer).getHandle();
        toKillNMS.getCombatTracker()
                .trackDamage(DamageSource.playerAttack(((CraftPlayer) killer).getHandle()), 0.1f, 0.1f);
        /* Preferred method, but may get prevented by third party plugins such as NoCheatPlus: */
        //toKillNMS.damageEntity(DamageSource.playerAttack(((CraftPlayer) killer).getHandle()), Float.MAX_VALUE);
    }

    public static int getPing(Player p) {
        return ((CraftPlayer) p).getHandle().ping;
    }

    public static void sendTitlePackets(Player player, String topTitle, String subTitle, int fadeIn, int stay, int fadeOut) {

        if (!player.isOnline() || (topTitle.length() == 0 && subTitle.length() == 0)) return;
        PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;

        connection.sendPacket(new PacketPlayOutTitle(fadeIn, stay, fadeOut));
        if (topTitle.length() > 0)
            connection.sendPacket(new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE,
                    IChatBaseComponent.ChatSerializer.a("{\"text\":\"" + topTitle + "\"}")));

        if (subTitle.length() > 0)
            connection.sendPacket(new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE,
                    IChatBaseComponent.ChatSerializer.a("{\"text\":\"" + subTitle + "\"}")));
    }

    public static void sendActionBarPacket(Player player, String message) {

        if (!player.isOnline() || message.length() == 0) return;

        PacketPlayOutChat actionbar = new PacketPlayOutChat(
                IChatBaseComponent.ChatSerializer.a("{\"text\":\"" + message + "\"}"), ChatMessageType.GAME_INFO);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(actionbar);
    }

    public static void sendTablistPackets(Player player, String header, String footer) {

        if (!player.isOnline()) return;

        PacketPlayOutPlayerListHeaderFooter packet = new PacketPlayOutPlayerListHeaderFooter();

        try {
            setField(packet, "header", header);
            setField(packet, "footer", footer);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            HalfminerSystem.getInstance().getLogger().log(Level.SEVERE,
                    "Error occurred during tablist packet send", e);
        }

        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    private static void setField(Packet packet, String field, String setTo)
            throws NoSuchFieldException, IllegalAccessException {
        Field footerField = packet.getClass().getDeclaredField(field);
        footerField.setAccessible(true);
        footerField.set(packet, IChatBaseComponent.ChatSerializer.a("{\"text\":\"" + setTo + "\"}"));
        footerField.setAccessible(false);
    }
}
