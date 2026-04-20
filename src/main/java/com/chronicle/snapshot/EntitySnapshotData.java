package com.chronicle.snapshot;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;


public final class EntitySnapshotData implements Serializable {

    @Serial
    private static final long serialVersionUID = 2_000_010L;

    private final UUID   uuid;
    private final String entityType;   
    private final String worldName;


    private final double x, y, z;
    private final float  yaw, pitch;

 
    private final double velX, velY, velZ;
    private final boolean onGround;


    private final double maxHealth;
    private final double health;
    private final String customName;   
    private final boolean glowing;


    private final String playerName;
    private final int    foodLevel;
    private final int    expLevel;


    private final byte[] mainHandNbt;
    private final byte[] offHandNbt;
    private final byte[] helmetNbt;
    private final byte[] chestplateNbt;
    private final byte[] leggingsNbt;
    private final byte[] bootsNbt;


    private final String nbtString;

    private EntitySnapshotData(Builder b) {
        this.uuid         = b.uuid;
        this.entityType   = b.entityType;
        this.worldName    = b.worldName;
        this.x            = b.x;
        this.y            = b.y;
        this.z            = b.z;
        this.yaw          = b.yaw;
        this.pitch        = b.pitch;
        this.velX         = b.velX;
        this.velY         = b.velY;
        this.velZ         = b.velZ;
        this.onGround     = b.onGround;
        this.maxHealth    = b.maxHealth;
        this.health       = b.health;
        this.customName   = b.customName;
        this.glowing      = b.glowing;
        this.playerName   = b.playerName;
        this.foodLevel    = b.foodLevel;
        this.expLevel     = b.expLevel;
        this.mainHandNbt  = b.mainHandNbt;
        this.offHandNbt   = b.offHandNbt;
        this.helmetNbt    = b.helmetNbt;
        this.chestplateNbt = b.chestplateNbt;
        this.leggingsNbt  = b.leggingsNbt;
        this.bootsNbt     = b.bootsNbt;
        this.nbtString    = b.nbtString;
    }



    public static EntitySnapshotData fromEntity(Entity entity) {
        Builder b = new Builder();
        b.uuid       = entity.getUniqueId();
        b.entityType = entity.getType().name();
        b.worldName  = entity.getWorld().getName();

        Location loc = entity.getLocation();
        b.x = loc.getX(); b.y = loc.getY(); b.z = loc.getZ();
        b.yaw = loc.getYaw(); b.pitch = loc.getPitch();

        Vector vel = entity.getVelocity();
        b.velX = vel.getX(); b.velY = vel.getY(); b.velZ = vel.getZ();
        b.onGround = entity.isOnGround();

        if (entity instanceof Damageable d) {
            b.health    = d.getHealth();
            b.maxHealth = d.getMaxHealth();
        }

        b.customName = entity.customName() != null
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(entity.customName())
                : null;
        b.glowing = entity.isGlowing();

        if (entity instanceof Player p) {
            b.playerName = p.getName();
            b.foodLevel  = p.getFoodLevel();
            b.expLevel   = p.getLevel();
        }

        if (entity instanceof LivingEntity le) {
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                b.mainHandNbt   = serItem(eq.getItemInMainHand());
                b.offHandNbt    = serItem(eq.getItemInOffHand());
                b.helmetNbt     = serItem(eq.getHelmet());
                b.chestplateNbt = serItem(eq.getChestplate());
                b.leggingsNbt   = serItem(eq.getLeggings());
                b.bootsNbt      = serItem(eq.getBoots());
            }
        }


        b.nbtString = "EntityType=" + entity.getType().name()
                + ",UUID=" + entity.getUniqueId()
                + ",Pos=[" + String.format("%.2f,%.2f,%.2f", loc.getX(), loc.getY(), loc.getZ()) + "]"
                + (entity instanceof Damageable d
                        ? ",Health=" + String.format("%.1f", d.getHealth()) + "/" + String.format("%.1f", d.getMaxHealth())
                        : "");

        return new EntitySnapshotData(b);
    }



    public UUID   getUuid()        { return uuid; }
    public String getEntityType()  { return entityType; }
    public String getWorldName()   { return worldName; }
    public double getX()           { return x; }
    public double getY()           { return y; }
    public double getZ()           { return z; }
    public float  getYaw()         { return yaw; }
    public float  getPitch()       { return pitch; }
    public Vector getVelocity()    { return new Vector(velX, velY, velZ); }
    public boolean isOnGround()    { return onGround; }
    public double getHealth()      { return health; }
    public double getMaxHealth()   { return maxHealth; }
    public String getCustomName()  { return customName; }
    public boolean isGlowing()     { return glowing; }
    public String getPlayerName()  { return playerName; }
    public int    getFoodLevel()   { return foodLevel; }
    public int    getExpLevel()    { return expLevel; }
    public String getNbtString()   { return nbtString; }



    private static byte[] serItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        try { return item.serializeAsBytes(); } catch (Exception e) { return null; }
    }

    private static final class Builder {
        UUID uuid; String entityType; String worldName;
        double x, y, z; float yaw, pitch;
        double velX, velY, velZ; boolean onGround;
        double maxHealth, health; String customName; boolean glowing;
        String playerName; int foodLevel, expLevel;
        byte[] mainHandNbt, offHandNbt, helmetNbt, chestplateNbt, leggingsNbt, bootsNbt;
        String nbtString;
    }

    @Override
    public String toString() { return nbtString; }
}
