package com.chronicle;

import com.chronicle.buffer.BufferManager;
import com.chronicle.commands.ChronicleCommand;
import com.chronicle.commands.ReportCommand;
import com.chronicle.config.ChronicleConfig;
import com.chronicle.database.DatabaseManager;
import com.chronicle.listeners.PacketCaptureListener;
import com.chronicle.listeners.PlayerActionListener;
import com.chronicle.replay.ReplayEngine;
import com.chronicle.util.InspectManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChroniclePlugin extends JavaPlugin {

    private ChronicleConfig      chronicleConfig;
    private DatabaseManager      databaseManager;
    private BufferManager        bufferManager;
    private ReplayEngine         replayEngine;
    private InspectManager       inspectManager;
    private ProtocolManager      protocolManager;
    private PacketCaptureListener packetCaptureListener;



    @Override
    public void onEnable() {
        printBanner();


        this.chronicleConfig = new ChronicleConfig(this);


        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
            getLogger().info("DB ► " + databaseManager.connectionSummary());
        } catch (Exception e) {
            getLogger().severe("════════════════════════════════════════");
            getLogger().severe("  DATABASE CONNECTION FAILED");
            getLogger().severe("  Mode   : " + chronicleConfig.getDbMode());
            if (chronicleConfig.isSelfHosted()) {
                getLogger().severe("  Port   : " + chronicleConfig.getSelfHostedPort());
                getLogger().severe("  DataDir: " + chronicleConfig.getSelfHostedDataDir());
                getLogger().severe("  Ensure the port is free and the data-dir is writable.");
            } else {
                getLogger().severe("  Host   : " + chronicleConfig.getExternalHost()
                        + ":" + chronicleConfig.getExternalPort());
                getLogger().severe("  DB     : " + chronicleConfig.getExternalDbName());
                getLogger().severe("  Check host, credentials, and that the DB exists.");
            }
            getLogger().severe("  Error  : " + e.getMessage());
            getLogger().severe("  Chronicle will disable itself now.");
            getLogger().severe("════════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }


        this.bufferManager = new BufferManager(this);
        bufferManager.startPruneTask();

        this.replayEngine = new ReplayEngine(this);


        this.inspectManager = new InspectManager(this);


        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.packetCaptureListener = new PacketCaptureListener(this, protocolManager);
        packetCaptureListener.register();
        getLogger().info("ProtocolLib packet capture listeners registered.");


        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerActionListener(this), this);
        pm.registerEvents(inspectManager, this);
        getLogger().info("Bukkit event listeners registered.");


        var chronicleCmd = new ChronicleCommand(this);
        var reportCmd    = new ReportCommand(this);

        requireCommand("ch").setExecutor(chronicleCmd);
        requireCommand("ch").setTabCompleter(chronicleCmd);
        requireCommand("report").setExecutor(reportCmd);
        requireCommand("report").setTabCompleter(reportCmd);
        getLogger().info("Commands registered.");

        for (var player : getServer().getOnlinePlayers()) {
            bufferManager.onPlayerJoin(player);
        }

        getLogger().info("Chronicle v" + getDescription().getVersion() + " enabled successfully.");
        getLogger().info("Active buffers seeded for "
                + getServer().getOnlinePlayers().size() + " online player(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("Chronicle is shutting down...");

        if (replayEngine != null) {
            replayEngine.shutdown();
        }

        if (packetCaptureListener != null) {
            packetCaptureListener.unregister();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        if (bufferManager != null) {
            bufferManager.shutdown();
        }

        getLogger().info("Chronicle disabled cleanly.");
    }
    public void reloadChronicleConfig() {
        this.chronicleConfig = new ChronicleConfig(this);
        getLogger().info("Configuration hot-reloaded.");
    }



    public ChronicleConfig   getChronicleConfig()  { return chronicleConfig; }
    public DatabaseManager   getDatabaseManager()  { return databaseManager; }
    public BufferManager     getBufferManager()    { return bufferManager; }
    public ReplayEngine      getReplayEngine()     { return replayEngine; }
    public InspectManager    getInspectManager()   { return inspectManager; }
    public ProtocolManager   getProtocolManager()  { return protocolManager; }

    private org.bukkit.command.PluginCommand requireCommand(String name) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            throw new IllegalStateException("Command '" + name + "' not registered in plugin.yml");
        }
        return cmd;
    }

    private void printBanner() {
        String[] lines = {
            "",
            "  ╔═══════════════════════════════════════╗",
            "  ║       C H R O N I C L E  v1.0          ║",
            "  ║  Temporal Oversight & Action Capture   ║",
            "  ╚═══════════════════════════════════════╝",
            ""
        };
        for (String line : lines) getLogger().info(line);
    }
}
