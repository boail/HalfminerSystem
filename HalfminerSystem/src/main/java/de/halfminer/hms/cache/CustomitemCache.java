package de.halfminer.hms.cache;

import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.cache.exceptions.CachingException;
import de.halfminer.hms.cache.exceptions.ItemCacheException;
import de.halfminer.hms.handler.storage.PlayerNotFoundException;
import de.halfminer.hms.util.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

/**
 * Parses a CustomtextCache to create a custom {@link ItemStack} that will be given to a player.
 * Define custom id, custom name, custom lore and custom enchants.
 * The owner of a skull can be set aswell. Additional placeholders can be passed to the item.
 */
public class CustomitemCache {

    private final CustomtextCache originalCache;


    public CustomitemCache(CustomtextCache textCache) {
        originalCache = textCache;
    }

    public ItemStack getItem(String itemKey, Player owner, int amount,
                             @Nullable Map<String, String> additionalPlaceholders) throws ItemCacheException {

        List<String> itemUnparsed;
        try {
            itemUnparsed = originalCache.getChapter(itemKey);
        } catch (CachingException e) {
            throw new ItemCacheException(ItemCacheException.Reason.ITEM_NOT_FOUND);
        }

        ItemStack itemStack;

        Material itemMaterial;
        try {
            itemMaterial = Material.matchMaterial(itemUnparsed.get(0));
            if (itemMaterial == null) {
                throw new ItemCacheException(ItemCacheException.Reason.ITEM_SYNTAX_ERROR);
            }
        } catch (IllegalArgumentException e) {
            throw new ItemCacheException(ItemCacheException.Reason.ITEM_SYNTAX_ERROR);
        }

        itemStack = new ItemStack(itemMaterial);
        itemStack.setAmount(amount);

        for (int i = 1; i < itemUnparsed.size(); i++) {

            String parse = itemUnparsed.get(i);
            Pair<String, String> keyParamPair;
            try {
                keyParamPair = Utils.getKeyValuePair(parse);
            } catch (FormattingException e) {
                logInvalidKey(parse);
                continue;
            }

            Message parameterParse = Message.create(keyParamPair.getRight())
                    .setDirectString()
                    .addPlaceholder("%PLAYER%", owner.getName());

            if (additionalPlaceholders != null) {
                additionalPlaceholders.forEach(parameterParse::addPlaceholder);
            }
            // get the actual parameter and replace player placeholder
            String parameter = parameterParse.returnMessage();

            switch (keyParamPair.getLeft().toLowerCase()) {

                case "name":
                    Utils.setDisplayName(itemStack, ChatColor.translateAlternateColorCodes('&', parameter));
                    break;
                case "lore":
                    Utils.setItemLore(itemStack, Arrays.asList(parameter.split("[|]")));
                    break;
                case "damage":
                    try {
                        int damage = Integer.parseInt(parameter);
                        ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta instanceof Damageable) {
                            ((Damageable) itemMeta).setDamage(damage);
                            itemStack.setItemMeta(itemMeta);
                        } else {
                            logInvalidParameter(keyParamPair.getLeft(), parameter);
                        }
                    } catch (NumberFormatException e) {
                        logInvalidParameter(keyParamPair.getLeft(), parameter);
                    }
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
                        if (level > 0) itemStack.addUnsafeEnchantment(enchant, level);
                        else logInvalidParameter(keyParamPair.getLeft(), separatorEnchants.getArgument(1));
                    }
                    break;
                case "skullowner":

                    if (!itemStack.getType().equals(Material.PLAYER_HEAD)) {
                        logInvalidParameter(keyParamPair.getLeft(), parameter);
                        continue;
                    }

                    SkullMeta meta = (SkullMeta) itemStack.getItemMeta();
                    try {
                        OfflinePlayer offlinePlayer = HalfminerSystem.getInstance()
                                .getStorageHandler()
                                .getPlayer(parameter)
                                .getBase();

                        boolean success = meta.setOwningPlayer(offlinePlayer);
                        if (success) {
                            itemStack.setItemMeta(meta);
                            continue;
                        }

                    } catch (PlayerNotFoundException ignored) {}

                    logInvalidParameter(keyParamPair.getLeft(), parameter);
                    break;
                default:
                    logInvalidKey(keyParamPair.getLeft());
            }
        }

        return itemStack;
    }

    public void giveItem(String itemKey, Player giveTo, int amount) throws ItemCacheException {
        giveItem(itemKey, giveTo, amount, null);
    }

    void giveItem(String itemKey, Player giveTo, int amount,
                  @Nullable Map<String, String> additionalPlaceholders) throws ItemCacheException {

        ItemStack toGive = getItem(itemKey, giveTo, amount, additionalPlaceholders);
        Map<Integer, ItemStack> notGiven = giveTo.getInventory().addItem(toGive);
        if (notGiven.size() != 0) {
            throw new ItemCacheException(notGiven);
        } else {
            Message.create("cacheCustomitemCacheLogSuccess")
                    .addPlaceholder("%PLAYER%", giveTo.getName())
                    .addPlaceholder("%AMOUNT%", amount)
                    .addPlaceholder("%ITEMNAME%", itemKey)
                    .log(Level.INFO);
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
        Message.create("cacheCustomitemCacheInvalidKey")
                .addPlaceholder("%KEY%", key)
                .log(Level.WARNING);
    }

    private void logInvalidParameter(String key, String param) {
        Message.create("cacheCustomitemCacheInvalidParameter")
                .addPlaceholder("%KEY%", key)
                .addPlaceholder("%PARAMETER%", param)
                .log(Level.WARNING);
    }
}
