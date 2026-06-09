package dev.maghreb.parkour.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.models.PlayerRecord;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages the HikariCP connection pool for SQLite or MySQL.
 * All public query methods are safe to call from async threads.
 */
public class DatabaseManager {

    private final ParkourPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws Exception {
        String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();

        HikariConfig config = new HikariConfig();
        config.setPoolName("ParkourPool");

        if (dbType.equals("mysql")) {
            ConfigurationSection mysql = plugin.getConfig().getConfigurationSection("database.mysql");
            String host = mysql.getString("host", "localhost");
            int port = mysql.getInt("port", 3306);
            String database = mysql.getString("database", "parkour");
            String username = mysql.getString("username", "root");
            String password = mysql.getString("password", "");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(mysql.getInt("pool-size", 10));
            config.setMaxLifetime(mysql.getLong("max-lifetime", 1_800_000L));
            config.setConnectionTimeout(mysql.getLong("connection-timeout", 30_000L));
        } else {
            // SQLite
            plugin.getDataFolder().mkdirs();
            String filePath = plugin.getDataFolder().getAbsolutePath() + "/"
                    + plugin.getConfig().getString("database.sqlite.file", "parkour.db");

            config.setJdbcUrl("jdbc:sqlite:" + filePath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1); // SQLite is single-writer
            config.setConnectionTestQuery("SELECT 1");
        }

        // Common pool settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createSchema();
        plugin.getLogger().info("Database connection pool initialized (" + dbType + ").");
    }

    private void createSchema() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS player_records (
                    uuid         VARCHAR(36)  NOT NULL,
                    player_name  VARCHAR(16)  NOT NULL,
                    course_name  VARCHAR(64)  NOT NULL,
                    best_time_ms BIGINT       NOT NULL,
                    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, course_name)
                );
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Upserts a player's best time. Only writes if the new time is better.
     * Safe to call from async thread.
     */
    public void saveRecordIfBetter(UUID uuid, String playerName, String courseName, long timeMs) {
        String sql = """
                INSERT INTO player_records (uuid, player_name, course_name, best_time_ms)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid, course_name) DO UPDATE
                    SET best_time_ms = excluded.best_time_ms,
                        player_name  = excluded.player_name,
                        updated_at   = CURRENT_TIMESTAMP
                    WHERE excluded.best_time_ms < player_records.best_time_ms;
                """;
        // MySQL variant uses INSERT ... ON DUPLICATE KEY UPDATE
        String sqlMySQL = """
                INSERT INTO player_records (uuid, player_name, course_name, best_time_ms)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    best_time_ms = IF(VALUES(best_time_ms) < best_time_ms, VALUES(best_time_ms), best_time_ms),
                    player_name  = VALUES(player_name),
                    updated_at   = CURRENT_TIMESTAMP;
                """;

        String useSql = isMySql() ? sqlMySQL : sql;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(useSql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, courseName);
            ps.setLong(4, timeMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player record", e);
        }
    }

    /**
     * Returns the player's personal best for a course, or -1 if none.
     */
    public long getPersonalBest(UUID uuid, String courseName) {
        String sql = "SELECT best_time_ms FROM player_records WHERE uuid = ? AND course_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, courseName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("best_time_ms");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve personal best", e);
        }
        return -1L;
    }

    /**
     * Returns the top N records for a course, ordered by best_time_ms ascending.
     */
    public List<PlayerRecord> getTopRecords(String courseName, int limit) {
        List<PlayerRecord> records = new ArrayList<>();
        String sql = """
                SELECT uuid, player_name, course_name, best_time_ms
                FROM player_records
                WHERE course_name = ?
                ORDER BY best_time_ms ASC
                LIMIT ?;
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new PlayerRecord(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getString("course_name"),
                            rs.getLong("best_time_ms")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve top records", e);
        }
        return records;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    private boolean isMySql() {
        return plugin.getConfig().getString("database.type", "sqlite")
                .equalsIgnoreCase("mysql");
    }
}
