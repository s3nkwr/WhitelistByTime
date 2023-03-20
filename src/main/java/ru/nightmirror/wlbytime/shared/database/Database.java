package ru.nightmirror.wlbytime.shared.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import ru.nightmirror.wlbytime.interfaces.database.IDatabase;
import ru.nightmirror.wlbytime.misc.convertors.TimeConvertor;
import ru.nightmirror.wlbytime.shared.WhitelistByTime;
import ru.nightmirror.wlbytime.shared.api.events.PlayerAddedToWhitelistEvent;
import ru.nightmirror.wlbytime.shared.common.Checker;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class Database implements IDatabase {
    private HikariConfig config;
    private HikariDataSource source;
    private String DBTable;
    private final WhitelistByTime plugin;
    private final static Logger LOG = Logger.getLogger("WhitelistByTime");

    public Database(WhitelistByTime plugin) {
        this.plugin = plugin;
        enable();
    }
    private void enable() {
        DBTable = getConfigString("table", "whitelist");

        config = new HikariConfig();
        config.setJdbcUrl(getStringSource());

        if (getConfigBoolean("use-user-and-password", false)) {
            config.setUsername(getConfigString("user"));
            config.setPassword(getConfigString("password"));
        }

        createTable();
    }

    @Override
    public void reload() {
        LOG.info("Reloading database...");
        enable();
    }

    private void createTable() {
        final String query = "CREATE TABLE IF NOT EXISTS " + DBTable + " (\n"
                + " `nickname` TEXT,\n"
                + " `until` BIGINT\n"
                + ");";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute(query);
        } catch (Exception exception) {
            LOG.severe("Can't create table: " + exception.getMessage());
        }
    }

    private String getStringSource() {
        final String type = getConfigString("type", "sqlite");
        if (type.equalsIgnoreCase("sqlite") || type.equalsIgnoreCase("h2"))
            return "jdbc:" + type + ":" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath();

        return "jdbc:" + type + "://" + getConfigString("address") + "/" + getConfigString("name");
    }

    @Nullable
    private Connection getConnection() {
        try {
            if (source == null || source.isClosed()) {
                source = new HikariDataSource(config);
            }
            return source.getConnection();
        } catch (Exception exception) {
            LOG.severe("Can't create connection: " + exception.getMessage());
        }
        return null;
    }

    @Override
    public void addPlayer(String nickname, long until) {
        if (!getConfigBoolean("case-sensitive", true)) {
            nickname = nickname.toLowerCase(Locale.ROOT);
        }

        PlayerAddedToWhitelistEvent event = new PlayerAddedToWhitelistEvent(nickname, until);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        final String query = "INSERT INTO " + DBTable + " (`nickname`, `until`)" +
                "VALUES ('"+nickname+"', '"+until+"');";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(query);

            if (until == -1L) {
                LOG.info("Player "+nickname+" added to whitelist forever");
            } else {
                LOG.info("Player "+nickname+" added to whitelist for "+TimeConvertor.getTimeLine(plugin, until-System.currentTimeMillis(), false));
            }
        } catch (Exception exception) {
            LOG.warning("Can't add player: " + exception.getMessage());
        }
    }

    @Override
    public Boolean checkPlayer(String nickname) {
        if (!getConfigBoolean("case-sensitive", true)) {
            nickname = nickname.toLowerCase(Locale.ROOT);
        }
        long until = getUntil(nickname);
        return checkPlayer(until);
    }

    public Boolean checkPlayer(long until) {
        return until == -1L || until > System.currentTimeMillis();
    }

    @Override
    public long getUntil(String nickname) {
        final String query = "SELECT * FROM " + DBTable + " WHERE nickname = '"+nickname+"';";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)
        ) {
            if (resultSet.next()) {
                return resultSet.getLong("until");
            } else {
                return 0;
            }
        } catch (Exception exception) {
            LOG.warning("Can't get until: " + exception.getMessage());
        }
        return -1L;
    }

    @Override
    public void setUntil(String nickname, long until) {
        final String query = "UPDATE " + DBTable + " SET until = '"+until+"' WHERE nickname = '"+nickname+"';";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
                ) {
            statement.executeUpdate(query);

            if (until == -1L) {
                LOG.info("Set time for "+nickname+" forever");
            } else {
                LOG.info("Set time for "+nickname+" "+TimeConvertor.getTimeLine(plugin, until-System.currentTimeMillis(), false));
            }
        } catch (Exception exception) {
            LOG.warning("Can't set time for player: " + nickname);
        }
    }

    @Override
    public void removePlayer(String nickname) {

        final String query = "DELETE FROM " + DBTable + " WHERE nickname = '"+nickname+"';";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(query);

            LOG.info("Player "+nickname+" removed");
            synchronized (Checker.toKick) {
                Checker.toKick.add(nickname);
            }
        } catch (Exception exception) {
            LOG.warning("Can't remove player: " + exception.getMessage());
        }
    }

    @Override
    public Map<String, Long> getAll() {
        final String query = "SELECT * FROM " + DBTable + ";";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)
        ) {
            Map<String, Long> players = new HashMap<>();

            while (resultSet.next()) {
                String nickname = resultSet.getString("nickname");
                long until = resultSet.getLong("until");
                players.put(nickname, until);
            }

            return players;
        } catch (Exception exception) {
            LOG.warning("Can't get all players: " + exception.getMessage());
        }
        return Checker.players;
    }

    private String getConfigString(String path) {
        return getConfigString(path, "null");
    }

    private String getConfigString(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    private Boolean getConfigBoolean(String path, Boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }
}
