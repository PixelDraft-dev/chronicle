package com.chronicle.database;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.TemporalDataFile;
import com.chronicle.config.ChronicleConfig;
import com.chronicle.config.ChronicleConfig.DatabaseMode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;


public final class DatabaseManager {

    private static final Logger LOG = Logger.getLogger("Chronicle.DB");

    private final ChroniclePlugin plugin;


    private DB embeddedDb;

    private HikariDataSource dataSource;

    public DatabaseManager(ChroniclePlugin plugin) {
        this.plugin = plugin;
    }



    public void connect() throws Exception {
        ChronicleConfig cfg = plugin.getChronicleConfig();

        if (cfg.getDbMode() == DatabaseMode.SELF_HOSTED) {
            connectSelfHosted(cfg);
        } else {
            connectExternal(cfg);
        }

        applySchema();
        LOG.info("Chronicle database ready (" + cfg.getDbMode() + ").");
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("HikariCP connection pool closed.");
        }
        if (embeddedDb != null) {
            try {
                embeddedDb.stop();
                LOG.info("Embedded MariaDB4j instance stopped.");
            } catch (ManagedProcessException e) {
                LOG.warning("Error stopping embedded MariaDB: " + e.getMessage());
            }
        }
    }



    private void connectSelfHosted(ChronicleConfig cfg) throws Exception {
        LOG.info("Database mode: SELF_HOSTED — starting embedded MariaDB4j on port "
                + cfg.getSelfHostedPort() + "...");


        java.io.File dataDir = new java.io.File(cfg.getSelfHostedDataDir());
        if (!dataDir.isAbsolute()) {

            dataDir = new java.io.File(System.getProperty("user.dir"), cfg.getSelfHostedDataDir());
        }
        dataDir.mkdirs();

        DBConfigurationBuilder builder = DBConfigurationBuilder.newBuilder();
        builder.setPort(cfg.getSelfHostedPort());
        builder.setDataDir(dataDir.getAbsolutePath());

        builder.addArg("--bind-address=127.0.0.1");
        builder.addArg("--character-set-server=utf8mb4");
        builder.addArg("--collation-server=utf8mb4_unicode_ci");
        builder.addArg("--max_allowed_packet=64M");
        DBConfiguration dbConfig = builder.build();


        embeddedDb = DB.newEmbeddedDB(dbConfig);
        embeddedDb.start();

        LOG.info("Embedded MariaDB4j started at 127.0.0.1:" + cfg.getSelfHostedPort());
        LOG.info("phpMyAdmin → host=127.0.0.1, port=" + cfg.getSelfHostedPort()
                + ", user=root, pass=" + cfg.getSelfHostedRootPassword());

        provisionSelfHostedSchema(cfg);


        String jdbcUrl = "jdbc:mariadb://127.0.0.1:" + cfg.getSelfHostedPort()
                + "/" + cfg.getSelfHostedDbName()
                + "?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";

        openPool(jdbcUrl, cfg.getSelfHostedUsername(), cfg.getSelfHostedPassword(), cfg);
    }


    private void provisionSelfHostedSchema(ChronicleConfig cfg) throws Exception {
        String rootUrl = "jdbc:mariadb://127.0.0.1:" + cfg.getSelfHostedPort()
                + "/mysql?autoReconnect=true";

        try (Connection rootConn = DriverManager.getConnection(
                rootUrl, "root", cfg.getSelfHostedRootPassword());
             Statement stmt = rootConn.createStatement()) {

            String dbName = cfg.getSelfHostedDbName();
            String user   = cfg.getSelfHostedUsername();
            String pass   = cfg.getSelfHostedPassword();


            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");


            stmt.execute("CREATE USER IF NOT EXISTS '" + user
                    + "'@'localhost' IDENTIFIED BY '" + pass + "'");
            stmt.execute("CREATE USER IF NOT EXISTS '" + user
                    + "'@'127.0.0.1' IDENTIFIED BY '" + pass + "'");


            stmt.execute("GRANT ALL PRIVILEGES ON `" + dbName
                    + "`.* TO '" + user + "'@'localhost'");
            stmt.execute("GRANT ALL PRIVILEGES ON `" + dbName
                    + "`.* TO '" + user + "'@'127.0.0.1'");
            stmt.execute("FLUSH PRIVILEGES");

            LOG.info("Self-hosted: database '" + dbName + "' and user '"
                    + user + "' provisioned.");
        }
    }



    private void connectExternal(ChronicleConfig cfg) throws Exception {
        LOG.info("Database mode: EXTERNAL — connecting to "
                + cfg.getExternalHost() + ":" + cfg.getExternalPort()
                + "/" + cfg.getExternalDbName());

        StringBuilder urlBuilder = new StringBuilder("jdbc:mariadb://");
        urlBuilder.append(cfg.getExternalHost())
                  .append(":").append(cfg.getExternalPort())
                  .append("/").append(cfg.getExternalDbName())
                  .append("?autoReconnect=true")
                  .append("&useUnicode=true&characterEncoding=UTF-8");

        if (cfg.isExternalUseSsl()) {
            urlBuilder.append("&useSSL=true");
            if (cfg.isExternalVerifyCert()) {
                urlBuilder.append("&requireSSL=true");
            } else {
                urlBuilder.append("&trustServerCertificate=true");
            }
        } else {
            urlBuilder.append("&useSSL=false");
        }

        openPool(urlBuilder.toString(),
                cfg.getExternalUsername(), cfg.getExternalPassword(), cfg);
    }



    private void openPool(String jdbcUrl, String user, String password,
                           ChronicleConfig cfg) {

        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(user);
        hikari.setPassword(password);

        hikari.setMaximumPoolSize(cfg.getDbPoolSize());
        hikari.setMaxLifetime(cfg.getDbMaxLifetime());
        hikari.setConnectionTimeout(30_000L);
        hikari.setIdleTimeout(600_000L);
        hikari.setPoolName("Chronicle-HikariPool");

        hikari.addDataSourceProperty("cachePrepStmts",        "true");
        hikari.addDataSourceProperty("prepStmtCacheSize",     "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts",    "true");

        dataSource = new HikariDataSource(hikari);
        LOG.info("HikariCP pool open (size=" + cfg.getDbPoolSize() + ").");
    }



    private void applySchema() throws Exception {
        InputStream in = plugin.getResource("schema.sql");
        if (in == null) throw new IllegalStateException("schema.sql missing from plugin jar.");

        String schemaSql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
 
        String[] statements = schemaSql.split(";");

        try (Connection conn = dataSource.getConnection();
             Statement stmt  = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }
        }
        LOG.info("Chronicle schema applied (CREATE TABLE IF NOT EXISTS — idempotent).");
    }


    public long saveReport(ReportRecord report) throws SQLException {
        String sql = """
                INSERT INTO chronicle_reports
                    (accused_uuid, accused_name, reporter_uuid, reporter_name,
                     reason, world, pos_x, pos_y, pos_z, tdf_blob)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, report.getAccusedUuid().toString());
            ps.setString(2, report.getAccusedName());
            ps.setString(3, report.getReporterUuid().toString());
            ps.setString(4, report.getReporterName());
            ps.setString(5, report.getReason());
            ps.setString(6, report.getWorld());
            ps.setDouble(7, report.getPosX());
            ps.setDouble(8, report.getPosY());
            ps.setDouble(9, report.getPosZ());

            byte[] tdf = report.getTdfBlob();
            if (tdf != null && tdf.length > 0) ps.setBytes(10, tdf);
            else ps.setNull(10, Types.BLOB);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No generated key for report insert.");
    }


    public Optional<ReportRecord> fetchReport(long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM chronicle_reports WHERE id = ?")) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildReport(rs));
            }
        } catch (Exception e) {
            LOG.severe("fetchReport(" + id + "): " + e.getMessage());
        }
        return Optional.empty();
    }


    public List<ReportRecord> fetchOpenReports(int limit) {
        String sql = """
                SELECT id, accused_uuid, accused_name, reporter_uuid, reporter_name,
                       reason, world, pos_x, pos_y, pos_z, status, created_at
                FROM chronicle_reports
                WHERE status = 'OPEN'
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<ReportRecord> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(buildReportLight(rs));
            }
        } catch (Exception e) {
            LOG.severe("fetchOpenReports: " + e.getMessage());
        }
        return out;
    }

    public void updateReportStatus(long id, String status, UUID reviewedBy) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE chronicle_reports
                     SET status = ?, reviewed_by = ?, reviewed_at = NOW()
                     WHERE id = ?
                     """)) {

            ps.setString(1, status);
            ps.setString(2, reviewedBy != null ? reviewedBy.toString() : null);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }


    public long saveSnapshot(UUID triggeredBy, String label,
                              int entityCount, int blockCount,
                              byte[] blob) throws SQLException {

        String sql = """
                INSERT INTO chronicle_snapshots
                    (triggered_by, label, entity_count, block_count, data_blob)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, triggeredBy.toString());
            ps.setString(2, label);
            ps.setInt(3, entityCount);
            ps.setInt(4, blockCount);
            ps.setBytes(5, blob);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No generated key for snapshot insert.");
    }


    public Optional<byte[]> fetchSnapshotBlob(long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data_blob FROM chronicle_snapshots WHERE id = ?")) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.ofNullable(rs.getBytes("data_blob"));
            }
        } catch (Exception e) {
            LOG.severe("fetchSnapshotBlob(" + id + "): " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<Map<String, Object>> listSnapshots(int limit) {
        String sql = """
                SELECT id, triggered_by, label, entity_count, block_count, created_at
                FROM chronicle_snapshots
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    out.add(row);
                }
            }
        } catch (Exception e) {
            LOG.severe("listSnapshots: " + e.getMessage());
        }
        return out;
    }


    private ReportRecord buildReport(ResultSet rs) throws Exception {
        ReportRecord rec = buildReportLight(rs);

        byte[] tdfBlob = rs.getBytes("tdf_blob");
        if (tdfBlob != null && tdfBlob.length > 0) {
            rec.setTdfBlob(tdfBlob);
            try {
                TemporalDataFile tdf = TemporalDataFile.deserialize(tdfBlob);
                rec.setTdfPackets(tdf.getPackets());
            } catch (Exception e) {
                LOG.warning("Could not deserialize TDF for report #"
                        + rec.getId() + ": " + e.getMessage());
            }
        }
        return rec;
    }


    private ReportRecord buildReportLight(ResultSet rs) throws SQLException {
        ReportRecord rec = new ReportRecord();
        rec.setId(rs.getLong("id"));
        rec.setAccusedUuid(UUID.fromString(rs.getString("accused_uuid")));
        rec.setAccusedName(rs.getString("accused_name"));
        rec.setReporterUuid(UUID.fromString(rs.getString("reporter_uuid")));
        rec.setReporterName(rs.getString("reporter_name"));
        rec.setReason(rs.getString("reason"));
        rec.setWorld(rs.getString("world"));
        rec.setPosX(rs.getDouble("pos_x"));
        rec.setPosY(rs.getDouble("pos_y"));
        rec.setPosZ(rs.getDouble("pos_z"));
        rec.setStatus(rs.getString("status"));
        rec.setCreatedAt(rs.getTimestamp("created_at"));
        return rec;
    }


    public String connectionSummary() {
        if (dataSource == null) return "Not connected.";
        ChronicleConfig cfg = plugin.getChronicleConfig();
        return switch (cfg.getDbMode()) {
            case SELF_HOSTED -> "SELF_HOSTED  │  127.0.0.1:" + cfg.getSelfHostedPort()
                    + "  │  db=" + cfg.getSelfHostedDbName()
                    + "  │  phpMyAdmin → root / " + cfg.getSelfHostedRootPassword();
            case EXTERNAL    -> "EXTERNAL  │  " + cfg.getExternalHost()
                    + ":" + cfg.getExternalPort()
                    + "  │  db=" + cfg.getExternalDbName();
        };
    }
}
