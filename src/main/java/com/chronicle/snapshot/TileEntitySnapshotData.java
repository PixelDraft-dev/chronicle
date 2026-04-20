package com.chronicle.snapshot;

import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public final class TileEntitySnapshotData implements Serializable {

    @Serial
    private static final long serialVersionUID = 2_000_011L;

    private final String worldName;
    private final int    blockX, blockY, blockZ;
    private final String blockType;     
    private final String blockData;     


    private final Map<Integer, byte[]> inventoryContents;


    private final String customName;


    private final int powerLevel;


    private final String nbtSummary;

    private TileEntitySnapshotData(String worldName, int x, int y, int z,
                                    String blockType, String blockData,
                                    Map<Integer, byte[]> inventoryContents,
                                    String customName, int powerLevel,
                                    String nbtSummary) {
        this.worldName         = worldName;
        this.blockX            = x;
        this.blockY            = y;
        this.blockZ            = z;
        this.blockType         = blockType;
        this.blockData         = blockData;
        this.inventoryContents = inventoryContents;
        this.customName        = customName;
        this.powerLevel        = powerLevel;
        this.nbtSummary        = nbtSummary;
    }

    public static TileEntitySnapshotData fromBlockState(BlockState state) {
        String worldName  = state.getWorld().getName();
        int    x          = state.getX();
        int    y          = state.getY();
        int    z          = state.getZ();
        String blockType  = state.getType().name();
        String blockData  = state.getBlockData().getAsString();

        Map<Integer, byte[]> inventory = null;
        String customName = null;
        int    powerLevel = 0;
        StringBuilder nbt = new StringBuilder();

        nbt.append("Block=").append(blockType)
           .append(",Pos=[").append(x).append(",").append(y).append(",").append(z).append("]");

        if (state instanceof Container container) {
            inventory = serializeInventory(container.getInventory());
            int itemCount = (int) inventory.values().stream()
                    .filter(b -> b != null && b.length > 0).count();
            nbt.append(",Items=").append(itemCount);


            if (state instanceof org.bukkit.Nameable nameable && nameable.customName() != null) {
                customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(nameable.customName());
                nbt.append(",Name=\"").append(customName).append("\"");
            }
        }


        if (state.getBlock().isBlockPowered()) {
            powerLevel = state.getBlock().getBlockPower();
            nbt.append(",Power=").append(powerLevel);
        }

        nbt.append(",World=").append(worldName);

        return new TileEntitySnapshotData(
                worldName, x, y, z,
                blockType, blockData,
                inventory, customName, powerLevel,
                nbt.toString()
        );
    }



    private static Map<Integer, byte[]> serializeInventory(Inventory inv) {
        Map<Integer, byte[]> map = new HashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                try {
                    map.put(i, item.serializeAsBytes());
                } catch (Exception e) {
                    map.put(i, new byte[0]);
                }
            }
        }
        return map;
    }



    public String              getWorldName()          { return worldName; }
    public int                 getBlockX()             { return blockX; }
    public int                 getBlockY()             { return blockY; }
    public int                 getBlockZ()             { return blockZ; }
    public String              getBlockType()          { return blockType; }
    public String              getBlockData()          { return blockData; }
    public Map<Integer, byte[]> getInventoryContents() { return inventoryContents; }
    public String              getCustomName()         { return customName; }
    public int                 getPowerLevel()         { return powerLevel; }
    public String              getNbtSummary()         { return nbtSummary; }


    public ItemStack deserializeSlot(int slot) {
        if (inventoryContents == null) return null;
        byte[] data = inventoryContents.get(slot);
        if (data == null || data.length == 0) return null;
        try {
            return ItemStack.deserializeBytes(data);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() { return nbtSummary; }
}
