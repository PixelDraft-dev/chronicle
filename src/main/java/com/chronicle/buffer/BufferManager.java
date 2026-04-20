package com.chronicle.buffer;

import com.chronicle.ChroniclePlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public final class BufferManager {

    private final ChroniclePlugin plugin;
    private final Logger          log;


    private final int  maxTicks;

    private final long idlePruneSeconds;


    private final Map<UUID, ActionBuffer> buffers = new ConcurrentHashMap<>();

    public BufferManager(ChroniclePlugin plugin) {
        this.plugin          = plugin;
        this.log             = plugin.getLogger();
        this.maxTicks        = plugin.getChronicleConfig().getBufferMaxTicks();
        this.idlePruneSeconds = plugin.getChronicleConfig().getIdlePruneSeconds();
    }




    public void startPruneTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long threshold = idlePruneSeconds * 1_000L;
                int pruned = 0;
                for (var entry : buffers.entrySet()) {
                    if (now - entry.getValue().getLastWriteMillis() > threshold) {
                        buffers.remove(entry.getKey());
                        pruned++;
                    }
                }
                if (pruned > 0) {
                    log.fine("BufferManager pruned " + pruned + " idle player buffer(s).");
                }
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L); 
    }

    public void shutdown() {
        buffers.clear();
    }




    public ActionBuffer getOrCreate(Player player) {
        return buffers.computeIfAbsent(player.getUniqueId(),
                uuid -> new ActionBuffer(uuid, maxTicks));
    }


    public ActionBuffer get(UUID uuid) {
        return buffers.get(uuid);
    }


    public void onPlayerJoin(Player player) {
        buffers.put(player.getUniqueId(),
                new ActionBuffer(player.getUniqueId(), maxTicks));
        log.fine("ActionBuffer created for " + player.getName());
    }


    public TemporalDataFile flush(UUID targetUuid) {
        ActionBuffer buf = buffers.get(targetUuid);
        if (buf == null || buf.isEmpty()) return null;

        List<ActionPacket> packets = buf.snapshot();
        log.info("Buffer flushed for " + targetUuid + " — " + packets.size() + " packets captured.");
        return new TemporalDataFile(targetUuid, packets);
    }


    public int bufferSize(UUID uuid) {
        ActionBuffer buf = buffers.get(uuid);
        return buf == null ? -1 : buf.size();
    }


    public long tickSpan(UUID uuid) {
        ActionBuffer buf = buffers.get(uuid);
        return buf == null ? -1 : buf.tickSpan();
    }

    public int activeCount() { return buffers.size(); }
}
