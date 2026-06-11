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

public class ParticleManager {

    private final ParkourPlugin plugin;
    private final CourseManager courseManager;
    private BukkitTask task;

    private static final double RAY_HEIGHT   = 2.5;
    private static final double RAY_STEP     = 0.3;
    private static final int    RENDER_RANGE = 48;

    public ParticleManager(ParkourPlugin plugin, CourseManager courseManager) {
        this.plugin = plugin;
        this.courseManager = courseManager;
    }

    public void startTask() {
        int interval = plugin.getConfig().getInt("settings.particle-interval", 20);
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (ParkourCourse course : courseManager.getAllCourses()) {
                    renderCourse(course);
                }
            }
        }.runTaskTimer(plugin, 10L, interval);
    }

    public void stopTask() {
        if (task != null) task.cancel();
    }

    private void renderCourse(ParkourCourse course) {
        for (ParkourCheckpoint cp : course.getCheckpoints()) {
            spawnRay(cp.getWorld(), cp.getX(), cp.getY(), cp.getZ(), Particle.WITCH);
        }
        if (course.getStartLocation() != null) {
            SerializableLocation s = course.getStartLocation();
            spawnRay(s.getWorld(), s.getX(), s.getY(), s.getZ(), Particle.HAPPY_VILLAGER);
        }
        if (course.getFinishLocation() != null) {
            SerializableLocation f = course.getFinishLocation();
            spawnRay(f.getWorld(), f.getX(), f.getY(), f.getZ(), Particle.END_ROD);
        }
    }

    private void spawnRay(String worldName, double x, double y, double z, Particle particle) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return;

        Location base = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        boolean nearby = world.getPlayers().stream()
                .anyMatch(p -> p.getLocation().distanceSquared(base) <= RENDER_RANGE * RENDER_RANGE);
        if (!nearby) return;

        for (double dy = 0; dy <= RAY_HEIGHT; dy += RAY_STEP) {
            world.spawnParticle(particle, base.clone().add(0, dy, 0), 1, 0.05, 0, 0.05, 0, null, true);
        }
    }
}
