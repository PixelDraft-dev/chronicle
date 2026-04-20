package com.chronicle.listeners;

import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.ActionPacket;
import com.chronicle.buffer.ActionPacket.ActionType;
import com.chronicle.buffer.ActionBuffer;
import com.chronicle.buffer.BufferManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;


public final class PlayerActionListener implements Listener {

    private final ChroniclePlugin plugin;
    private final BufferManager   buffers;

    public PlayerActionListener(ChroniclePlugin plugin) {
        this.plugin  = plugin;
        this.buffers = plugin.getBufferManager();
    }



    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        buffers.onPlayerJoin(event.getPlayer());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block  block  = event.getBlock();

        ActionPacket packet = makeBaseBuilder(player, ActionType.BREAK_BLOCK)
                .blockTarget(block.getX(), block.getY(), block.getZ(),
                             player.getFacing(), block.getType().name())
                .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                .heldSlot(player.getInventory().getHeldItemSlot())
                .build();

        push(player, packet);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        Block  block  = event.getBlockPlaced();

        ActionPacket packet = makeBaseBuilder(player, ActionType.PLACE_BLOCK)
                .blockTarget(block.getX(), block.getY(), block.getZ(),
                             event.getBlockAgainst().getFace(block), block.getType().name())
                .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                .offHandNBT(serializeItem(player.getInventory().getItemInOffHand()))
                .heldSlot(player.getInventory().getHeldItemSlot())
                .build();

        push(player, packet);
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Block  block  = event.getClickedBlock();

        ActionPacket packet = makeBaseBuilder(player, ActionType.INTERACT_BLOCK)
                .blockTarget(block.getX(), block.getY(), block.getZ(),
                             event.getBlockFace(), block.getType().name())
                .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                .offHandNBT(serializeItem(player.getInventory().getItemInOffHand()))
                .heldSlot(player.getInventory().getHeldItemSlot())
                .build();

        push(player, packet);
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;


        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(),
                6.0, entity -> entity.equals(event.getEntity()));

        ActionPacket packet = makeBaseBuilder(player, ActionType.ENTITY_ATTACK)
                .targetEntity(event.getEntity().getEntityId())
                .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                .heldSlot(player.getInventory().getHeldItemSlot())
                .lookVector(computeLookVector(player))
                .build();

        push(player, packet);
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        ActionPacket packet = makeBaseBuilder(player, ActionType.ENTITY_INTERACT)
                .targetEntity(event.getRightClicked().getEntityId())
                .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                .offHandNBT(serializeItem(player.getInventory().getItemInOffHand()))
                .heldSlot(player.getInventory().getHeldItemSlot())
                .build();

        push(player, packet);
    }



    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        ActionPacket packet = makeBaseBuilder(player, ActionType.MOVEMENT)
                .heldSlot(event.getNewSlot())
                .build();
        push(player, packet);
    }


    private ActionPacket.Builder makeBaseBuilder(Player player, ActionType type) {
        long tick = plugin.getServer().getCurrentTick();

        return ActionPacket.builder(type, System.currentTimeMillis(), tick)
                .position(player.getX(), player.getY(), player.getZ())
                .rotation(player.getYaw(), player.getPitch())
                .velocity(player.getVelocity())
                .onGround(player.isOnGround())
                .sneaking(player.isSneaking())
                .sprinting(player.isSprinting())
                .blocking(player.isBlocking())
                .elytraGliding(player.isGliding())
                .lookVector(computeLookVector(player));
    }


    private byte[] serializeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return new byte[0];
        try {
            return item.serializeAsBytes();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to serialize ItemStack: " + e.getMessage());
            return new byte[0];
        }
    }

    private Vector computeLookVector(Player player) {
        return player.getLocation().getDirection().normalize();
    }


    private void push(Player player, ActionPacket packet) {
        ActionBuffer buf = buffers.getOrCreate(player);
        buf.push(packet);
    }
}
