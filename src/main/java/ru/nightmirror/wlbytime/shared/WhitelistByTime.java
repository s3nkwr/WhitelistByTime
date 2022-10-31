package ru.nightmirror.wlbytime.shared;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.nightmirror.wlbytime.shared.api.API;
import ru.nightmirror.wlbytime.interfaces.api.IAPI;
import ru.nightmirror.wlbytime.shared.common.Checker;
import ru.nightmirror.wlbytime.misc.utils.ConfigUtils;
import ru.nightmirror.wlbytime.shared.database.Database;
import ru.nightmirror.wlbytime.interfaces.database.IDatabase;
import ru.nightmirror.wlbytime.shared.executors.CommandsExecutor;
import ru.nightmirror.wlbytime.shared.executors.minecraft.WhitelistCommandExecutor;
import ru.nightmirror.wlbytime.listeners.PlayerJoinListener;
import ru.nightmirror.wlbytime.listeners.WhitelistCmdListener;
import ru.nightmirror.wlbytime.shared.executors.minecraft.WhitelistTabCompleter;

import java.util.logging.Logger;

public class WhitelistByTime extends JavaPlugin {

    private final Logger log = Logger.getLogger("WhitelistByTime");
    private static IAPI api;

    private BukkitTask checker;

    @Override
    public void onEnable() {
        ConfigUtils.checkConfig(this);

        IDatabase database = new Database(this);

        Bukkit.getPluginManager().registerEvents(new WhitelistCmdListener(new CommandsExecutor(database, this)), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(database, this), this);

        getCommand("whitelist").setExecutor(new WhitelistCommandExecutor(new CommandsExecutor(database, this)));
        getCommand("wl").setExecutor(new WhitelistCommandExecutor(new CommandsExecutor(database, this)));
        getCommand("whitelist").setTabCompleter(new WhitelistTabCompleter(database, this));
        getCommand("wl").setTabCompleter(new WhitelistTabCompleter(database, this));

        if (getConfig().getBoolean("checker-thread", true)) {
            checker = new Checker(this, database).start(getConfig().getInt("checker-thread", 1));
        }

        api = new API(database, this);

        new Metrics(this, 13834);

        log.info(ChatColor.GREEN + "Enabled!");
    }

    @Override
    public void onDisable() {
        if (checker != null) {
            checker.cancel();
        }

        log.info(ChatColor.GOLD + "Disabled");
    }

    public static IAPI getAPI() {
        return api;
    }
}