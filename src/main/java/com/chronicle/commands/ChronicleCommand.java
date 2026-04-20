package com.chronicle.commands;

import com.chronicle.ChroniclePlugin;
import com.chronicle.replay.ReplayEngine;
import com.chronicle.snapshot.GlobalSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


public final class ChronicleCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "play", "live", "capture", "inspect", "restore-view",
            "speed", "list", "info", "close", "reload"
    );

    private static final List<String> SPEEDS = List.of("0.25", "0.5", "1.0", "2.0");

    private final ChroniclePlugin plugin;
    private final ReplayEngine    replay;
    private final GlobalSnapshot  snapshot;

    public ChronicleCommand(ChroniclePlugin plugin) {
        this.plugin   = plugin;
        this.replay   = plugin.getReplayEngine();
        this.snapshot = new GlobalSnapshot(plugin);
    }



    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "play"         -> handlePlay(sender, args);
            case "live"         -> handleLive(sender, args);
            case "capture"      -> handleCapture(sender, args);
            case "inspect"      -> handleInspect(sender);
            case "restore-view" -> handleRestoreView(sender);
            case "speed"        -> handleSpeed(sender, args);
            case "list"         -> handleList(sender, args);
            case "info"         -> handleInfo(sender, args);
            case "close"        -> handleClose(sender, args);
            case "reload"       -> handleReload(sender);
            default             -> { sendHelp(sender); yield true; }
        };
    }


    private boolean handlePlay(CommandSender sender, String[] args) {
        Player admin = requirePlayer(sender);
        if (admin == null) return true;

        if (args.length < 2) {
            sender.sendMessage(err("Usage: /ch play <report-id>"));
            return true;
        }

        long id = parseLong(sender, args[1]);
        if (id < 0) return true;

        replay.startReplay(admin, id);
        return true;
    }


    private boolean handleLive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chronicle.live")) {
            sender.sendMessage(err("You don't have permission for live streaming."));
            return true;
        }
        Player admin = requirePlayer(sender);
        if (admin == null) return true;

        if (args.length < 2) {
            sender.sendMessage(err("Usage: /ch live <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(err("Player &e" + args[1] + " &cnot found online."));
            return true;
        }

        replay.startLive(admin, target);
        return true;
    }


    private boolean handleCapture(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chronicle.snapshot")) {
            sender.sendMessage(err("You don't have permission to trigger snapshots."));
            return true;
        }
        Player admin = requirePlayer(sender);
        if (admin == null) return true;


        String label = null;
        List<String> argList = Arrays.asList(args).subList(1, args.length);
        List<String> nonFlags = argList.stream()
                .filter(a -> !a.startsWith("--")).toList();
        if (!nonFlags.isEmpty()) {
            label = String.join(" ", nonFlags);
        }

        snapshot.capture(admin, label);
        return true;
    }


    private boolean handleInspect(CommandSender sender) {
        Player admin = requirePlayer(sender);
        if (admin == null) return true;

 
        boolean nowInspecting = plugin.getInspectManager().toggle(admin.getUniqueId());
        if (nowInspecting) {
            admin.sendMessage(info("&aInspect mode &2ON&a. Right-click a ghost block or entity to view NBT."));
        } else {
            admin.sendMessage(info("&7Inspect mode &cOFF&7."));
        }
        return true;
    }


    private boolean handleRestoreView(CommandSender sender) {
        Player admin = requirePlayer(sender);
        if (admin == null) return true;
        replay.restoreView(admin);
        return true;
    }


    private boolean handleSpeed(CommandSender sender, String[] args) {
        Player admin = requirePlayer(sender);
        if (admin == null) return true;

        if (args.length < 2) {
            sender.sendMessage(err("Usage: /ch speed <0.25|0.5|1.0|2.0>"));
            return true;
        }

        double speed;
        try {
            speed = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(err("Invalid speed: " + args[1]));
            return true;
        }

        if (speed != 0.25 && speed != 0.5 && speed != 1.0 && speed != 2.0) {
            sender.sendMessage(err("Speed must be one of: 0.25, 0.5, 1.0, 2.0"));
            return true;
        }

        replay.setSpeed(admin, speed);
        return true;
    }


    private boolean handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
        }
        final int finalPage = page;   
        int pageSize = 10;
        int offset   = (finalPage - 1) * pageSize;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var reports = plugin.getDatabaseManager().fetchOpenReports(pageSize + offset);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (reports.isEmpty()) {
                    sender.sendMessage(info("&7No open reports found."));
                    return;
                }
                sender.sendMessage(Component.text("═══ Chronicle — Open Reports (page " + finalPage + ") ═══",
                        NamedTextColor.GOLD, TextDecoration.BOLD));
                var pageReports = reports.stream().skip(offset).limit(pageSize).toList();
                for (var r : pageReports) {
                    sender.sendMessage(Component.text(
                            "  #" + r.getId() + " | " + r.getAccusedName()
                                    + " | " + r.getReason()
                                    + " | " + r.getCreatedAt(),
                            NamedTextColor.YELLOW));
                }
                sender.sendMessage(Component.text(
                        "Use /ch play <id> to review a report.", NamedTextColor.GRAY));
            });
        });
        return true;
    }


    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /ch info <report-id>"));
            return true;
        }
        long id = parseLong(sender, args[1]);
        if (id < 0) return true;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var opt = plugin.getDatabaseManager().fetchReport(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    sender.sendMessage(err("Report #" + id + " not found."));
                    return;
                }
                var r = opt.get();
                sender.sendMessage(Component.text("═══ Report #" + r.getId() + " ═══", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Accused : " + r.getAccusedName() + " (" + r.getAccusedUuid() + ")", NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Reporter: " + r.getReporterName(), NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Reason  : " + r.getReason(), NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  World   : " + r.getWorld()
                        + " @ " + String.format("%.0f,%.0f,%.0f", r.getPosX(), r.getPosY(), r.getPosZ()),
                        NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Status  : " + r.getStatus(), NamedTextColor.AQUA));
                sender.sendMessage(Component.text("  Filed   : " + r.getCreatedAt(), NamedTextColor.GRAY));
                int frames = r.hasTdfData() ? r.getTdfPackets().size() : 0;
                sender.sendMessage(Component.text("  TDF     : " + frames + " frames captured", NamedTextColor.GRAY));
            });
        });
        return true;
    }


    private boolean handleClose(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /ch close <report-id>"));
            return true;
        }
        long id = parseLong(sender, args[1]);
        if (id < 0) return true;

        UUID reviewerUuid = (sender instanceof Player p) ? p.getUniqueId() : null;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().updateReportStatus(id, "CLOSED", reviewerUuid);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(info("&aReport #" + id + " marked as &2CLOSED&a.")));
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(err("Failed to close report: " + e.getMessage())));
            }
        });
        return true;
    }


    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("chronicle.admin")) {
            sender.sendMessage(err("No permission."));
            return true;
        }
        plugin.reloadChronicleConfig();
        sender.sendMessage(info("&aChronicle configuration reloaded."));
        return true;
    }



    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══ Chronicle v2.0 Commands ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        String[][] cmds = {
            {"/ch play <id>",          "Replay a report's action sequence."},
            {"/ch live <player>",      "Live-stream a player's movements."},
            {"/ch capture [--global]", "Take a full server snapshot."},
            {"/ch inspect",            "Toggle NBT inspector in ghost reality."},
            {"/ch restore-view",       "Exit ghost reality and re-sync client."},
            {"/ch speed <0.25–2.0>",   "Change replay speed."},
            {"/ch list [page]",        "List open reports."},
            {"/ch info <id>",          "Show details for a report."},
            {"/ch close <id>",         "Mark a report as closed."},
            {"/ch reload",             "Reload config.yml."},
            {"/report <player> [reason]", "File a player report."},
        };
        for (String[] c : cmds) {
            sender.sendMessage(
                    Component.text("  " + c[0], NamedTextColor.YELLOW)
                             .append(Component.text(" — " + c[1], NamedTextColor.GRAY)));
        }
    }

 

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "live" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                            names.add(p.getName());
                    }
                    yield names;
                }
                case "speed"   -> SPEEDS.stream().filter(s -> s.startsWith(args[1])).toList();
                case "capture" -> List.of("--global");
                default        -> List.of();
            };
        }
        return List.of();
    }



    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(Component.text("[Chronicle] Console cannot use this command.", NamedTextColor.RED));
        return null;
    }

    private long parseLong(CommandSender sender, String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            sender.sendMessage(err("'" + s + "' is not a valid report ID."));
            return -1L;
        }
    }

    private Component err(String msg) {
        return Component.text("[Chronicle] " + msg
                .replace("&a", "").replace("&c", "").replace("&e", ""),
                NamedTextColor.RED);
    }

    private Component info(String legacyMsg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize("[Chronicle] " + legacyMsg);
    }
}
