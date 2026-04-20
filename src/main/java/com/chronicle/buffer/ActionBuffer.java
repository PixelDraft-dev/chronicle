package com.chronicle.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;


public final class ActionBuffer {

    private final UUID playerUuid;
    private final int  maxCapacity;

    private final ConcurrentLinkedQueue<ActionPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger(0);

    private volatile long lastWriteMillis = System.currentTimeMillis();

    public ActionBuffer(UUID playerUuid, int maxCapacity) {
        this.playerUuid  = playerUuid;
        this.maxCapacity = maxCapacity;
    }

    public void push(ActionPacket packet) {
        queue.offer(packet);
        if (size.incrementAndGet() > maxCapacity) {
            queue.poll();       
            size.decrementAndGet();
        }
        lastWriteMillis = System.currentTimeMillis();
    }


    public List<ActionPacket> snapshot() {
        return List.copyOf(queue);
    }


    public List<ActionPacket> drainAll() {
        List<ActionPacket> result = new ArrayList<>(size.get());
        ActionPacket p;
        while ((p = queue.poll()) != null) {
            result.add(p);
        }
        size.set(0);
        return result;
    }


    public int size() { return size.get(); }


    public boolean isEmpty() { return queue.isEmpty(); }


    public long getLastWriteMillis() { return lastWriteMillis; }


    public UUID getPlayerUuid() { return playerUuid; }


    public long tickSpan() {
        List<ActionPacket> snap = snapshot();
        if (snap.size() < 2) return 0;
        return snap.getLast().getServerTick() - snap.getFirst().getServerTick();
    }
}
