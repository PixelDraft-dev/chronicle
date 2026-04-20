package com.chronicle.replay;

import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.ActionPacket;
import com.chronicle.database.DatabaseManager;
import com.chronicle.database.ReportRecord;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public final class ReplayEngine {

    private static final Logger LOG = Logger.getLogger("Chronicle.ReplayEngine");

    private final ChroniclePlugin plugin;
    private final DatabaseManager db;


    private final Map<UUID, ReplaySession> activeSessions = new ConcurrentHashMap<>();

    private final Map<UUID, LiveSession> liveSessions = new ConcurrentHashMap<>();

    public ReplayEngine(ChroniclePlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
    }


    public void startReplay(Player admin, long reportId) {
        if (activeSessions.containsKey(admin.getUniqueId())) {
            admin.sendMessage(Component.text(
                    "[Chronicle] You are already in a replay. Use /ch restore-view first.",
                    NamedTextColor.RED));
            return;
        }

        admin.sendMessage(Component.text(
                "[Chronicle] Loading report #" + reportId + "...", NamedTextColor.GOLD));


        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<ReportRecord> opt = db.fetchReport(reportId);
            if (opt.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        admin.sendMessage(Component.text(
                                "[Chronicle] Report #" + reportId + " not found.",
                                NamedTextColor.RED)));
                return;
            }

            ReportRecord report = opt.get();
            if (report.getTdfPackets() == null || report.getTdfPackets().isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        admin.sendMessage(Component.text(
                                "[Chronicle] Report #" + reportId
                                        + " has no TDF data (very old report or empty buffer).",
                                NamedTextColor.YELLOW)));
                return;
            }


            plugin.getServer().getScheduler().runTask(plugin, () ->
                    beginSession(admin, report));
        });
    }



    public void startLive(Player admin, Player target) {
        if (liveSessions.containsKey(admin.getUniqueId())) {
            stopLive(admin);
        }

        admin.sendMessage(Component.text(
                "[Chronicle] Live stream started for " + target.getName()
                        + ". Use /ch restore-view to stop.", NamedTextColor.GREEN));

        GhostPlayer ghost = new GhostPlayer(plugin,
                target.getUniqueId(), target.getName());


        prepareAdminForGhostReality(admin,
                target.getLocation().clone().add(5, 2, 0));

        LiveSession liveSession = new LiveSession(admin.getUniqueId(),
                target.getUniqueId(), ghost);
        liveSessions.put(admin.getUniqueId(), liveSession);


        List<ActionPacket> snap = plugin.getBufferManager()
                .flush(target.getUniqueId()).getPackets();
        if (!snap.isEmpty()) {
            ghost.spawn(admin, snap.getLast());
        } else {

            ActionPacket synthetic = ActionPacket.builder(
                            ActionPacket.ActionType.MOVEMENT,
                            System.currentTimeMillis(),
                            plugin.getServer().getCurrentTick())
                    .position(target.getX(), target.getY(), target.getZ())
                    .rotation(target.getYaw(), target.getPitch())
                    .build();
            ghost.spawn(admin, synthetic);
        }


        new BukkitRunnable() {
            @Override
            public void run() {
                LiveSession ls = liveSessions.get(admin.getUniqueId());
                if (ls == null || !admin.isOnline()) {
                    stopLive(admin);
                    cancel();
                    return;
                }
                Player tgt = plugin.getServer().getPlayer(ls.targetUuid());
                if (tgt == null || !tgt.isOnline()) {
                    admin.sendMessage(Component.text(
                            "[Chronicle] Live target disconnected.", NamedTextColor.YELLOW));
                    stopLive(admin);
                    cancel();
                    return;
                }

   
                ActionPacket liveFrame = ActionPacket.builder(
                                ActionPacket.ActionType.MOVEMENT,
                                System.currentTimeMillis(),
                                plugin.getServer().getCurrentTick())
                        .position(tgt.getX(), tgt.getY(), tgt.getZ())
                        .rotation(tgt.getYaw(), tgt.getPitch())
                        .velocity(tgt.getVelocity())
                        .onGround(tgt.isOnGround())
                        .sneaking(tgt.isSneaking())
                        .sprinting(tgt.isSprinting())
                        .blocking(tgt.isBlocking())
                        .elytraGliding(tgt.isGliding())
                        .lookVector(tgt.getLocation().getDirection().normalize())
                        .build();

                ls.ghost().applyFrame(admin, liveFrame);
            }
        }.runTaskTimer(plugin, 2L, 2L); 
    }


    public void stopLive(Player admin) {
        LiveSession ls = liveSessions.remove(admin.getUniqueId());
        if (ls == null) return;
        ls.ghost().despawnAll();
        restoreAdmin(admin, null);
        admin.sendMessage(Component.text("[Chronicle] Live stream ended.", NamedTextColor.GRAY));
    }


    public void setSpeed(Player admin, double speed) {
        ReplaySession session = activeSessions.get(admin.getUniqueId());
        if (session == null) {
            admin.sendMessage(Component.text(
                    "[Chronicle] You have no active replay session.", NamedTextColor.RED));
            return;
        }
        session.setSpeed(speed);
        admin.sendMessage(Component.text(
                "[Chronicle] Replay speed set to " + speed + "x.", NamedTextColor.GOLD));
    }


    public void restoreView(Player admin) {
        ReplaySession session = activeSessions.remove(admin.getUniqueId());
        if (session != null) {
            session.getGhost().despawn(admin);
            restoreAdmin(admin, session);
            admin.sendMessage(Component.text(
                    "[Chronicle] Ghost reality exited. Welcome back.", NamedTextColor.GRAY));
            return;
        }
        if (liveSessions.containsKey(admin.getUniqueId())) {
            stopLive(admin);
            return;
        }
        admin.sendMessage(Component.text(
                "[Chronicle] You are not in any Chronicle session.", NamedTextColor.YELLOW));
    }


    public void shutdown() {
        for (UUID uuid : Set.copyOf(activeSessions.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) restoreView(p);
        }
        for (UUID uuid : Set.copyOf(liveSessions.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopLive(p);
        }
        activeSessions.clear();
        liveSessions.clear();
    }

    public boolean hasActiveSession(UUID adminUuid) {
        return activeSessions.containsKey(adminUuid)
                || liveSessions.containsKey(adminUuid);
    }

    private void beginSession(Player admin, ReportRecord report) {
        List<ActionPacket> packets = report.getTdfPackets();
        if (packets.isEmpty()) return;

        ActionPacket firstFrame = packets.getFirst();


        Location watchPoint = new Location(
                admin.getWorld(),
                firstFrame.getX() + 5, firstFrame.getY() + 2, firstFrame.getZ(),
                admin.getYaw(), admin.getPitch()
        );
        prepareAdminForGhostReality(admin, watchPoint);

        reconstructScene(admin, report, firstFrame);

        GhostPlayer ghost = new GhostPlayer(plugin,
                report.getAccusedUuid(), report.getAccusedName());
        ghost.spawn(admin, firstFrame);

        double speed = plugin.getChronicleConfig().getDefaultReplaySpeed();
        ReplaySession session = new ReplaySession(admin, report, packets, ghost);
        session.setSpeed(speed);
        activeSessions.put(admin.getUniqueId(), session);

        admin.sendMessage(Component.text(
                "[Chronicle] Replay #" + report.getId()
                        + " — " + packets.size() + " frames — "
                        + String.format("%.1f", packets.size() / 20.0) + "s at " + speed + "x.",
                NamedTextColor.GREEN));

        schedulePlayback(session, admin);
    }

    private void schedulePlayback(ReplaySession session, Player admin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!admin.isOnline()
                        || !activeSessions.containsKey(admin.getUniqueId())) {
                    cancel();
                    return;
                }
                if (session.isPaused()) return;

  
                int toPlay = session.framesPerTick();
                for (int i = 0; i < toPlay; i++) {
                    ActionPacket frame = session.nextFrame();
                    if (frame == null) {

                        session.getGhost().despawn(admin);
                        activeSessions.remove(admin.getUniqueId());
                        restoreAdmin(admin, session);
                        admin.sendMessage(Component.text(
                                "[Chronicle] Replay finished. Use /ch restore-view if blocks look wrong.",
                                NamedTextColor.GRAY));
                        cancel();
                        return;
                    }
                    session.getGhost().applyFrame(admin, frame);
                }

                if (session.getFrameIndex() % 40 == 0) {
                    admin.sendActionBar(Component.text(
                            "Chronicle Replay  " + session.progressPercent() + "%  "
                                    + session.getSpeed() + "x",
                            NamedTextColor.GOLD));
                }
            }
        }.runTaskTimer(plugin, 5L, session.schedulerDelayTicks());
    }


    private void reconstructScene(Player admin, ReportRecord report, ActionPacket frame) {
        int radius = plugin.getChronicleConfig().getSceneRadius();

        if (report.getSceneBlocks() == null || report.getSceneBlocks().isEmpty()) return;

        report.getSceneBlocks().forEach((pos, blockData) -> {
            try {
                plugin.getProtocolManager().sendServerPacket(admin,
                        buildBlockChangePacket(pos[0], pos[1], pos[2], blockData));
            } catch (Exception e) {
                LOG.warning("Scene block restore failed at "
                        + Arrays.toString(pos) + ": " + e.getMessage());
            }
        });
    }

    private PacketContainer buildBlockChangePacket(int x, int y, int z, String blockData) {

        PacketContainer pkt = plugin.getProtocolManager()
                .createPacket(PacketType.Play.Server.BLOCK_CHANGE);
        pkt.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
        pkt.getBlockData().write(0,
                WrappedBlockData.createData(Bukkit.createBlockData(blockData)));
        return pkt;
    }


    private void prepareAdminForGhostReality(Player admin, Location watchPoint) {
        admin.setGameMode(GameMode.SPECTATOR);
        admin.teleport(watchPoint);
    }


    private void restoreAdmin(Player admin, ReplaySession session) {
        if (session == null) {

            admin.setGameMode(GameMode.SURVIVAL);
            return;
        }
        admin.teleport(session.getSavedLocation());
        admin.setGameMode(session.getSavedGameMode());
        admin.setAllowFlight(session.isSavedFlyAllowed());
        admin.setFlying(session.isSavedFlying());
    }


    private record LiveSession(UUID watcherUuid, UUID targetUuid, GhostPlayer ghost) { }
}
