package dev.maghreb.parkour.events;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.managers.CourseManager;
import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * High-frequency listener that drives the parkour state machine.
 * Handles: run start, checkpoint advance, run completion, and failure conditions.
 *
 * Uses HIGHEST priority to let other plugins cancel movement first.
 */
public class PlayerMoveListener implements Listener {

    private static final double START_RADIUS      = 1.5;
    private static final double CHECKPOINT_RADIUS = 1.5;
    private static final double FINISH_RADIUS     = 1.5;

    private final ParkourPlugin plugin;
    private final RunManager runManager;
    private final CourseManager courseManager;

    public PlayerMoveListener(ParkourPlugin plugin, RunManager runManager, CourseManager courseManager) {
        this.plugin = plugin;
        this.runManager = runManager;
        this.courseManager = courseManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if only head rotation changed (no position delta)
        if (event.getFrom().distanceSquared(event.getTo()) == 0) return;

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        for (ParkourCourse course : courseManager.getAllCourses()) {
            if (!course.isFullyConfigured()) continue;

            boolean isRunning = runManager.isRunning(player.getUniqueId());

            // ── Start pad check ──────────────────────────────────────────
            if (!isRunning && course.getStartLocation() != null) {
                if (course.getStartLocation().isNear(to, START_RADIUS)) {
                    runManager.startRun(player, course);
                    return;
                }
            }

            if (!isRunning) continue;

            // Only process events for the course the player is running
            runManager.getActiveRun(player.getUniqueId()).ifPresent(run -> {
                if (!run.getCourseName().equals(course.getName())) return;

                // ── Finish pad check ─────────────────────────────────────
                if (course.getFinishLocation() != null
                        && course.getFinishLocation().isNear(to, FINISH_RADIUS)) {
                    runManager.completeRun(player, course);
                    return;
                }

                // ── Checkpoint check ─────────────────────────────────────
                for (ParkourCheckpoint cp : course.getCheckpoints()) {
                    if (cp.toSerializableLocation().isNear(to, CHECKPOINT_RADIUS)) {
                        runManager.onCheckpointReached(player, course, cp);
                        break;
                    }
                }

                // ── Fail conditions ──────────────────────────────────────
                runManager.checkFailConditions(player, course);
            });
        }
    }
}
