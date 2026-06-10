package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import dev.maghreb.parkour.utils.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core state machine managing all active parkour runs.
 * Uses only Bukkit ChatColor / sendMessage — no Adventure API.
 */
public class RunManager {

    private final ParkourPlugin plugin;
    private final DatabaseManager db;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private BukkitTask actionBarTask;

    public RunManager(ParkourPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ── Run Lifecycle ──────────────────────────────────────────────────────

    public void startRun(Player player, ParkourCourse course) {
        Location startLoc = course.getStartLocation().toBukkitLocation();
        if (startLoc == null) return;
        activeRuns.remove(player.getUniqueId());
        ActiveRun run = new ActiveRun(player.getUniqueId(), course.getName(), startLoc);
        activeRuns.put(player.getUniqueId(), run);
        player.sendMessage(c("&bRun started on &e" + course.getName() + "&b! Good luck!"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }

    public void onCheckpointReached(Player player, ParkourCourse course, ParkourCheckpoint checkpoint) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        if (!run.getCourseName().equals(course.getName())) return;
        if (checkpoint.getOrder() > run.getLastCheckpointIndex()) {
            run.setLastCheckpointIndex(checkpoint.getOrder());
            Location cpLoc = checkpoint.toSerializableLocation().toBukkitLocation();
            if (cpLoc != null) run.setLastCheckpointLocation(cpLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            player.sendMessage(c("&7Checkpoint &e#" + checkpoint.getOrder() + " &7reached."));
        }
    }

    public void completeRun(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.remove(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        if (!run.getCourseName().equals(course.getName())) return;
        run.setActive(false);
        long timeMs = run.getElapsedMillis();
        String formatted = ActiveRun.formatTime(timeMs);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(c("&aRun complete! Time: &e" + formatted));
        player.sendTitle("", c("&aFinished! &e" + formatted), 5, 40, 20);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long pb = db.getPersonalBest(player.getUniqueId(), course.getName());
            db.saveRecordIfBetter(player.getUniqueId(), player.getName(), course.getName(), timeMs);
            if (pb < 0 || timeMs < pb) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(c("&6★ New personal record! &e" + formatted + " &6on &e" + course.getName()));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
                });
            }
        });
    }

    public void repositionToCheckpoint(Player player) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        Location dest = run.getLastCheckpointLocation();
        if (dest == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(dest);
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0, 0));
        });
    }

    public void invalidateRun(Player player, ActiveRun.InvalidationReason reason) {
        ActiveRun run = activeRuns.remove(player.getUniqueId());
        if (run == null) return;
        run.setActive(false);
        player.sendMessage(c("&cRun invalidated: &e" + reason.getDisplay()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    public void invalidateAllRuns(String reason) {
        for (UUID uuid : new HashSet<>(activeRuns.keySet())) {
            ActiveRun run = activeRuns.remove(uuid);
            if (run != null) run.setActive(false);
        }
    }

    // ── Fail Conditions ────────────────────────────────────────────────────

    public void checkFailConditions(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        Location loc = player.getLocation();
        if (loc.getY() < course.getFailYLevel()) {
            repositionToCheckpoint(player);
            return;
        }
        org.bukkit.Material mat = loc.getBlock().getType();
        if (mat == org.bukkit.Material.WATER || mat == org.bukkit.Material.LAVA
                || mat == org.bukkit.Material.BUBBLE_COLUMN) {
            repositionToCheckpoint(player);
        }
    }

    // ── Action Bar ─────────────────────────────────────────────────────────

    public void startActionBarTask() {
        int interval = plugin.getConfig().getInt("settings.actionbar-interval", 2);
        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ActiveRun> entry : activeRuns.entrySet()) {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    if (p == null || !p.isOnline()) continue;
                    ActiveRun run = entry.getValue();
                    if (!run.isActive()) continue;
                    // sendActionBar via Spigot API — available on all Paper 1.21.x
                    p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                            ChatColor.AQUA + "⏱ " + ChatColor.WHITE + ChatColor.BOLD
                            + run.getFormattedTime()
                            + ChatColor.RESET + ChatColor.GRAY + "  [" + run.getCourseName() + "]"
                        )
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    public void stopActionBarTask() {
        if (actionBarTask != null) actionBarTask.cancel();
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public boolean isRunning(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);
        return run != null && run.isActive();
    }

    public Optional<ActiveRun> getActiveRun(UUID uuid) {
        return Optional.ofNullable(activeRuns.get(uuid));
    }

    public Map<UUID, ActiveRun> getActiveRuns() {
        return Collections.unmodifiableMap(activeRuns);
    }

    private String c(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
