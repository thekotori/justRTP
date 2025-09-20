package eu.kotori.justRTP.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.kotori.justRTP.JustRTP;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {
    public record ProxyTeleportRequest(
            UUID playerUUID, String originServer, String targetServer, String commandArgs, String targetWorld,
            Location location, String status, Integer minRadius, Integer maxRadius, String requestType
    ) {}

    private enum RequestStatus { PENDING, PROCESSING, COMPLETE, FAILED }

    private final JustRTP plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JustRTP plugin, FoliaScheduler scheduler) {
        this.plugin = plugin;
        connect();
        if(isConnected()) {
            scheduler.runAsync(this::updateTableSchema);
        }
    }

    private void connect() {
        try {
            File mysqlFile = new File(plugin.getDataFolder(), "mysql.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(mysqlFile);

            if (!config.getBoolean("enabled", false)) {
                plugin.debug("MySQL is disabled in mysql.yml.");
                return;
            }

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getString("host") + ":" + config.getInt("port") + "/" + config.getString("database") + "?useSSL=false&autoReconnect=true");
            hikariConfig.setUsername(config.getString("username"));
            hikariConfig.setPassword(config.getString("password"));
            hikariConfig.setMaximumPoolSize(config.getInt("pool-settings.maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(config.getInt("pool-settings.minimum-idle", 10));
            hikariConfig.setConnectionTimeout(config.getInt("pool-settings.connection-timeout", 30000));
            hikariConfig.setIdleTimeout(config.getInt("pool-settings.idle-timeout", 600000));
            hikariConfig.setMaxLifetime(config.getInt("pool-settings.max-lifetime", 1800000));

            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Successfully connected to MySQL database.");
        } catch (Exception e) {
            plugin.getLogger().severe("Could not connect to MySQL database! " + e.getMessage());
            dataSource = null;
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    private void updateTableSchema() {
        if (!isConnected()) return;
        plugin.debug("Checking and updating database schema...");
        String createTableSQL = "CREATE TABLE IF NOT EXISTS justrtp_teleports (" +
                "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                "origin_server VARCHAR(255) NOT NULL," +
                "target_server VARCHAR(255)," +
                "target_world VARCHAR(255)," +
                "command_args TEXT," +
                "loc_x DOUBLE, loc_y DOUBLE, loc_z DOUBLE, loc_yaw FLOAT, loc_pitch FLOAT," +
                "min_radius INT, max_radius INT," +
                "status VARCHAR(16) NOT NULL DEFAULT 'PENDING'," +
                "request_type VARCHAR(16) NOT NULL DEFAULT 'INDIVIDUAL'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
            plugin.debug("Database schema check complete.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create or update table schema!", e);
        }
    }

    public CompletableFuture<Void> createTeleportRequest(UUID playerUUID, String originServer, String targetServer, String commandArgs, String targetWorld, Optional<Integer> minRadius, Optional<Integer> maxRadius, String requestType) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) return;
            plugin.debug("Creating teleport request in DB for " + playerUUID + " to server " + targetServer);
            String sql = "REPLACE INTO justrtp_teleports (player_uuid, origin_server, target_server, command_args, target_world, min_radius, max_radius, status, request_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, originServer);
                pstmt.setString(3, targetServer);
                pstmt.setString(4, commandArgs);
                pstmt.setString(5, targetWorld);
                pstmt.setObject(6, minRadius.orElse(null));
                pstmt.setObject(7, maxRadius.orElse(null));
                pstmt.setString(8, RequestStatus.PENDING.name());
                pstmt.setString(9, requestType);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create teleport request", e);
            }
        });
    }

    public CompletableFuture<Void> updateTeleportRequestWithLocation(UUID playerUUID, Location location) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) return;
            plugin.debug("Updating teleport request for " + playerUUID + " with location " + location);
            String sql = "UPDATE justrtp_teleports SET target_world = ?, loc_x = ?, loc_y = ?, loc_z = ?, loc_yaw = ?, loc_pitch = ?, status = ? WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, location.getWorld().getName());
                pstmt.setDouble(2, location.getX());
                pstmt.setDouble(3, location.getY());
                pstmt.setDouble(4, location.getZ());
                pstmt.setFloat(5, location.getYaw());
                pstmt.setFloat(6, location.getPitch());
                pstmt.setString(7, RequestStatus.COMPLETE.name());
                pstmt.setString(8, playerUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update teleport request with location", e);
            }
        });
    }

    public CompletableFuture<Void> updateGroupTeleportRequestWithLocation(UUID leaderUUID, Location location) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) return;
            plugin.debug("Updating GROUP teleport request for leader " + leaderUUID + " with location " + location);
            getTeleportRequest(leaderUUID).thenAccept(requestOpt -> {
                requestOpt.ifPresent(leaderRequest -> {
                    List<UUID> allMemberUuids = new ArrayList<>();
                    allMemberUuids.add(leaderUUID);
                    try {
                        String[] parts = leaderRequest.commandArgs().split(":", 2);
                        if (parts.length == 2 && parts[0].equals("GROUP_TELEPORT")) {
                            for (String uuidStr : parts[1].split(",")) {
                                allMemberUuids.add(UUID.fromString(uuidStr));
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not parse group members from leader request: " + leaderUUID);
                    }

                    String sql = "UPDATE justrtp_teleports SET target_world = ?, loc_x = ?, loc_y = ?, loc_z = ?, loc_yaw = ?, loc_pitch = ?, status = ? WHERE player_uuid = ?";
                    try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        for (UUID memberUUID : allMemberUuids) {
                            pstmt.setString(1, location.getWorld().getName());
                            pstmt.setDouble(2, location.getX());
                            pstmt.setDouble(3, location.getY());
                            pstmt.setDouble(4, location.getZ());
                            pstmt.setFloat(5, location.getYaw());
                            pstmt.setFloat(6, location.getPitch());
                            pstmt.setString(7, RequestStatus.COMPLETE.name());
                            pstmt.setString(8, memberUUID.toString());
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                        plugin.debug("Updated " + allMemberUuids.size() + " group members to COMPLETE status.");
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Could not update group teleport request with location", e);
                    }
                });
            });
        });
    }


    public CompletableFuture<Void> failTeleportRequest(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) return;
            String sql = "UPDATE justrtp_teleports SET status = ? WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, RequestStatus.FAILED.name());
                pstmt.setString(2, playerUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not mark teleport request as failed", e);
            }
        });
    }

    public CompletableFuture<Optional<ProxyTeleportRequest>> findAndMarkPendingTeleportRequestForServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) return Optional.empty();
            String selectSql = "SELECT * FROM justrtp_teleports WHERE target_server = ? AND status = ? LIMIT 1 FOR UPDATE";
            String updateSql = "UPDATE justrtp_teleports SET status = ? WHERE player_uuid = ?";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql)) {
                    selectPstmt.setString(1, serverName);
                    selectPstmt.setString(2, RequestStatus.PENDING.name());
                    ResultSet rs = selectPstmt.executeQuery();

                    if (rs.next()) {
                        ProxyTeleportRequest request = mapResultSetToRequest(rs);
                        try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                            updatePstmt.setString(1, RequestStatus.PROCESSING.name());
                            updatePstmt.setString(2, request.playerUUID().toString());
                            updatePstmt.executeUpdate();
                        }
                        conn.commit();
                        return Optional.of(request);
                    } else {
                        conn.commit();
                        return Optional.empty();
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().log(Level.SEVERE, "Could not poll and mark pending teleport request for server " + serverName, e);
                    return Optional.empty();
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database transaction error during polling", e);
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<List<ProxyTeleportRequest>> findFinalizedTeleportRequestsByOrigin(String originServer) {
        return CompletableFuture.supplyAsync(() -> {
            List<ProxyTeleportRequest> requests = new ArrayList<>();
            if (!isConnected()) return requests;
            String sql = "SELECT * FROM justrtp_teleports WHERE origin_server = ? AND (status = ? OR status = ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, originServer);
                pstmt.setString(2, RequestStatus.COMPLETE.name());
                pstmt.setString(3, RequestStatus.FAILED.name());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    requests.add(mapResultSetToRequest(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not poll for finalized teleport requests for server " + originServer, e);
            }
            return requests;
        });
    }

    public CompletableFuture<Optional<ProxyTeleportRequest>> getTeleportRequest(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) return Optional.empty();
            String sql = "SELECT * FROM justrtp_teleports WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(mapResultSetToRequest(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get teleport request for " + playerUUID, e);
            }
            return Optional.empty();
        });
    }

    private ProxyTeleportRequest mapResultSetToRequest(ResultSet rs) throws SQLException {
        Integer minRadius = rs.getObject("min_radius") != null ? rs.getInt("min_radius") : null;
        Integer maxRadius = rs.getObject("max_radius") != null ? rs.getInt("max_radius") : null;
        String status = rs.getString("status");

        Location location = null;
        if (RequestStatus.valueOf(status) == RequestStatus.COMPLETE) {
            World world = plugin.getServer().getWorld(rs.getString("target_world"));
            if (world != null) {
                location = new Location(
                        world,
                        rs.getDouble("loc_x"),
                        rs.getDouble("loc_y"),
                        rs.getDouble("loc_z"),
                        rs.getFloat("loc_yaw"),
                        rs.getFloat("loc_pitch")
                );
            }
        }

        return new ProxyTeleportRequest(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("origin_server"),
                rs.getString("target_server"),
                rs.getString("command_args"),
                rs.getString("target_world"),
                location,
                status,
                minRadius,
                maxRadius,
                rs.getString("request_type")
        );
    }

    public CompletableFuture<Void> removeTeleportRequest(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) return;
            plugin.debug("Removing teleport request from DB for " + playerUUID);
            String sql = "DELETE FROM justrtp_teleports WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove teleport request", e);
            }
        });
    }

    public void close() {
        if (isConnected()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection closed.");
        }
    }
}