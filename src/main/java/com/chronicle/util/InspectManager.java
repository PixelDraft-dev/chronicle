package com.chronicle.util;

import com.chronicle.ChroniclePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class InspectManager implements Listener {

    private final ChroniclePlugin plugin;


    private final Set<UUID> inspecting = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public InspectManager(ChroniclePlugin plugin) {
        this.plugin = plugin;
    }


    public boolean toggle(UUID adminUuid) {
        if (inspecting.contains(adminUuid)) {
            inspecting.remove(adminUuid);
            return false;
        } else {
            inspecting.add(adminUuid);
            return true;
        }
    }

    public boolean isInspecting(UUID adminUuid) {
        return inspecting.contains(adminUuid);
    }


    public void clear(UUID adminUuid) {
        inspecting.remove(adminUuid);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractBlock(PlayerInteractEvent event) {
        Player admin = event.getPlayer();
        if (!inspecting.contains(admin.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        showBlockInfo(admin, event.getClickedBlock());
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player admin = event.getPlayer();
        if (!inspecting.contains(admin.getUniqueId())) return;

        event.setCancelled(true);
        showEntityInfo(admin, event.getRightClicked());
    }



    private void showBlockInfo(Player admin, Block block) {
        admin.sendMessage(Component.text(
                "══ Block Inspect ══", NamedTextColor.GOLD, TextDecoration.BOLD));

        admin.sendMessage(row("Material", block.getType().name()));
        admin.sendMessage(row("Position",
                block.getX() + ", " + block.getY() + ", " + block.getZ()));
        admin.sendMessage(row("World", block.getWorld().getName()));
        admin.sendMessage(row("BlockData", block.getBlockData().getAsString()));


        if (block.getState() instanceof org.bukkit.block.Container container) {
            int items = 0;
            for (var item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) items++;
            }
            admin.sendMessage(row("Items in container", items + " / "
                    + container.getInventory().getSize()));
            if (block.getState() instanceof org.bukkit.Nameable n && n.customName() != null) {
                admin.sendMessage(row("Custom name",
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(n.customName())));
            }
        }


        PersistentDataContainer pdc = block.getState() instanceof org.bukkit.persistence.PersistentDataHolder h
                ? h.getPersistentDataContainer() : null;
        if (pdc != null && !pdc.isEmpty()) {
            admin.sendMessage(row("PersistentData keys",
                    pdc.getKeys().toString()));
        }
    }

    private void showEntityInfo(Player admin, Entity entity) {
        admin.sendMessage(Component.text(
                "══ Entity Inspect ══", NamedTextColor.GOLD, TextDecoration.BOLD));

        admin.sendMessage(row("Type", entity.getType().name()));
        admin.sendMessage(row("UUID", entity.getUniqueId().toString()));
        admin.sendMessage(row("EntityID", String.valueOf(entity.getEntityId())));
        admin.sendMessage(row("World", entity.getWorld().getName()));
        admin.sendMessage(row("Position",
                String.format("%.2f, %.2f, %.2f",
                        entity.getX(), entity.getY(), entity.getZ())));

        if (entity instanceof org.bukkit.entity.Damageable d) {
            admin.sendMessage(row("Health",
                    String.format("%.1f / %.1f", d.getHealth(), d.getMaxHealth())));
        }

        if (entity instanceof Player p) {
            admin.sendMessage(row("Gamemode", p.getGameMode().name()));
            admin.sendMessage(row("Food",    String.valueOf(p.getFoodLevel())));
            admin.sendMessage(row("Level",   String.valueOf(p.getLevel())));
            admin.sendMessage(row("Ping",    p.getPing() + "ms"));
        }

        if (entity.customName() != null) {
            admin.sendMessage(row("Custom name",
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(entity.customName())));
        }

        // Velocity.
        var vel = entity.getVelocity();
        admin.sendMessage(row("Velocity",
                String.format("%.3f, %.3f, %.3f | spd=%.3f",
                        vel.getX(), vel.getY(), vel.getZ(), vel.length())));

        admin.sendMessage(Component.text("═══════════════════", NamedTextColor.GOLD));
    }

    private Component row(String key, String value) {
        return Component.text("  " + key + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
