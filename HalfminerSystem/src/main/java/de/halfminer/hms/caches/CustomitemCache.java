package de.halfminer.hms.caches;

import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.exception.CachingException;
import de.halfminer.hms.exception.FormattingException;
import de.halfminer.hms.exception.GiveItemException;
import de.halfminer.hms.util.MessageBuilder;
import de.halfminer.hms.util.Pair;
import de.halfminer.hms.util.StringArgumentSeparator;
import de.halfminer.hms.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

/**
 * Parses a CustomtextCache to create a custom {@link ItemStack} that will be given to a player.
 * Define custom id, custom name, custom lore and custom enchants, see customitems.txt for example.
 * The owner of a skull can be set aswell. Additional placeholders can be passed to the item.
 */
public class CustomitemCache {

    private final CustomtextCache originalCache;

    public CustomitemCache(CustomtextCache textCache) {
        originalCache = textCache;
    }

    public void giveItem(String itemKey, Player giveTo, int amount) throws GiveItemException {
        giveItem(itemKey, giveTo, amount, null);
    }

    void giveItem(String itemKey, Player giveTo, int amount,
                         @Nullable Map<String, String> additionalPlaceholders) throws GiveItemException {

        List<String> itemUnparsed;
        try {
            itemUnparsed = originalCache.getChapter(itemKey);
        } catch (CachingException e) {
            throw new GiveItemException(GiveItemException.Reason.ITEM_NOT_FOUND);
        }

        ItemStack toGive;

        Material itemMaterial;
        try {
            itemMaterial = Material.valueOf(itemUnparsed.get(0));
        } catch (IllegalArgumentException e) {
            throw new GiveItemException(GiveItemException.Reason.ITEM_SYNTAX_ERROR);
        }

        toGive = new ItemStack(itemMaterial);
        toGive.setAmount(amount);

        for (int i = 1; i < itemUnparsed.size(); i++) {

            String parse = itemUnparsed.get(i);
            Pair<String, String> keyParamPair;
            try {
                keyParamPair = Utils.getKeyValuePair(parse);
            } catch (FormattingException e) {
                logInvalidKey(parse);
                continue;
            }

            MessageBuilder parameterParse = MessageBuilder.create(keyParamPair.getRight())
                    .setDirectString()
                    .addPlaceholderReplace("%PLAYER%", giveTo.getName());

            if (additionalPlaceholders != null) {
                additionalPlaceholders.entrySet()
                        .forEach(entry -> parameterParse.addPlaceholderReplace(entry.getKey(), entry.getValue()));
            }
            // get the actual parameter and replace player placeholder
            String parameter = parameterParse.returnMessage();

            switch (keyParamPair.getLeft().toLowerCase()) {
                case "itemid":
                    try {
                        short itemId = Short.parseShort(parameter);
                        toGive.setDurability(itemId);
                    } catch (NumberFormatException e) {
                        logInvalidParameter(keyParamPair.getLeft(), parameter);
                    }
                    break;
                case "name":
                    Utils.setDisplayName(toGive, ChatColor.translateAlternateColorCodes('&', parameter));
                    break;
                case "lore":
                    Utils.setItemLore(toGive, Arrays.asList(parameter.split("[|]")));
                    break;
                case "enchant":
                    StringArgumentSeparator separator = new StringArgumentSeparator(parameter);
                    for (String enchantStr : separator.getArguments()) {
                        StringArgumentSeparator separatorEnchants = new StringArgumentSeparator(enchantStr, ':');
                        if (!separatorEnchants.meetsLength(2)) {
                            logInvalidParameter(keyParamPair.getLeft(), enchantStr);
                            continue;
                        }

                        String enchantName = separatorEnchants.getArgument(0);
                        int level = separatorEnchants.getArgumentInt(1);

                        Enchantment enchant = HalfminerSystem.getInstance().getHooksHandler()
                                .getEnchantmentFromString(enchantName);
                        if (enchant == null) {
                            logInvalidParameter(keyParamPair.getLeft(), enchantName);
                            continue;
                        }
                        if (level > 0) toGive.addUnsafeEnchantment(enchant, level);
                        else logInvalidParameter(keyParamPair.getLeft(), separatorEnchants.getArgument(1));
                    }
                    break;
                case "skullowner":
                    if (!toGive.getType().equals(Material.SKULL_ITEM)) {
                        logInvalidParameter(keyParamPair.getLeft(), parameter);
                        continue;
                    }
                    SkullMeta meta = (SkullMeta) toGive.getItemMeta();
                    boolean success = meta.setOwner(parameter);
                    if (success) {
                        toGive.setItemMeta(meta);
                    } else {
                        logInvalidParameter(keyParamPair.getLeft(), parameter);
                    }
                    break;
                default:
                    logInvalidKey(keyParamPair.getLeft());
            }
        }

        Map<Integer, ItemStack> notGiven = giveTo.getInventory().addItem(toGive);
        if (notGiven.size() != 0) {
            throw new GiveItemException(notGiven);
        } else {
            MessageBuilder.create("cacheCustomitemCacheLogSuccess")
                    .addPlaceholderReplace("%PLAYER%", giveTo.getName())
                    .addPlaceholderReplace("%AMOUNT%", String.valueOf(amount))
                    .addPlaceholderReplace("%ITEMNAME%", itemKey)
                    .logMessage(Level.INFO);
        }
    }

    public Set<String> getAllItems() {
        try {
            return originalCache.getAllChapters();
        } catch (CachingException e) {
            return new HashSet<>();
        }
    }

    private void logInvalidKey(String key) {
        MessageBuilder.create("cacheCustomitemCacheInvalidKey")
                .addPlaceholderReplace("%KEY%", key)
                .logMessage(Level.WARNING);
    }

    private void logInvalidParameter(String key, String param) {
        MessageBuilder.create("cacheCustomitemCacheInvalidParameter")
                .addPlaceholderReplace("%KEY%", key)
                .addPlaceholderReplace("%PARAMETER%", param)
                .logMessage(Level.WARNING);
    }
}