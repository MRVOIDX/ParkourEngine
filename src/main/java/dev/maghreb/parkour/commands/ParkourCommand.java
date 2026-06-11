package dev.maghreb.parkour.commands;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.managers.CourseManager;
import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.PlayerRecord;
import dev.maghreb.parkour.utils.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&bParkour&8] &r";
    private static final String ADMIN  = "parkour.admin";

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
            plugin.getLogger().warning("Command error: " + e.getMessage());
            msg(sender, "&cSomething went wrong. Check the console.");
            return true;
        }
    }

    private boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "create"            -> handleCreate(sender, args);
            case "setstart"          -> handleSetStart(sender, args);
            case "setfinish"         -> handleSetFinish(sender, args);
            case "addcheckpoint"     -> handleAddCheckpoint(sender, args);
            case "leaderboard", "lb" -> handleLeaderboard(sender, args);
            case "list"              -> handleList(sender);
            case "stats"             -> handleStats(sender, args);
            case "reset"             -> handleReset(sender, args);
            case "delete"            -> handleDelete(sender, args);
            case "deleterecord"      -> handleDeleteRecord(sender, args);
            case "setfaily"          -> handleSetFailY(sender, args);
            case "reload"            -> handleReload(sender);
            case "help"              -> sendHelp(sender);
            default                  -> msg(sender, "&cUnknown subcommand. &7Try &e/parkour help&7.");
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour create <name>"); return; }
        String name = args[1];
        if (!validName(name)) { msg(sender, "&cName must be 1-32 alphanumeric characters or underscores."); return; }
        if (courseManager.courseExists(name)) { msg(sender, "&cA course named &e" + name + " &calready exists."); return; }
        courseManager.createCourse(name);
        msg(sender, "&aCourse &e" + name + " &acreated. Set start with &e/parkour setstart " + name + "&a.");
    }

    private void handleSetStart(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour setstart <name>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }
        courseManager.setStart(name, p.getLocation());
        msg(sender, "&aStart pad set for &e" + name + " &aat &7" + loc(p.getLocation()));
    }

    private void handleSetFinish(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour setfinish <name>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }
        courseManager.setFinish(name, p.getLocation());
        msg(sender, "&aFinish pad set for &e" + name + " &aat &7" + loc(p.getLocation()));
    }

    private void handleAddCheckpoint(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour addcheckpoint <name>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }
        int order = courseManager.addCheckpoint(name, p.getLocation());
        msg(sender, "&aCheckpoint &e#" + order + " &aadded to &e" + name + " &aat &7" + loc(p.getLocation()));
    }

    private void handleSetFailY(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) { msg(sender, "&cUsage: &e/parkour setfaily <name> <y-level>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }
        try {
            double y = Double.parseDouble(args[2]);
            courseManager.setFailY(name, y);
            msg(sender, "&aFail Y-level for &e" + name + " &aset to &e" + y);
        } catch (NumberFormatException e) {
            msg(sender, "&c\"" + args[2] + "\" is not a valid number.");
        }
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour leaderboard <course>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerRecord> records = db.getTopRecords(name, 10);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String line = ChatColor.DARK_GRAY + "  " + ChatColor.STRIKETHROUGH
                        + "                              ";
                sender.sendMessage(line);
                sender.sendMessage(MessageUtil.colorize(
                        "    &b&lLeaderboard &8\u00bb &e" + name));
                sender.sendMessage(line);
                if (records.isEmpty()) {
                    sender.sendMessage(MessageUtil.colorize("    &7No completions recorded yet."));
                } else {
                    String[] medals = {"&6[1]", "&7[2]", "&c[3]"};
                    for (int i = 0; i < records.size(); i++) {
                        PlayerRecord r = records.get(i);
                        String rank = i < medals.length ? medals[i] : "&8[" + (i + 1) + "]";
                        sender.sendMessage(MessageUtil.colorize(
                                "  " + rank + " &b" + r.getPlayerName()
                                + " &8\u2014 &a" + r.getFormattedTime()));
                    }
                }
                sender.sendMessage(line);
            });
        });
    }

    private void handleList(CommandSender sender) {
        Collection<?> courses = courseManager.getAllCourses();
        if (courses.isEmpty()) { msg(sender, "&7No courses configured yet."); return; }
        msg(sender, "&7Courses &8(" + courses.size() + ")&7:");
        courseManager.getAllCourses().forEach(c -> {
            String ready = c.isFullyConfigured() ? "&a\u2714" : "&e\u26a0";
            msg(sender, "  " + ready + " &e" + c.getName()
                    + " &8\u00bb &7" + c.getCheckpoints().size() + " checkpoint(s)");
        });
    }

    private void handleStats(CommandSender sender, String[] args) {
        String targetName;
        UUID targetUUID;

        if (args.length >= 2) {
            if (!admin(sender)) return;
            OfflinePlayer offline = plugin.getServer().getOfflinePlayer(args[1]);
            targetName = offline.getName() != null ? offline.getName() : args[1];
            targetUUID = offline.getUniqueId();
        } else {
            Player p = player(sender); if (p == null) return;
            targetName = p.getName();
            targetUUID = p.getUniqueId();
        }

        String courseName = args.length >= 3 ? args[2] : null;
        final String finalName = targetName;
        final UUID finalUUID = targetUUID;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String line = ChatColor.DARK_GRAY + "  " + ChatColor.STRIKETHROUGH + "                              ";
                sender.sendMessage(line);
                sender.sendMessage(MessageUtil.colorize("    &b&lStats &8\u00bb &e" + finalName));
                sender.sendMessage(line);

                List<String> courses = courseName != null
                        ? List.of(courseName)
                        : courseManager.getAllCourses().stream()
                                .map(c -> c.getName()).collect(Collectors.toList());

                if (courses.isEmpty()) {
                    sender.sendMessage(MessageUtil.colorize("    &7No courses to display."));
                } else {
                    for (String cn : courses) {
                        long pb = db.getPersonalBest(finalUUID, cn);
                        String pbStr = pb < 0 ? "&8-" : "&a" + ActiveRun.formatTime(pb);
                        sender.sendMessage(MessageUtil.colorize("  &e" + cn + " &8\u2014 " + pbStr));
                    }
                }
                sender.sendMessage(line);
            });
        });
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Player p = player(sender); if (p == null) return;
            if (runManager.isRunning(p.getUniqueId())) {
                runManager.invalidateRun(p, ActiveRun.InvalidationReason.ADMIN_RESET);
                msg(sender, "&aYour run was reset.");
            } else {
                msg(sender, "&7You have no active run.");
            }
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { msg(sender, "&cPlayer &e" + args[1] + " &cnot found or offline."); return; }
        if (runManager.isRunning(target.getUniqueId())) {
            runManager.invalidateRun(target, ActiveRun.InvalidationReason.ADMIN_RESET);
            msg(sender, "&aReset &e" + target.getName() + "&a's run.");
        } else {
            msg(sender, "&e" + target.getName() + " &7has no active run.");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) { msg(sender, "&cUsage: &e/parkour delete <name>"); return; }
        String name = args[1];
        if (!courseManager.courseExists(name)) { msg(sender, "&cCourse &e" + name + " &cnot found."); return; }
        courseManager.deleteCourse(name);
        msg(sender, "&aCourse &e" + name + " &adeleted.");
    }

    private void handleDeleteRecord(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) { msg(sender, "&cUsage: &e/parkour deleterecord <player> <course>"); return; }
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        String course = args[2];
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = db.deleteRecord(target.getUniqueId(), course);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (deleted) msg(sender, "&aDeleted record for &e" + args[1] + " &aon &e" + course + "&a.");
                else         msg(sender, "&7No record found for &e" + args[1] + " &7on &e" + course + "&7.");
            });
        });
    }

    private void handleReload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadConfig();
        plugin.getCourseStorage().load();
        msg(sender, "&aConfig and courses reloaded.");
    }

    private void sendHelp(CommandSender sender) {
        String line = ChatColor.DARK_GRAY + "  " + ChatColor.STRIKETHROUGH + "                              ";
        sender.sendMessage(line);
        sender.sendMessage(MessageUtil.colorize("    &b&lParkourEngine &8\u00bb &7Commands"));
        sender.sendMessage(line);
        line(sender, "/parkour create <name>",           "Create a new course");
        line(sender, "/parkour setstart <name>",         "Set start pad at your position");
        line(sender, "/parkour setfinish <name>",        "Set finish pad at your position");
        line(sender, "/parkour addcheckpoint <name>",    "Add checkpoint at your position");
        line(sender, "/parkour setfaily <name> <y>",     "Set the fail Y-level");
        line(sender, "/parkour leaderboard <name>",      "Top times for a course");
        line(sender, "/parkour stats [player] [course]", "View personal bests");
        line(sender, "/parkour list",                    "List all courses");
        line(sender, "/parkour reset [player]",          "Cancel an active run");
        line(sender, "/parkour delete <name>",           "Delete a course");
        line(sender, "/parkour deleterecord <p> <c>",    "Delete a player record");
        line(sender, "/parkour reload",                  "Reload config and courses");
        sender.sendMessage(line);
    }

    private void line(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(MessageUtil.colorize("  &e" + cmd + " &8\u2014 &7" + desc));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create","setstart","setfinish","addcheckpoint","setfaily",
                    "leaderboard","list","stats","reset","delete","deleterecord","reload","help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Set.of("setstart","setfinish","addcheckpoint","leaderboard","lb","delete","setfaily").contains(sub))
                return filter(courseNames(), args[1]);
            if (Set.of("reset","stats","deleterecord").contains(sub))
                return null;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("deleterecord"))
            return filter(courseNames(), args[2]);
        return Collections.emptyList();
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        msg(sender, "&cYou don't have permission to do that.");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        msg(sender, "&cThis command can only be used in-game.");
        return null;
    }

    private boolean validName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]{1,32}");
    }

    private String loc(org.bukkit.Location l) {
        return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
    }

    private List<String> courseNames() {
        return courseManager.getAllCourses().stream().map(c -> c.getName()).collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        String lo = prefix.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(lo)).collect(Collectors.toList());
    }

    private void msg(CommandSender sender, String message) {
        MessageUtil.send(sender, PREFIX + message);
    }
}
