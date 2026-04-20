package com.chronicle.config;

import com.chronicle.ChroniclePlugin;
import org.bukkit.configuration.file.FileConfiguration;


public final class ChronicleConfig {


    public enum DatabaseMode { SELF_HOSTED, EXTERNAL }

 
    private final int  bufferMaxTicks;
    private final int  captureRateTicks;
    private final long idlePruneSeconds;

 
    private final DatabaseMode dbMode;
    private final int          dbPoolSize;
    private final long         dbMaxLifetime;


    private final int    selfHostedPort;
    private final String selfHostedDataDir;
    private final String selfHostedDbName;
    private final String selfHostedRootPassword;
    private final String selfHostedUsername;
    private final String selfHostedPassword;

  
    private final String  externalHost;
    private final int     externalPort;
    private final String  externalDbName;
    private final String  externalUsername;
    private final String  externalPassword;
    private final boolean externalUseSsl;
    private final boolean externalVerifyCert;


    private final String ghostPrefix;
    private final int    autoExitSeconds;
    private final double defaultReplaySpeed;
    private final int    sceneRadius;

    private final String snapshotDirectory;
    private final String compression;
    private final int    maxSnapshots;

    private final String msgPrefix;
    private final String msgReportFiled;
    private final String msgReportNotify;
    private final String msgBufferFlushed;
    private final String msgReplayStart;
    private final String msgReplayEnd;
    private final String msgCaptureStart;
    private final String msgCaptureDone;


    public ChronicleConfig(ChroniclePlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();


        bufferMaxTicks   = cfg.getInt("buffer.max-ticks",          300);
        captureRateTicks = cfg.getInt("buffer.capture-rate-ticks", 1);
        idlePruneSeconds = cfg.getLong("buffer.idle-prune-seconds", 120L);


        String modeStr = cfg.getString("database.mode", "SELF_HOSTED").toUpperCase().trim();
        DatabaseMode parsedMode;
        try {
            parsedMode = DatabaseMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Unknown database.mode '" + modeStr + "' — falling back to SELF_HOSTED.");
            parsedMode = DatabaseMode.SELF_HOSTED;
        }
        dbMode       = parsedMode;
        dbPoolSize   = cfg.getInt("database.pool-size",    10);
        dbMaxLifetime = cfg.getLong("database.max-lifetime", 1_800_000L);


        selfHostedPort         = cfg.getInt(   "database.self-hosted.port",          13306);
        selfHostedDataDir      = cfg.getString("database.self-hosted.data-dir",      "plugins/Chronicle/db-data");
        selfHostedDbName       = cfg.getString("database.self-hosted.database-name", "chronicle");
        selfHostedRootPassword = cfg.getString("database.self-hosted.root-password", "chronicle_root_secret");
        selfHostedUsername     = cfg.getString("database.self-hosted.username",      "chronicle_user");
        selfHostedPassword     = cfg.getString("database.self-hosted.password",      "chronicle_pass");

        externalHost       = cfg.getString( "database.external.host",             "localhost");
        externalPort       = cfg.getInt(    "database.external.port",             3306);
        externalDbName     = cfg.getString( "database.external.database-name",    "chronicle");
        externalUsername   = cfg.getString( "database.external.username",         "chronicle_user");
        externalPassword   = cfg.getString( "database.external.password",         "changeme");
        externalUseSsl     = cfg.getBoolean("database.external.use-ssl",          false);
        externalVerifyCert = cfg.getBoolean("database.external.verify-certificate", false);


        ghostPrefix        = cfg.getString("replay.ghost-prefix",      "&8[&6GHOST&8] ");
        autoExitSeconds    = cfg.getInt(   "replay.auto-exit-seconds", 300);
        defaultReplaySpeed = cfg.getDouble("replay.default-speed",     1.0);
        sceneRadius        = Math.min(7, cfg.getInt("replay.scene-radius", 7));


        snapshotDirectory = cfg.getString("snapshot.directory",     "snapshots");
        compression       = cfg.getString("snapshot.compression",   "ZSTD");
        maxSnapshots      = cfg.getInt(   "snapshot.max-snapshots", 10);


        msgPrefix        = cfg.getString("messages.prefix",         "&8[&6Chronicle&8] ");
        msgReportFiled   = cfg.getString("messages.report-filed",   "&aReport filed! ID: &e#{id}&a.");
        msgReportNotify  = cfg.getString("messages.report-notify",  "&c{reporter} &7reported &c{accused}&7.");
        msgBufferFlushed = cfg.getString("messages.buffer-flushed", "&7Buffer flushed (&e{ticks} &7ticks).");
        msgReplayStart   = cfg.getString("messages.replay-start",   "&aStarting replay &e#{id}&a.");
        msgReplayEnd     = cfg.getString("messages.replay-end",     "&7Replay ended.");
        msgCaptureStart  = cfg.getString("messages.capture-start",  "&eStarting global snapshot...");
        msgCaptureDone   = cfg.getString("messages.capture-done",   "&aSnapshot &e#{id} &acomplete.");

        plugin.getLogger().info("Configuration loaded — database mode: " + dbMode);
    }

    public int          getBufferMaxTicks()    { return bufferMaxTicks; }
    public int          getCaptureRateTicks()  { return captureRateTicks; }
    public long         getIdlePruneSeconds()  { return idlePruneSeconds; }


    public DatabaseMode getDbMode()            { return dbMode; }
    public boolean      isSelfHosted()         { return dbMode == DatabaseMode.SELF_HOSTED; }
    public int          getDbPoolSize()        { return dbPoolSize; }
    public long         getDbMaxLifetime()     { return dbMaxLifetime; }


    public int    getSelfHostedPort()         { return selfHostedPort; }
    public String getSelfHostedDataDir()      { return selfHostedDataDir; }
    public String getSelfHostedDbName()       { return selfHostedDbName; }
    public String getSelfHostedRootPassword() { return selfHostedRootPassword; }
    public String getSelfHostedUsername()     { return selfHostedUsername; }
    public String getSelfHostedPassword()     { return selfHostedPassword; }


    public String  getExternalHost()          { return externalHost; }
    public int     getExternalPort()          { return externalPort; }
    public String  getExternalDbName()        { return externalDbName; }
    public String  getExternalUsername()      { return externalUsername; }
    public String  getExternalPassword()      { return externalPassword; }
    public boolean isExternalUseSsl()         { return externalUseSsl; }
    public boolean isExternalVerifyCert()     { return externalVerifyCert; }


    public String getGhostPrefix()            { return ghostPrefix; }
    public int    getAutoExitSeconds()        { return autoExitSeconds; }
    public double getDefaultReplaySpeed()     { return defaultReplaySpeed; }
    public int    getSceneRadius()            { return sceneRadius; }

    public String getSnapshotDirectory()      { return snapshotDirectory; }
    public String getCompression()            { return compression; }
    public int    getMaxSnapshots()           { return maxSnapshots; }


    public String getMsgPrefix()              { return msgPrefix; }
    public String getMsgReportFiled()         { return msgReportFiled; }
    public String getMsgReportNotify()        { return msgReportNotify; }
    public String getMsgBufferFlushed()       { return msgBufferFlushed; }
    public String getMsgReplayStart()         { return msgReplayStart; }
    public String getMsgReplayEnd()           { return msgReplayEnd; }
    public String getMsgCaptureStart()        { return msgCaptureStart; }
    public String getMsgCaptureDone()         { return msgCaptureDone; }
}
