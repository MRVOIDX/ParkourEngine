package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core state machine managing the lifecycle of all active parkour runs.
 *
 * Responsibilities:
 *  - Run initialization when player hits start pad
 *  - Checkpoint progression tracking
 *  - Failure detection (Y-level, illegal materials) → repositioning
 *  - Run completion with async DB write
 *  - Anti-exploit run invalidation
 *  - Action bar timer streaming
 */
public class RunManager {

    private final ParkourPlugin plugin;
    private final DatabaseManager db;

    /** UUID → ActiveRun map. ConcurrentHashMap ensures safe cross-thread access. */
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    private BukkitTask actionBarTask;

    // Particle-effect radius threshold for start/finish detection
    private static final double START_RADIUS = 1.5;
    private static final double FINISH_RADIUS = 1.5;
    private static final double CHECKPOINT_RADIUS = 1.5;

    public RunManager(ParkourPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ── Run Lifecycle ──────────────────────────────────────────────────────

    /**
     * Starts a run for the player on the given course.
     * If the player already has an active run, it is silently cancelled first.
     */
    public void startRun(Player player, ParkourCourse course) {
        Location startLoc = course.getStartLocation().toBukkitLocation();
        if (startLoc == null) return;

        // Cancel any existing run without broadcasting
        activeRuns.remove(player.getUniqueId());

        ActiveRun run = new ActiveRun(player.getUniqueId(), course.getName(), startLoc);
        activeRuns.put(player.getUniqueId(), run);

        player.sendMessage(msg("&bRun started on &e" + course.getName() + "&b! Good luck!"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }

    /**
     * Called when a player steps onto a checkpoint block.
     * Only advances if the checkpoint index is higher than the last reached.
     */
    public void onCheckpointReached(Player player, ParkourCourse course, ParkourCheckpoint checkpoint) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        if (!run.getCourseName().equals(course.getName())) return;

        if (checkpoint.getOrder() > run.getLastCheckpointIndex()) {
            run.setLastCheckpointIndex(checkpoint.getOrder());
            Location cpLoc = checkpoint.toSerializableLocation().toBukkitLocation();
            if (cpLoc != null) run.setLastCheckpointLocation(cpLoc);

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            player.sendMessage(msg("&7Checkpoint &e#" + checkpoint.getOrder() + " &7reached."));
        }
    }

    /**
     * Completes the run — halts the timer, optionally writes new PB to DB.
     */
    public void completeRun(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.remove(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        if (!run.getCourseName().equals(course.getName())) return;

        run.setActive(false);
        long timeMs = run.getElapsedMillis();
        String formatted = ActiveRun.formatTime(timeMs);

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(msg("&aRun complete! Time: &e" + formatted));

        // Clear action bar
        player.sendActionBar(Component.empty());

        // Non-blocking background write
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long pb = db.getPersonalBest(player.getUniqueId(), course.getName());
            db.saveRecordIfBetter(player.getUniqueId(), player.getName(), course.getName(), timeMs);

            if (pb < 0 || timeMs < pb) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(msg("&6★ New personal record! &e" + formatted
                            + " &6on &e" + course.getName() + "&6!"));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
                });
            }
        });
    }

    /**
     * Silently repositions the player back to their last checkpoint.
     * Timer is NOT reset.
     */
    public void repositionToCheckpoint(Player player) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;

        Location dest = run.getLastCheckpointLocation();
        if (dest == null) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(dest);
            player.setFallDistance(0f);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        });
    }

    /**
     * Immediately invalidates a player's active run.
     */
    public void invalidateRun(Player player, ActiveRun.InvalidationReason reason) {
        ActiveRun run = activeRuns.remove(player.getUniqueId());
        if (run == null) return;
        run.setActive(false);

        player.sendActionBar(Component.empty());
        player.sendMessage(msg("&cRun invalidated: &e" + reason.getDisplay()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /** Server shutdown — invalidate all silently. */
    public void invalidateAllRuns(String reason) {
        for (UUID uuid : activeRuns.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            ActiveRun run = activeRuns.remove(uuid);
            if (run != null) run.setActive(false);
            if (p != null) p.sendActionBar(Component.empty());
        }
    }

    // ── Failure Detection ──────────────────────────────────────────────────

    /**
     * Called every tick (via PlayerMoveListener) to check Y-level and material failures.
     */
    public void checkFailConditions(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;

        Location loc = player.getLocation();

        // Y-level check
        if (loc.getY() < course.getFailYLevel()) {
            repositionToCheckpoint(player);
            return;
        }

        // Illegal block contact (water / lava)
        org.bukkit.block.Block block = loc.getBlock();
        org.bukkit.Material mat = block.getType();
        if (mat == org.bukkit.Material.WATER || mat == org.bukkit.Material.LAVA
                || mat == org.bukkit.Material.BUBBLE_COLUMN) {
            repositionToCheckpoint(player);
        }
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

    // ── Action Bar Task ────────────────────────────────────────────────────

    public void startActionBarTask() {
        int intervalTicks = plugin.getConfig().getInt("settings.actionbar-interval", 2);
        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ActiveRun> entry : activeRuns.entrySet()) {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    if (p == null || !p.isOnline()) continue;
                    ActiveRun run = entry.getValue();
                    if (!run.isActive()) continue;

                    Component bar = Component.text("⏱ ")
                            .color(TextColor.color(0x55FFFF))
                            .append(Component.text(run.getFormattedTime())
                                    .color(NamedTextColor.WHITE)
                                    .decorate(TextDecoration.BOLD))
                            .append(Component.text("  [" + run.getCourseName() + "]")
                                    .color(TextColor.color(0xAAAAAA)));

                    p.sendActionBar(bar);
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
    }

    public void stopActionBarTask() {
        if (actionBarTask != null) actionBarTask.cancel();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Component msg(String legacy) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacy);
    }
}
