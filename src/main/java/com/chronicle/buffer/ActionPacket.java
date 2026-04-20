package com.chronicle.buffer;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.io.Serial;
import java.io.Serializable;


public final class ActionPacket implements Serializable {

    @Serial
    private static final long serialVersionUID = 2_000_001L;

    // ── Action classification ──────────────────────────────────────────────────
    public enum ActionType {

        MOVEMENT,

        ARM_SWING,

        INTERACT_BLOCK,
  
        BREAK_BLOCK,

        PLACE_BLOCK,

        LOOK,

        ENTITY_INTERACT,

        ENTITY_ATTACK
    }


    private final long       timestamp;    
    private final long       serverTick;   
    private final ActionType type;

    private final double  x, y, z;
    private final float   yaw, pitch;


    private final double  velX, velY, velZ;
    private final boolean onGround;

    private final boolean sneaking;
    private final boolean sprinting;
    private final boolean blocking;        
    private final boolean elytraGliding;


    private final int       blockX, blockY, blockZ;
    private final BlockFace blockFace;
    private final String    blockType;     


    private final byte[] mainHandNBT;
    private final byte[] offHandNBT;
    private final int    heldSlot;


    private final int    targetEntityId;


    private final double lookDirX, lookDirY, lookDirZ;

    private ActionPacket(Builder b) {
        this.timestamp       = b.timestamp;
        this.serverTick      = b.serverTick;
        this.type            = b.type;
        this.x               = b.x;
        this.y               = b.y;
        this.z               = b.z;
        this.yaw             = b.yaw;
        this.pitch           = b.pitch;
        this.velX            = b.velX;
        this.velY            = b.velY;
        this.velZ            = b.velZ;
        this.onGround        = b.onGround;
        this.sneaking        = b.sneaking;
        this.sprinting       = b.sprinting;
        this.blocking        = b.blocking;
        this.elytraGliding   = b.elytraGliding;
        this.blockX          = b.blockX;
        this.blockY          = b.blockY;
        this.blockZ          = b.blockZ;
        this.blockFace       = b.blockFace;
        this.blockType       = b.blockType;
        this.mainHandNBT     = b.mainHandNBT;
        this.offHandNBT      = b.offHandNBT;
        this.heldSlot        = b.heldSlot;
        this.targetEntityId  = b.targetEntityId;
        this.lookDirX        = b.lookDirX;
        this.lookDirY        = b.lookDirY;
        this.lookDirZ        = b.lookDirZ;
    }


    public static Builder builder(ActionType type, long timestamp, long serverTick) {
        return new Builder(type, timestamp, serverTick);
    }


    public long       getTimestamp()      { return timestamp; }
    public long       getServerTick()     { return serverTick; }
    public ActionType getType()           { return type; }
    public double     getX()              { return x; }
    public double     getY()              { return y; }
    public double     getZ()              { return z; }
    public float      getYaw()            { return yaw; }
    public float      getPitch()          { return pitch; }
    public Vector     getVelocity()       { return new Vector(velX, velY, velZ); }
    public boolean    isOnGround()        { return onGround; }
    public boolean    isSneaking()        { return sneaking; }
    public boolean    isSprinting()       { return sprinting; }
    public boolean    isBlocking()        { return blocking; }
    public boolean    isElytraGliding()   { return elytraGliding; }
    public int        getBlockX()         { return blockX; }
    public int        getBlockY()         { return blockY; }
    public int        getBlockZ()         { return blockZ; }
    public BlockFace  getBlockFace()      { return blockFace; }
    public String     getBlockType()      { return blockType; }
    public byte[]     getMainHandNBT()    { return mainHandNBT; }
    public byte[]     getOffHandNBT()     { return offHandNBT; }
    public int        getHeldSlot()       { return heldSlot; }
    public int        getTargetEntityId() { return targetEntityId; }

    public Vector getLookVector() { return new Vector(lookDirX, lookDirY, lookDirZ); }

    public static final class Builder {
        private final long       timestamp;
        private final long       serverTick;
        private final ActionType type;

        private double  x, y, z;
        private float   yaw, pitch;
        private double  velX, velY, velZ;
        private boolean onGround, sneaking, sprinting, blocking, elytraGliding;
        private int     blockX, blockY, blockZ;
        private BlockFace blockFace = BlockFace.UP;
        private String  blockType  = "";
        private byte[]  mainHandNBT, offHandNBT;
        private int     heldSlot;
        private int     targetEntityId = -1;
        private double  lookDirX, lookDirY = -1, lookDirZ;

        private Builder(ActionType type, long timestamp, long serverTick) {
            this.type       = type;
            this.timestamp  = timestamp;
            this.serverTick = serverTick;
        }

        public Builder position(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }

        public Builder rotation(float yaw, float pitch) {
            this.yaw = yaw; this.pitch = pitch;
            return this;
        }

        public Builder velocity(Vector v) {
            velX = v.getX(); velY = v.getY(); velZ = v.getZ();
            return this;
        }

        public Builder onGround(boolean v)      { this.onGround = v;      return this; }
        public Builder sneaking(boolean v)      { this.sneaking = v;      return this; }
        public Builder sprinting(boolean v)     { this.sprinting = v;     return this; }
        public Builder blocking(boolean v)      { this.blocking = v;      return this; }
        public Builder elytraGliding(boolean v) { this.elytraGliding = v; return this; }

        public Builder blockTarget(int bx, int by, int bz, BlockFace face, String type) {
            this.blockX = bx; this.blockY = by; this.blockZ = bz;
            this.blockFace = face; this.blockType = type;
            return this;
        }

        public Builder mainHandNBT(byte[] nbt) { this.mainHandNBT = nbt; return this; }
        public Builder offHandNBT(byte[] nbt)  { this.offHandNBT = nbt;  return this; }
        public Builder heldSlot(int s)         { this.heldSlot = s;      return this; }
        public Builder targetEntity(int id)    { this.targetEntityId = id; return this; }

        public Builder lookVector(Vector dir) {
            lookDirX = dir.getX(); lookDirY = dir.getY(); lookDirZ = dir.getZ();
            return this;
        }

        public ActionPacket build() { return new ActionPacket(this); }
    }

    @Override
    public String toString() {
        return "ActionPacket{tick=%d, type=%s, pos=(%.2f,%.2f,%.2f), yaw=%.1f}"
                .formatted(serverTick, type, x, y, z, yaw);
    }
}
