package de.halfminer.hmc;

import de.halfminer.hmc.cmd.abs.HalfminerCommand;
import de.halfminer.hmc.enums.ModuleType;
import de.halfminer.hmc.modules.HalfminerModule;
import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.handlers.HanStorage;
import de.halfminer.hms.util.MessageBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * HalfminerCore main class, core Bukkit/Spigot plugin containing most server side functionality for Halfminer
 *
 * @author Fabian Prieto Wunderlich - Kakifrucht
 */
public class HalfminerCore extends JavaPlugin {

    private final static String PACKAGE_PATH = "de.halfminer.hmc";
    private static HalfminerCore instance;

    static HalfminerCore getInstance() {
        return instance;
    }

    private HanStorage storage;
    private final Map<ModuleType, HalfminerModule> modules = new HashMap<>();

    @Override
    public void onEnable() {

        instance = this;
        HalfminerSystem.getInstance().getHalfminerManager().reloadOcurred(this);

        storage = new HanStorage(this);
        try {
            for (ModuleType module : ModuleType.values()) {
                HalfminerModule mod = (HalfminerModule) this.getClassLoader()
                        .loadClass(PACKAGE_PATH + ".modules." + module.getClassName()).newInstance();

                modules.put(module, mod);
            }
        } catch (Exception e) {
            getLogger().severe("An error has occurred, see stacktrace for information");
            e.printStackTrace();
            setEnabled(false);
            return;
        }

        getLogger().info("HalfminerCore enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equals("?")) return true;

        HalfminerCommand command;
        try {
            command = (HalfminerCommand) this.getClassLoader()
                    .loadClass(PACKAGE_PATH + ".cmd.Cmd" + cmd.getName()).newInstance();
        } catch (Exception e) {
            getLogger().severe("An error has occured executing " + cmd.getName() + ":");
            e.printStackTrace();
            return true;
        }

        if (command.hasPermission(sender)) {
            command.run(sender, label, args);
        } else MessageBuilder.create("noPermission", "Info").sendMessage(sender);

        return true;
    }

    public HanStorage getStorage() {
        return storage;
    }

    public HalfminerModule getModule(ModuleType type) {
        if (modules.size() != ModuleType.values().length)
            throw new RuntimeException("Illegal call to getModule before all modules were initialized");
        return modules.get(type);
    }
}