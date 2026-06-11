package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.models.ActiveRun;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
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
 *
 * Key fixes vs previous version:
 *  - repositionToCheckpoint() falls back to course start if no checkpoint reached yet
 *  - Action bar streams correctly via Spigot API (no Adventure classes)
 *  - Reposition is debounced (500 ms cooldown) to prevent teleport spam
 */
public class RunManager {

    private final ParkourPlugin plugin;
    private final DatabaseManager db;

    /** UUID → active run */
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    /** UUID → last reposition timestamp (ms) — prevents reposition spam */
    private final Map<UUID, Long> repositionCooldown = new ConcurrentHashMap<>();
    private static final long REPOSITION_COOLDOWN_MS = 500;

    private BukkitTask actionBarTask;

    public RunManager(ParkourPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ── Run Lifecycle ──────────────────────────────────────────────────────

    public void startRun(Player player, ParkourCourse course) {
        Location startLoc = course.getStartLocation().toBukkitLocation();
        if (startLoc == null) return;

        // Cancel any existing run silently
        ActiveRun existing = activeRuns.remove(player.getUniqueId());
        if (existing != null) existing.setActive(false);

        ActiveRun run = new ActiveRun(player.getUniqueId(), course.getName(), startLoc);
        activeRuns.put(player.getUniqueId(), run);

        player.sendMessage(c("&8&l[&b&lParkour&8&l] &r&bStarted &e" + course.getName()
                + " &7— &fGood luck!"));
        player.sendMessage(c("&8» &7" + course.getCheckpoints().size()
                + " checkpoint(s) · Reach the &afinish &7to complete"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.8f);
    }

    public void onCheckpointReached(Player player, ParkourCourse course, ParkourCheckpoint checkpoint) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;
        if (!run.getCourseName().equals(course.getName())) return;

        // Only advance — never go backward
        if (checkpoint.getOrder() > run.getLastCheckpointIndex()) {
            run.setLastCheckpointIndex(checkpoint.getOrder());
            Location cpLoc = checkpoint.toSerializableLocation().toBukkitLocation();
            if (cpLoc != null) run.setLastCheckpointLocation(cpLoc);

            int total = course.getCheckpoints().size();
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            player.sendMessage(c("&8» &7Checkpoint &b" + checkpoint.getOrder()
                    + " &8/ &b" + total + " &7— " + run.getFormattedTime()));
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
        player.sendMessage(c("&8&l[&b&lParkour&8&l] &a&lCOURSE COMPLETE!"));
        player.sendMessage(c("&8» &7Course: &e" + course.getName()));
        player.sendMessage(c("&8» &7Time:   &b" + formatted));
        player.sendTitle(c("&a&lFINISHED"), c("&7Time: &b" + formatted), 5, 60, 15);

        // Non-blocking async DB write
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long pb = db.getPersonalBest(player.getUniqueId(), course.getName());
            db.saveRecordIfBetter(player.getUniqueId(), player.getName(), course.getName(), timeMs);

            boolean isNewPb = (pb < 0 || timeMs < pb);
            long improvement = isNewPb && pb > 0 ? pb - timeMs : 0;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (isNewPb) {
                    player.sendMessage(c("&8» &6★ &eNew Personal Best! &6★"));
                    if (improvement > 0) {
                        player.sendMessage(c("&8» &7Improved by &a-" + ActiveRun.formatTime(improvement)));
                    }
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
                } else {
                    player.sendMessage(c("&8» &7Personal best: &b" + ActiveRun.formatTime(pb)));
                    long diff = timeMs - pb;
                    player.sendMessage(c("&8» &7Difference: &c+" + ActiveRun.formatTime(diff)));
                }
            });
        });
    }

    /**
     * Repositions player to their last valid checkpoint.
     * If no checkpoint has been reached yet, sends them back to the course START.
     * Includes a 500ms debounce to prevent teleport spam on rapid Y-level dips.
     */
    public void repositionToCheckpoint(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;

        // Debounce check
        long now = System.currentTimeMillis();
        Long last = repositionCooldown.get(player.getUniqueId());
        if (last != null && (now - last) < REPOSITION_COOLDOWN_MS) return;
        repositionCooldown.put(player.getUniqueId(), now);

        // If no checkpoint reached yet → fall back to course start
        Location dest;
        String label;
        if (run.getLastCheckpointIndex() < 0) {
            dest  = course.getStartLocation().toBukkitLocation();
            label = "&7Sent back to &estart";
        } else {
            dest  = run.getLastCheckpointLocation();
            label = "&7Sent back to checkpoint &e#" + run.getLastCheckpointIndex();
        }

        if (dest == null) return;
        final Location finalDest = dest;
        final String  finalLabel = label;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(finalDest);
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0.1, 0));
            player.sendMessage(c("&8» " + finalLabel));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
        });
    }

    public void invalidateRun(Player player, ActiveRun.InvalidationReason reason) {
        ActiveRun run = activeRuns.remove(player.getUniqueId());
        repositionCooldown.remove(player.getUniqueId());
        if (run == null) return;
        run.setActive(false);

        player.sendMessage(c("&8&l[&c&lParkour&8&l] &cRun cancelled &8— &7" + reason.getDisplay()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
    }

    public void invalidateAllRuns(String reason) {
        for (UUID uuid : new HashSet<>(activeRuns.keySet())) {
            ActiveRun run = activeRuns.remove(uuid);
            if (run != null) run.setActive(false);
        }
        repositionCooldown.clear();
    }

    // ── Fail Conditions ────────────────────────────────────────────────────

    public void checkFailConditions(Player player, ParkourCourse course) {
        ActiveRun run = activeRuns.get(player.getUniqueId());
        if (run == null || !run.isActive()) return;

        Location loc = player.getLocation();

        // Y-level boundary — primary fail condition
        if (loc.getY() < course.getFailYLevel()) {
            repositionToCheckpoint(player, course);
            return;
        }

        // Illegal block contact
        org.bukkit.Material mat = loc.getBlock().getType();
        if (mat == org.bukkit.Material.WATER
                || mat == org.bukkit.Material.LAVA
                || mat == org.bukkit.Material.BUBBLE_COLUMN) {
            repositionToCheckpoint(player, course);
        }
    }

    // ── Action Bar ─────────────────────────────────────────────────────────

    /**
     * Streams a live timer to the player's action bar every 2 ticks.
     * Format: ⏱ MM:SS.mmm  [CourseName]  ✦ CP: x/total
     *
     * Uses Spigot's sendMessage(ChatMessageType, BaseComponent[]) — available
     * on all Paper 1.21.x builds without touching Adventure classes.
     */
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

                    // Build the action bar string using legacy colour codes
                    String cpInfo = "";
                    // Attempt to get checkpoint count from course
                    try {
                        dev.maghreb.parkour.models.ParkourCourse c =
                                plugin.getCourseManager()
                                      .getCourse(run.getCourseName())
                                      .orElse(null);
                        if (c != null) {
                            int reached = Math.max(run.getLastCheckpointIndex(), 0);
                            int total   = c.getCheckpoints().size();
                            cpInfo = ChatColor.DARK_GRAY + "  \u2726 "
                                    + ChatColor.YELLOW + reached
                                    + ChatColor.DARK_GRAY + "/"
                                    + ChatColor.YELLOW + total + " CP";
                        }
                    } catch (Exception ignored) {}

                    String bar =
                            ChatColor.DARK_GRAY + "[\u23f1]"
                            + ChatColor.WHITE + ChatColor.BOLD + " " + run.getFormattedTime()
                            + ChatColor.RESET
                            + ChatColor.GRAY + "  " + run.getCourseName()
                            + cpInfo;

                    p.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(bar)
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

    // ── Helpers ────────────────────────────────────────────────────────────

    private String c(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
