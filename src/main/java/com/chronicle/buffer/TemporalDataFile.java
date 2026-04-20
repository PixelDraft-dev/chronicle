package com.chronicle.buffer;

import com.chronicle.util.CompressionUtil;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TemporalDataFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 2_000_002L;

    private static final int MAGIC = 0x54_44_46_32; 

    private final UUID              playerUuid;
    private final long              captureTimestamp;   
    private final List<ActionPacket> packets;           
    private final int                tickSpan;

    public TemporalDataFile(UUID playerUuid, List<ActionPacket> packets) {
        this.playerUuid       = playerUuid;
        this.captureTimestamp = System.currentTimeMillis();
        this.packets          = List.copyOf(packets);     
        this.tickSpan         = packets.size() < 2
                ? 0
                : (int)(packets.getLast().getServerTick() - packets.getFirst().getServerTick());
    }



    public UUID              getPlayerUuid()       { return playerUuid; }
    public long              getCaptureTimestamp() { return captureTimestamp; }
    public List<ActionPacket> getPackets()          { return packets; }
    public int               getPacketCount()      { return packets.size(); }
    public int               getTickSpan()         { return tickSpan; }


    public double getDurationSeconds() { return tickSpan / 20.0; }


    public byte[] serialize() throws IOException {
        byte[] raw = toRawBytes();
        return CompressionUtil.compress(raw);
    }


    public static TemporalDataFile deserialize(byte[] compressed) throws IOException, ClassNotFoundException {
        byte[] raw = CompressionUtil.decompress(compressed);
        return fromRawBytes(raw);
    }



    private byte[] toRawBytes() throws IOException {
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos)) {
            oos.writeInt(MAGIC);
            oos.writeInt(packets.size());
            oos.writeObject(this);
            return bos.toByteArray();
        }
    }

    private static TemporalDataFile fromRawBytes(byte[] raw) throws IOException, ClassNotFoundException {
        try (var bis = new ByteArrayInputStream(raw);
             var ois = new ObjectInputStream(bis)) {
            int magic = ois.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid TDF magic: 0x" + Integer.toHexString(magic));
            }
            ois.readInt(); 
            return (TemporalDataFile) ois.readObject();
        }
    }
}
