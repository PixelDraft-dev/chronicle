package com.chronicle.replay;

import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.ActionPacket;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;


public final class GhostPlayer {

    private static final Logger LOG = Logger.getLogger("Chronicle.GhostPlayer");


    private static final int  GHOST_ENTITY_ID = 999_001;
    private static final UUID GHOST_UUID       = UUID.fromString("deadbeef-dead-beef-dead-beefdeadbeef");

    private final ChroniclePlugin plugin;
    private final ProtocolManager protocol;
    private final UUID            accusedUuid;
    private final String          accusedName;


    private final Set<UUID> viewers = new HashSet<>();

    public GhostPlayer(ChroniclePlugin plugin, UUID accusedUuid, String accusedName) {
        this.plugin      = plugin;
        this.protocol    = plugin.getProtocolManager();
        this.accusedUuid = accusedUuid;
        this.accusedName = accusedName;
    }


    public void spawn(Player viewer, ActionPacket firstFrame) {
        try {
            Location spawnLoc = new Location(
                    viewer.getWorld(),
                    firstFrame.getX(), firstFrame.getY(), firstFrame.getZ(),
                    firstFrame.getYaw(), firstFrame.getPitch()
            );

            sendPlayerInfoAdd(viewer, spawnLoc);
            sendSpawnEntity(viewer, spawnLoc);
            sendMetadata(viewer, firstFrame);

            viewers.add(viewer.getUniqueId());
            LOG.fine("Ghost spawned for viewer: " + viewer.getName());

        } catch (Exception e) {
            LOG.severe("Failed to spawn ghost for " + viewer.getName() + ": " + e.getMessage());
        }
    }


    public void applyFrame(Player viewer, ActionPacket frame) {
        if (!viewers.contains(viewer.getUniqueId())) return;

        try {
            sendTeleport(viewer, frame);
            sendLook(viewer, frame);

            switch (frame.getType()) {
                case ARM_SWING, BREAK_BLOCK    -> sendArmSwing(viewer, true);
                case INTERACT_BLOCK, PLACE_BLOCK -> sendArmSwing(viewer, false);
                default -> { /* movement only */ }
            }

            sendMetadata(viewer, frame);

        } catch (Exception e) {
            LOG.warning("Error applying ghost frame: " + e.getMessage());
        }
    }


    public void despawn(Player viewer) {
        if (!viewers.remove(viewer.getUniqueId())) return;
        try {
            sendDestroyEntity(viewer);
            sendPlayerInfoRemove(viewer);
        } catch (Exception e) {
            LOG.warning("Error despawning ghost for " + viewer.getName() + ": " + e.getMessage());
        }
    }


    public void despawnAll() {
        for (UUID uuid : List.copyOf(viewers)) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) despawn(p);
        }
    }


    private void sendPlayerInfoAdd(Player viewer, Location loc)
            throws InvocationTargetException {

        Player accused = plugin.getServer().getPlayer(accusedUuid);
        WrappedGameProfile profile = accused != null
                ? WrappedGameProfile.fromPlayer(accused).withName("[GHOST] " + accusedName)
                : new WrappedGameProfile(GHOST_UUID, "[GHOST] " + accusedName);

        PacketContainer infoPacket =
                protocol.createPacket(PKT_PLAYER_INFO_UPDATE);

        infoPacket.getPlayerInfoDataLists().write(0,
                List.of(new PlayerInfoData(
                        GHOST_UUID,
                        0,
                        false,  // not listed in tab — keeps ghost subtle
                        EnumWrappers.NativeGameMode.SURVIVAL,
                        profile,
                        null    // no display name override
                ))
        );

        protocol.sendServerPacket(viewer, infoPacket);
    }

    private void sendSpawnEntity(Player viewer, Location loc)
            throws InvocationTargetException {

        PacketContainer spawn = protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, GHOST_ENTITY_ID);
        spawn.getUUIDs().write(0, GHOST_UUID);
        spawn.getIntegers().write(1, 128);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());
        spawn.getBytes().write(0, angleToByte(loc.getPitch()));
        spawn.getBytes().write(1, angleToByte(loc.getYaw()));
        spawn.getBytes().write(2, angleToByte(loc.getYaw())); // head yaw
        spawn.getIntegers().write(2, 0);                      // data
        spawn.getShorts().write(0, (short) 0);                // vel x
        spawn.getShorts().write(1, (short) 0);                // vel y
        spawn.getShorts().write(2, (short) 0);                // vel z

        protocol.sendServerPacket(viewer, spawn);
    }

    private void sendTeleport(Player viewer, ActionPacket frame)
            throws InvocationTargetException {

        PacketContainer tp = protocol.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        tp.getIntegers().write(0, GHOST_ENTITY_ID);
        tp.getDoubles().write(0, frame.getX());
        tp.getDoubles().write(1, frame.getY());
        tp.getDoubles().write(2, frame.getZ());
        tp.getBytes().write(0, angleToByte(frame.getYaw()));
        tp.getBytes().write(1, angleToByte(frame.getPitch()));
        tp.getBooleans().write(0, frame.isOnGround());

        protocol.sendServerPacket(viewer, tp);
    }

    private void sendLook(Player viewer, ActionPacket frame)
            throws InvocationTargetException {

        PacketContainer look =
                protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        look.getIntegers().write(0, GHOST_ENTITY_ID);
        look.getBytes().write(0, angleToByte(frame.getYaw()));

        protocol.sendServerPacket(viewer, look);
    }

    private void sendArmSwing(Player viewer, boolean mainHand)
            throws InvocationTargetException {

        PacketContainer anim = protocol.createPacket(PacketType.Play.Server.ANIMATION);
        anim.getIntegers().write(0, GHOST_ENTITY_ID);

        anim.getIntegers().write(1, mainHand ? 0 : 3);

        protocol.sendServerPacket(viewer, anim);
    }


    private void sendMetadata(Player viewer, ActionPacket frame)
            throws InvocationTargetException {

        WrappedDataWatcher watcher = new WrappedDataWatcher();


        byte flags = 0;
        if (frame.isSneaking())    flags |= 0x02;
        if (frame.isSprinting())   flags |= 0x08;
        if (frame.isBlocking())    flags |= 0x10;
        if (frame.isElytraGliding()) flags |= 0x80;

        watcher.setObject(
                new WrappedDataWatcher.WrappedDataWatcherObject(
                        0, WrappedDataWatcher.Registry.get(Byte.class)),
                flags);

        watcher.setObject(
                new WrappedDataWatcher.WrappedDataWatcherObject(
                        6, WrappedDataWatcher.Registry.get(Integer.class, true)),
                frame.isSneaking() ? 5 : 0);

        PacketContainer meta =
                protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, GHOST_ENTITY_ID);
        meta.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

        protocol.sendServerPacket(viewer, meta);
    }

    private void sendDestroyEntity(Player viewer)
            throws InvocationTargetException {

        PacketContainer destroy =
                protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(GHOST_ENTITY_ID));

        protocol.sendServerPacket(viewer, destroy);
    }


    private void sendPlayerInfoRemove(Player viewer)
            throws InvocationTargetException {

        PacketContainer removePacket =
                protocol.createPacket(PKT_PLAYER_INFO_REMOVE);
        removePacket.getUUIDLists().write(0, List.of(GHOST_UUID));

        protocol.sendServerPacket(viewer, removePacket);
    }


    private static PacketType resolvePacketType(String... names) {
        for (String name : names) {
            try {
                return (PacketType) PacketType.Play.Server.class.getField(name).get(null);
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException(
                "[Chronicle] Could not resolve any of these PacketType.Play.Server fields: "
                        + java.util.Arrays.toString(names));
    }

    private static final PacketType PKT_PLAYER_INFO_UPDATE =
            resolvePacketType("PLAYER_INFO_UPDATE", "PLAYER_INFO");
    private static final PacketType PKT_PLAYER_INFO_REMOVE =
            resolvePacketType("PLAYER_INFO_REMOVE", "PLAYER_INFO");

    private byte angleToByte(float angle) {
        return (byte) Math.floor(angle * 256.0 / 360.0);
    }
}
