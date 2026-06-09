package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import dev.maghreb.parkour.models.SerializableLocation;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Renders ambient particle rays above each checkpoint block so players
 * can visually identify the intended track pathway.
 */
public class ParticleManager {

    private final ParkourPlugin plugin;
    private final CourseManager courseManager;
    private BukkitTask task;

    private static final double RAY_HEIGHT = 2.5;   // vertical reach of the column
    private static final double RAY_STEP  = 0.3;    // gap between particle points
    private static final int    RENDER_RADIUS = 48; // only render near players

    public ParticleManager(ParkourPlugin plugin, CourseManager courseManager) {
        this.plugin = plugin;
        this.courseManager = courseManager;
    }

    public void startTask() {
        int interval = plugin.getConfig().getInt("settings.particle-interval", 20);
        task = new BukkitRunnable() {
            @Override
            public void run() {
                spawnParticles();
            }
        }.runTaskTimer(plugin, 10L, interval);
    }

    public void stopTask() {
        if (task != null) task.cancel();
    }

    private void spawnParticles() {
        for (ParkourCourse course : courseManager.getAllCourses()) {
            renderCourseParticles(course);
        }
    }

    private void renderCourseParticles(ParkourCourse course) {
        // Checkpoints — cyan pillar
        for (ParkourCheckpoint cp : course.getCheckpoints()) {
            spawnVerticalRay(cp.getWorld(), cp.getX(), cp.getY(), cp.getZ(),
                    Particle.WITCH, RAY_HEIGHT, RAY_STEP);
        }

        // Start — green pillar
        if (course.getStartLocation() != null) {
            SerializableLocation s = course.getStartLocation();
            spawnVerticalRay(s.getWorld(), s.getX(), s.getY(), s.getZ(),
                    Particle.HAPPY_VILLAGER, RAY_HEIGHT, RAY_STEP);
        }

        // Finish — gold pillar
        if (course.getFinishLocation() != null) {
            SerializableLocation f = course.getFinishLocation();
            spawnVerticalRay(f.getWorld(), f.getX(), f.getY(), f.getZ(),
                    Particle.END_ROD, RAY_HEIGHT, RAY_STEP);
        }
    }

    private void spawnVerticalRay(String worldName, double x, double y, double z,
                                   Particle particle, double height, double step) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return;

        Location base = new Location(world, x + 0.5, y + 1.0, z + 0.5);

        // Only render if at least one player is nearby (performance guard)
        boolean playerNearby = world.getPlayers().stream().anyMatch(p ->
                p.getLocation().distanceSquared(base) <= RENDER_RADIUS * RENDER_RADIUS);
        if (!playerNearby) return;

        for (double dy = 0; dy <= height; dy += step) {
            Location point = base.clone().add(0, dy, 0);
            world.spawnParticle(particle, point, 1, 0.05, 0, 0.05, 0, null, true);
        }
    }
}
