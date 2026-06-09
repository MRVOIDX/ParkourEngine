package dev.maghreb.parkour.commands;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.managers.CourseManager;
import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.PlayerRecord;
import dev.maghreb.parkour.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /parkour subcommands with strict argument validation
 * and defensive exception handling throughout.
 *
 * Subcommands:
 *   /parkour create <name>          — Admin: create a new course
 *   /parkour setstart <name>        — Admin: set start pad at current location
 *   /parkour setfinish <name>       — Admin: set finish pad at current location
 *   /parkour addcheckpoint <name>   — Admin: add next checkpoint at current location
 *   /parkour leaderboard <name>     — All: display top 3 times for a course
 *   /parkour list                   — All: list all courses
 *   /parkour reset <name>           — Admin: force-reset a player's run (optional)
 *   /parkour reload                 — Admin: reload config
 */
public class ParkourCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&bParkour&8] &r";
    private static final String PERM_ADMIN = "parkour.admin";

    private final ParkourPlugin plugin;
    private final CourseManager courseManager;
    private final RunManager runManager;
    private final DatabaseManager db;

    public ParkourCommand(ParkourPlugin plugin, CourseManager courseManager,
                          RunManager runManager, DatabaseManager db) {
        this.plugin = plugin;
        this.courseManager = courseManager;
        this.runManager = runManager;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return dispatch(sender, args);
        } catch (Exception e) {
            plugin.getLogger().warning("Unhandled exception in /parkour command: " + e.getMessage());
            MessageUtil.send(sender, PREFIX + "&cAn internal error occurred. Check console.");
            return true;
        }
    }

    private boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create"         -> handleCreate(sender, args);
            case "setstart"       -> handleSetStart(sender, args);
            case "setfinish"      -> handleSetFinish(sender, args);
            case "addcheckpoint"  -> handleAddCheckpoint(sender, args);
            case "leaderboard", "lb" -> handleLeaderboard(sender, args);
            case "list"           -> handleList(sender);
            case "reset"          -> handleReset(sender, args);
            case "reload"         -> handleReload(sender);
            case "help"           -> sendHelp(sender);
            default               -> {
                MessageUtil.send(sender, PREFIX + "&cUnknown subcommand. Use &e/parkour help&c.");
            }
        }
        return true;
    }

    // ── /parkour create <name> ─────────────────────────────────────────────

    private void handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour create <name>");
            return;
        }

        String name = args[1];
        if (!isValidName(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse name may only contain letters, numbers, and underscores.");
            return;
        }

        if (courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cA course named &e" + name + " &calready exists.");
            return;
        }

        courseManager.createCourse(name);
        MessageUtil.send(sender, PREFIX + "&aCourse &e" + name + " &acreated. Now use &e/parkour setstart " + name + "&a.");
    }

    // ── /parkour setstart <name> ───────────────────────────────────────────

    private void handleSetStart(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour setstart <name>");
            return;
        }

        String name = args[1];
        if (!courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found. Create it first.");
            return;
        }

        courseManager.setStart(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aStart pad registered for &e" + name
                + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour setfinish <name> ──────────────────────────────────────────

    private void handleSetFinish(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour setfinish <name>");
            return;
        }

        String name = args[1];
        if (!courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found.");
            return;
        }

        courseManager.setFinish(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aFinish pad registered for &e" + name
                + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour addcheckpoint <name> ─────────────────────────────────────

    private void handleAddCheckpoint(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour addcheckpoint <name>");
            return;
        }

        String name = args[1];
        if (!courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found.");
            return;
        }

        int order = courseManager.addCheckpoint(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aCheckpoint &e#" + order
                + " &aadded to &e" + name + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour leaderboard <name> ────────────────────────────────────────

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour leaderboard <course>");
            return;
        }

        String name = args[1];
        if (!courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found.");
            return;
        }

        // Query asynchronously, render on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerRecord> records = db.getTopRecords(name, 3);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Header
                sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));
                sender.sendMessage(
                        Component.text("  🏆 Leaderboard — ")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD)
                                .append(Component.text(name).color(NamedTextColor.YELLOW))
                );
                sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));

                if (records.isEmpty()) {
                    sender.sendMessage(Component.text("  No completions recorded yet.")
                            .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
                } else {
                    String[] medals = {"🥇", "🥈", "🥉"};
                    for (int i = 0; i < records.size(); i++) {
                        PlayerRecord rec = records.get(i);
                        String medal = i < medals.length ? medals[i] : (i + 1) + ".";
                        sender.sendMessage(
                                Component.text("  " + medal + " ")
                                        .color(NamedTextColor.WHITE)
                                        .append(Component.text(rec.getPlayerName())
                                                .color(NamedTextColor.AQUA)
                                                .decorate(TextDecoration.BOLD))
                                        .append(Component.text("  —  ").color(NamedTextColor.DARK_GRAY))
                                        .append(Component.text(rec.getFormattedTime())
                                                .color(NamedTextColor.GREEN)
                                                .decorate(TextDecoration.BOLD))
                        );
                    }
                }
                sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));
            });
        });
    }

    // ── /parkour list ──────────────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        Collection<?> courses = courseManager.getAllCourses();
        if (courses.isEmpty()) {
            MessageUtil.send(sender, PREFIX + "&7No courses configured yet.");
            return;
        }

        sender.sendMessage(MessageUtil.colorize(PREFIX + "&aCourses &7(" + courses.size() + "):"));
        courseManager.getAllCourses().forEach(c -> {
            String status = c.isFullyConfigured() ? "&a✔" : "&e⚠";
            int cpCount = c.getCheckpoints().size();
            sender.sendMessage(MessageUtil.colorize("  " + status + " &e" + c.getName()
                    + " &7— " + cpCount + " checkpoint(s)"));
        });
    }

    // ── /parkour reset [player] ────────────────────────────────────────────

    private void handleReset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;

        if (args.length < 2) {
            // Reset self
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour reset <player>");
                return;
            }
            if (runManager.isRunning(player.getUniqueId())) {
                runManager.invalidateRun(player, ActiveRun.InvalidationReason.ADMIN_RESET);
                MessageUtil.send(sender, PREFIX + "&aYour run has been reset.");
            } else {
                MessageUtil.send(sender, PREFIX + "&7You have no active run.");
            }
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            MessageUtil.send(sender, PREFIX + "&cPlayer &e" + args[1] + " &cnot found.");
            return;
        }

        if (runManager.isRunning(target.getUniqueId())) {
            runManager.invalidateRun(target, ActiveRun.InvalidationReason.ADMIN_RESET);
            MessageUtil.send(sender, PREFIX + "&aReset &e" + target.getName() + "&a's run.");
        } else {
            MessageUtil.send(sender, PREFIX + "&e" + target.getName() + " &7has no active run.");
        }
    }

    // ── /parkour reload ────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        plugin.reloadConfig();
        plugin.getCourseStorage().load();
        MessageUtil.send(sender, PREFIX + "&aConfiguration and courses reloaded.");
    }

    // ── Help ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));
        sender.sendMessage(Component.text("  ParkourEngine Commands").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));
        helpLine(sender, "/parkour create <name>",         "Create a new course");
        helpLine(sender, "/parkour setstart <name>",       "Set start pad at your location");
        helpLine(sender, "/parkour setfinish <name>",      "Set finish pad at your location");
        helpLine(sender, "/parkour addcheckpoint <name>",  "Add checkpoint at your location");
        helpLine(sender, "/parkour leaderboard <name>",    "Show top 3 times for a course");
        helpLine(sender, "/parkour list",                  "List all courses");
        helpLine(sender, "/parkour reset [player]",        "Force-reset a run");
        helpLine(sender, "/parkour reload",                "Reload config");
        sender.sendMessage(Component.text("─────────────────────────────────").color(TextColor.color(0x555555)));
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(
                Component.text("  ").color(NamedTextColor.WHITE)
                        .append(Component.text(cmd).color(NamedTextColor.YELLOW))
                        .append(Component.text(" — ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(desc).color(NamedTextColor.GRAY))
        );
    }

    // ── Tab Completion ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "create", "setstart", "setfinish", "addcheckpoint",
                    "leaderboard", "list", "reset", "reload", "help"
            ));
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Set.of("setstart", "setfinish", "addcheckpoint", "leaderboard", "lb").contains(sub)) {
                return filter(getCourseNames(), args[1]);
            }
            if (sub.equals("reset")) {
                return null; // Let Bukkit suggest online players
            }
        }
        return Collections.emptyList();
    }

    // ── Guards & Helpers ───────────────────────────────────────────────────

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(PERM_ADMIN)) return true;
        MessageUtil.send(sender, PREFIX + "&cYou don't have permission to do that.");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        MessageUtil.send(sender, PREFIX + "&cThis command must be run by a player.");
        return null;
    }

    private boolean isValidName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]{1,32}");
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private List<String> getCourseNames() {
        return courseManager.getAllCourses().stream()
                .map(c -> c.getName())
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
