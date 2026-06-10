package dev.maghreb.parkour.commands;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.managers.CourseManager;
import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.PlayerRecord;
import dev.maghreb.parkour.utils.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /parkour subcommands with strict argument validation.
 * Uses only plain Bukkit ChatColor — no Adventure API — to avoid
 * classloading conflicts with the server's bundled Adventure version.
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
            plugin.getLogger().warning("Unhandled exception in /parkour: " + e.getMessage());
            MessageUtil.send(sender, PREFIX + "&cAn internal error occurred. Check console.");
            return true;
        }
    }

    private boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create"               -> handleCreate(sender, args);
            case "setstart"             -> handleSetStart(sender, args);
            case "setfinish"            -> handleSetFinish(sender, args);
            case "addcheckpoint"        -> handleAddCheckpoint(sender, args);
            case "leaderboard", "lb"    -> handleLeaderboard(sender, args);
            case "list"                 -> handleList(sender);
            case "reset"                -> handleReset(sender, args);
            case "reload"               -> handleReload(sender);
            case "help"                 -> sendHelp(sender);
            default -> MessageUtil.send(sender, PREFIX + "&cUnknown subcommand. Use &e/parkour help&c.");
        }
        return true;
    }

    // ── /parkour create <name> ─────────────────────────────────────────────

    private void handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour create <name>"); return; }

        String name = args[1];
        if (!isValidName(name)) {
            MessageUtil.send(sender, PREFIX + "&cName may only contain letters, numbers, and underscores.");
            return;
        }
        if (courseManager.courseExists(name)) {
            MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &calready exists.");
            return;
        }
        courseManager.createCourse(name);
        MessageUtil.send(sender, PREFIX + "&aCourse &e" + name + " &acreated. Now run &e/parkour setstart " + name + "&a.");
    }

    // ── /parkour setstart <name> ───────────────────────────────────────────

    private void handleSetStart(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour setstart <name>"); return; }

        String name = args[1];
        if (!courseManager.courseExists(name)) { MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found."); return; }
        courseManager.setStart(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aStart pad set for &e" + name + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour setfinish <name> ──────────────────────────────────────────

    private void handleSetFinish(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour setfinish <name>"); return; }

        String name = args[1];
        if (!courseManager.courseExists(name)) { MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found."); return; }
        courseManager.setFinish(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aFinish pad set for &e" + name + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour addcheckpoint <name> ─────────────────────────────────────

    private void handleAddCheckpoint(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour addcheckpoint <name>"); return; }

        String name = args[1];
        if (!courseManager.courseExists(name)) { MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found."); return; }
        int order = courseManager.addCheckpoint(name, player.getLocation());
        MessageUtil.send(sender, PREFIX + "&aCheckpoint &e#" + order + " &aadded to &e" + name + " &aat &7" + formatLoc(player.getLocation()));
    }

    // ── /parkour leaderboard <name> ────────────────────────────────────────

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 2) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour leaderboard <course>"); return; }

        String name = args[1];
        if (!courseManager.courseExists(name)) { MessageUtil.send(sender, PREFIX + "&cCourse &e" + name + " &cnot found."); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerRecord> records = db.getTopRecords(name, 3);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String line = ChatColor.DARK_GRAY + "-----------------------------";
                sender.sendMessage(line);
                sender.sendMessage(MessageUtil.colorize("  &6&l» Leaderboard &e" + name));
                sender.sendMessage(line);
                if (records.isEmpty()) {
                    sender.sendMessage(MessageUtil.colorize("  &7No completions yet."));
                } else {
                    String[] medals = {"&6[1]", "&7[2]", "&c[3]"};
                    for (int i = 0; i < records.size(); i++) {
                        PlayerRecord r = records.get(i);
                        String medal = i < medals.length ? medals[i] : "&7[" + (i + 1) + "]";
                        sender.sendMessage(MessageUtil.colorize(
                                "  " + medal + " &b" + r.getPlayerName() + " &8- &a" + r.getFormattedTime()));
                    }
                }
                sender.sendMessage(line);
            });
        });
    }

    // ── /parkour list ──────────────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        Collection<?> courses = courseManager.getAllCourses();
        if (courses.isEmpty()) { MessageUtil.send(sender, PREFIX + "&7No courses configured yet."); return; }
        MessageUtil.send(sender, PREFIX + "&aCourses &7(" + courses.size() + "):");
        courseManager.getAllCourses().forEach(c -> {
            String status = c.isFullyConfigured() ? "&a✔" : "&e⚠";
            sender.sendMessage(MessageUtil.colorize("  " + status + " &e" + c.getName()
                    + " &7- " + c.getCheckpoints().size() + " checkpoint(s)"));
        });
    }

    // ── /parkour reset [player] ────────────────────────────────────────────

    private void handleReset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            if (!(sender instanceof Player player)) { MessageUtil.send(sender, PREFIX + "&cUsage: &e/parkour reset <player>"); return; }
            if (runManager.isRunning(player.getUniqueId())) {
                runManager.invalidateRun(player, ActiveRun.InvalidationReason.ADMIN_RESET);
                MessageUtil.send(sender, PREFIX + "&aYour run has been reset.");
            } else { MessageUtil.send(sender, PREFIX + "&7You have no active run."); }
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { MessageUtil.send(sender, PREFIX + "&cPlayer &e" + args[1] + " &cnot found."); return; }
        if (runManager.isRunning(target.getUniqueId())) {
            runManager.invalidateRun(target, ActiveRun.InvalidationReason.ADMIN_RESET);
            MessageUtil.send(sender, PREFIX + "&aReset &e" + target.getName() + "&a's run.");
        } else { MessageUtil.send(sender, PREFIX + "&e" + target.getName() + " &7has no active run."); }
    }

    // ── /parkour reload ────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        plugin.reloadConfig();
        plugin.getCourseStorage().load();
        MessageUtil.send(sender, PREFIX + "&aConfig and courses reloaded.");
    }

    // ── Help ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        String line = ChatColor.DARK_GRAY + "-----------------------------";
        sender.sendMessage(line);
        sender.sendMessage(MessageUtil.colorize("  &b&lParkourEngine &7Commands"));
        sender.sendMessage(line);
        helpLine(sender, "/parkour create <name>",        "Create a new course");
        helpLine(sender, "/parkour setstart <name>",      "Set start pad at your location");
        helpLine(sender, "/parkour setfinish <name>",     "Set finish pad at your location");
        helpLine(sender, "/parkour addcheckpoint <name>", "Add next checkpoint at your location");
        helpLine(sender, "/parkour leaderboard <name>",   "Show top 3 times");
        helpLine(sender, "/parkour list",                 "List all courses");
        helpLine(sender, "/parkour reset [player]",       "Force-reset a run");
        helpLine(sender, "/parkour reload",               "Reload config & courses");
        sender.sendMessage(line);
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(MessageUtil.colorize("  &e" + cmd + " &8- &7" + desc));
    }

    // ── Tab Completion ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create","setstart","setfinish","addcheckpoint",
                    "leaderboard","list","reset","reload","help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Set.of("setstart","setfinish","addcheckpoint","leaderboard","lb").contains(sub))
                return filter(getCourseNames(), args[1]);
            if (sub.equals("reset")) return null;
        }
        return Collections.emptyList();
    }

    // ── Guards & Helpers ───────────────────────────────────────────────────

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(PERM_ADMIN)) return true;
        MessageUtil.send(sender, PREFIX + "&cNo permission.");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        MessageUtil.send(sender, PREFIX + "&cThis command requires a player.");
        return null;
    }

    private boolean isValidName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]{1,32}");
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private List<String> getCourseNames() {
        return courseManager.getAllCourses().stream().map(c -> c.getName()).collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
