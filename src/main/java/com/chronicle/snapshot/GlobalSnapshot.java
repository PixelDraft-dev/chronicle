package com.chronicle.snapshot;

import com.chronicle.ChroniclePlugin;
import com.chronicle.database.DatabaseManager;
import com.chronicle.util.CompressionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


public final class GlobalSnapshot {

    private static final Logger LOG = Logger.getLogger("Chronicle.GlobalSnapshot");

    private final ChroniclePlugin plugin;
    private final DatabaseManager db;

    public GlobalSnapshot(ChroniclePlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
    }


    public CompletableFuture<Long> capture(Player triggeredBy, String label) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        triggeredBy.sendMessage(Component.text(
                "[Chronicle] Initiating global server snapshot...",
                NamedTextColor.YELLOW));


        plugin.getServer().getScheduler().runTask(plugin, () -> {

            List<EntitySnapshotData>     entities    = new ArrayList<>();
            List<TileEntitySnapshotData> tileEntities = new ArrayList<>();
            AtomicInteger entityCount = new AtomicInteger();
            AtomicInteger blockCount  = new AtomicInteger();

            for (World world : Bukkit.getWorlds()) {

                for (Entity entity : world.getEntities()) {
                    entities.add(EntitySnapshotData.fromEntity(entity));
                    entityCount.incrementAndGet();
                }

     
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (BlockState blockState : chunk.getTileEntities()) {
                        TileEntitySnapshotData snap = TileEntitySnapshotData.fromBlockState(blockState);
                        if (snap != null) {
                            tileEntities.add(snap);
                            blockCount.incrementAndGet();
                        }
                    }
                }
            }

            LOG.info("Global snapshot collection complete: "
                    + entityCount + " entities, " + blockCount + " tile-entities.");

  
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    byte[] blob = serializeBlob(entities, tileEntities);

                    long snapshotId = db.saveSnapshot(
                            triggeredBy.getUniqueId(),
                            label != null ? label : "snapshot-" + System.currentTimeMillis(),
                            entityCount.get(),
                            blockCount.get(),
                            blob
                    );

     
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        triggeredBy.sendMessage(Component.text(
                                "[Chronicle] Snapshot #" + snapshotId + " complete — "
                                        + entityCount + " entities + "
                                        + blockCount + " tile-entities archived.",
                                NamedTextColor.GREEN));
                        future.complete(snapshotId);
                    });

                } catch (Exception e) {
                    LOG.severe("Global snapshot serialization failed: " + e.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        triggeredBy.sendMessage(Component.text(
                                "[Chronicle] Snapshot FAILED: " + e.getMessage(),
                                NamedTextColor.RED));
                        future.completeExceptionally(e);
                    });
                }
            });
        });

        return future;
    }


    private byte[] serializeBlob(List<EntitySnapshotData> entities,
                                  List<TileEntitySnapshotData> tileEntities)
            throws IOException {

        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos)) {

            oos.writeInt(0x534E4150);       
            oos.writeInt(1);               

            oos.writeInt(entities.size());
            for (EntitySnapshotData e : entities) oos.writeObject(e);

            oos.writeInt(tileEntities.size());
            for (TileEntitySnapshotData t : tileEntities) oos.writeObject(t);

            byte[] raw = bos.toByteArray();
            return CompressionUtil.compress(raw);
        }
    }

    public static SnapshotBlob deserialize(byte[] compressed)
            throws IOException, ClassNotFoundException {

        byte[] raw = CompressionUtil.decompress(compressed);
        try (var bis = new ByteArrayInputStream(raw);
             var ois = new ObjectInputStream(bis)) {

            int magic = ois.readInt();
            if (magic != 0x534E4150) throw new IOException("Invalid snapshot magic.");
            ois.readInt(); // version

            int eCount = ois.readInt();
            List<EntitySnapshotData> entities = new ArrayList<>(eCount);
            for (int i = 0; i < eCount; i++) entities.add((EntitySnapshotData) ois.readObject());

            int tCount = ois.readInt();
            List<TileEntitySnapshotData> tiles = new ArrayList<>(tCount);
            for (int i = 0; i < tCount; i++) tiles.add((TileEntitySnapshotData) ois.readObject());

            return new SnapshotBlob(entities, tiles);
        }
    }


    public record SnapshotBlob(
            List<EntitySnapshotData>     entities,
            List<TileEntitySnapshotData> tileEntities
    ) { }
}
