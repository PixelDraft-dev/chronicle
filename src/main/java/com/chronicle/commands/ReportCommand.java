package com.chronicle.commands;

import com.chronicle.ChroniclePlugin;
import com.chronicle.buffer.TemporalDataFile;
import com.chronicle.database.DatabaseManager;
import com.chronicle.database.ReportRecord;
import com.chronicle.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;



public final class ReportCommand implements CommandExecutor, TabCompleter {

    private static final Logger LOG = Logger.getLogger("Chronicle.Report");

    private final ChroniclePlugin plugin;
    private final DatabaseManager db;

    public ReportCommand(ChroniclePlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
    }



    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(Component.text(
                    "[Chronicle] Only players can file reports.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            reporter.sendMessage(Component.text(
                    "Usage: /report <player> [reason...]", NamedTextColor.RED));
            return true;
        }


        String targetName = args[0];
        Player accused    = Bukkit.getPlayer(targetName);

        if (accused == null) {
            reporter.sendMessage(MessageUtil.colorize(
                    "&cPlayer &e" + targetName + " &cis not online."));
            return true;
        }

        if (accused.equals(reporter)) {
            reporter.sendMessage(MessageUtil.colorize("&cYou cannot report yourself."));
            return true;
        }

        String reason = args.length > 1
                ? String.join(" ", args).substring(targetName.length()).strip()
                : "No reason provided.";


        TemporalDataFile tdf = plugin.getBufferManager().flush(accused.getUniqueId());

        int ticksCaptured = (tdf != null) ? tdf.getPacketCount() : 0;


        final byte[] tdfBlob;
        if (tdf != null) {
            byte[] blob;
            try {
                blob = tdf.serialize();
            } catch (Exception e) {
                LOG.severe("Failed to serialize TDF for " + accused.getName() + ": " + e.getMessage());
                blob = null;
            }
            tdfBlob = blob;
        } else {
            tdfBlob = null;
        }

        final UUID   accusedUuidFinal  = accused.getUniqueId();
        final String accusedNameFinal  = accused.getName();
        final String reporterNameFinal = reporter.getName();
        final UUID   reporterUuidFinal = reporter.getUniqueId();

        final double px = accused.getX();
        final double py = accused.getY();
        final double pz = accused.getZ();
        final String world = accused.getWorld().getName();
        final int    finalTicks = ticksCaptured;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ReportRecord record = ReportRecord.create(
                        accusedUuidFinal, accusedNameFinal,
                        reporterUuidFinal, reporterNameFinal,
                        reason, world, px, py, pz, tdfBlob);

                long reportId = db.saveReport(record);


                plugin.getServer().getScheduler().runTask(plugin, () -> {

                    reporter.sendMessage(MessageUtil.format(
                            plugin.getChronicleConfig().getMsgReportFiled(),
                            Map.of("id", String.valueOf(reportId))));

                    if (finalTicks > 0) {
                        reporter.sendMessage(MessageUtil.format(
                                plugin.getChronicleConfig().getMsgBufferFlushed(),
                                Map.of("ticks", String.valueOf(finalTicks))));
                    }


                    String notifyRaw = plugin.getChronicleConfig().getMsgReportNotify();
                    Component notifyMsg = MessageUtil.format(notifyRaw, Map.of(
                            "reporter", reporterNameFinal,
                            "accused",  accusedNameFinal,
                            "id",       String.valueOf(reportId)
                    ));
                    for (Player admin : Bukkit.getOnlinePlayers()) {
                        if (admin.hasPermission("chronicle.admin")) {
                            admin.sendMessage(notifyMsg);
                        }
                    }

                    LOG.info("Report #" + reportId + " filed: " + reporterNameFinal
                            + " → " + accusedNameFinal + " [" + reason + "] ticks=" + finalTicks);
                });

            } catch (Exception e) {
                LOG.severe("Failed to save report: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        reporter.sendMessage(Component.text(
                                "[Chronicle] Report could not be saved: " + e.getMessage(),
                                NamedTextColor.RED)));
            }
        });

        return true;
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        return List.of();
    }
}
