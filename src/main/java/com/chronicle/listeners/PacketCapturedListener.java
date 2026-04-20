package com.chronicle.listeners;

import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.ActionPacket;
import com.chronicle.buffer.ActionPacket.ActionType;
import com.chronicle.buffer.ActionBuffer;
import com.chronicle.buffer.BufferManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;


public final class PacketCaptureListener {

    private final ChroniclePlugin plugin;
    private final BufferManager   buffers;
    private final ProtocolManager protocol;

    public PacketCaptureListener(ChroniclePlugin plugin, ProtocolManager protocol) {
        this.plugin   = plugin;
        this.buffers  = plugin.getBufferManager();
        this.protocol = protocol;
    }


    public void register() {
        registerMovementListener();
        registerBlockDigListener();
        registerArmSwingListener();
        registerEntityActionListener();
    }


    public void unregister() {
        protocol.removePacketListeners(plugin);
    }



    private void registerMovementListener() {
        protocol.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) return;

                Player          player = event.getPlayer();
                PacketContainer pkt    = event.getPacket();
                PacketType      type   = event.getPacketType();

                double x = 0, y = 0, z = 0;
                float  yaw = 0, pitch = 0;
                boolean onGround;

                if (type == PacketType.Play.Client.POSITION
                        || type == PacketType.Play.Client.POSITION_LOOK) {
                    x = pkt.getDoubles().read(0);
                    y = pkt.getDoubles().read(1);
                    z = pkt.getDoubles().read(2);
                }

                if (type == PacketType.Play.Client.POSITION_LOOK
                        || type == PacketType.Play.Client.LOOK) {
                    yaw   = pkt.getFloat().read(0);
                    pitch = pkt.getFloat().read(1);
                }

                onGround = pkt.getBooleans().size() > 0 && pkt.getBooleans().read(0);

                if (type == PacketType.Play.Client.LOOK) {
                    x = player.getX();
                    y = player.getY();
                    z = player.getZ();
                }

                ActionType actionType = (type == PacketType.Play.Client.LOOK)
                        ? ActionType.LOOK : ActionType.MOVEMENT;

                long tick = plugin.getServer().getCurrentTick();
                Vector look = yawPitchToVector(yaw, pitch);

                ActionPacket packet = ActionPacket
                        .builder(actionType, System.currentTimeMillis(), tick)
                        .position(x, y, z)
                        .rotation(yaw, pitch)
                        .velocity(player.getVelocity())  
                        .onGround(onGround)
                        .sneaking(player.isSneaking())
                        .sprinting(player.isSprinting())
                        .blocking(player.isBlocking())
                        .elytraGliding(player.isGliding())
                        .lookVector(look)
                        .heldSlot(player.getInventory().getHeldItemSlot())
                        .build();

                buffers.getOrCreate(player).push(packet);
            }
        });
    }



    private void registerBlockDigListener() {
        protocol.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Client.BLOCK_DIG
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) return;
                PacketContainer pkt = event.getPacket();

 
                EnumWrappers.PlayerDigType digType = pkt.getPlayerDigTypes().read(0);
                if (digType != EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) return;

                Player player = event.getPlayer();
                var    pos    = pkt.getBlockPositionModifier().read(0);
                var    face   = pkt.getDirections().read(0);
                long   tick   = plugin.getServer().getCurrentTick();

                ActionPacket packet = ActionPacket
                        .builder(ActionType.BREAK_BLOCK, System.currentTimeMillis(), tick)
                        .position(player.getX(), player.getY(), player.getZ())
                        .rotation(player.getYaw(), player.getPitch())
                        .velocity(player.getVelocity())
                        .onGround(player.isOnGround())
                        .sneaking(player.isSneaking())
                        .sprinting(player.isSprinting())
                        .blockTarget(pos.getX(), pos.getY(), pos.getZ(),
                                mapDirection(face),
                                player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ())
                                      .getType().name())
                        .mainHandNBT(serializeItem(player.getInventory().getItemInMainHand()))
                        .heldSlot(player.getInventory().getHeldItemSlot())
                        .lookVector(player.getLocation().getDirection().normalize())
                        .build();

                buffers.getOrCreate(player).push(packet);
            }
        });
    }



    private void registerArmSwingListener() {
        protocol.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Client.ARM_ANIMATION
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) return;
                Player player = event.getPlayer();
                long   tick   = plugin.getServer().getCurrentTick();

                ActionPacket packet = ActionPacket
                        .builder(ActionType.ARM_SWING, System.currentTimeMillis(), tick)
                        .position(player.getX(), player.getY(), player.getZ())
                        .rotation(player.getYaw(), player.getPitch())
                        .velocity(player.getVelocity())
                        .onGround(player.isOnGround())
                        .sneaking(player.isSneaking())
                        .lookVector(player.getLocation().getDirection().normalize())
                        .build();

                buffers.getOrCreate(player).push(packet);
            }
        });
    }



    private void registerEntityActionListener() {
        protocol.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Client.ENTITY_ACTION
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) return;
                Player player = event.getPlayer();
                long   tick   = plugin.getServer().getCurrentTick();

                ActionPacket packet = ActionPacket
                        .builder(ActionType.MOVEMENT, System.currentTimeMillis(), tick)
                        .position(player.getX(), player.getY(), player.getZ())
                        .rotation(player.getYaw(), player.getPitch())
                        .velocity(player.getVelocity())
                        .onGround(player.isOnGround())
                        .sneaking(player.isSneaking())
                        .sprinting(player.isSprinting())
                        .elytraGliding(player.isGliding())
                        .lookVector(player.getLocation().getDirection().normalize())
                        .build();

                buffers.getOrCreate(player).push(packet);
            }
        });
    }


    private Vector yawPitchToVector(float yaw, float pitch) {
        double radYaw   = Math.toRadians(-yaw);
        double radPitch = Math.toRadians(-pitch);
        double x = Math.sin(radYaw) * Math.cos(radPitch);
        double y = Math.sin(radPitch);
        double z = Math.cos(radYaw) * Math.cos(radPitch);
        return new Vector(x, y, z).normalize();
    }


    private org.bukkit.block.BlockFace mapDirection(EnumWrappers.Direction dir) {
        return switch (dir) {
            case DOWN  -> org.bukkit.block.BlockFace.DOWN;
            case UP    -> org.bukkit.block.BlockFace.UP;
            case NORTH -> org.bukkit.block.BlockFace.NORTH;
            case SOUTH -> org.bukkit.block.BlockFace.SOUTH;
            case WEST  -> org.bukkit.block.BlockFace.WEST;
            case EAST  -> org.bukkit.block.BlockFace.EAST;
        };
    }

    private byte[] serializeItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return new byte[0];
        try {
            return item.serializeAsBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
