package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.JsonCourseStorage;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import dev.maghreb.parkour.models.SerializableLocation;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;

/**
 * High-level API for managing parkour course configuration.
 * Delegates persistence to JsonCourseStorage.
 */
public class CourseManager {

    private final ParkourPlugin plugin;
    private final JsonCourseStorage storage;

    public CourseManager(ParkourPlugin plugin, JsonCourseStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Creates a new course with the given name. Returns false if it already exists. */
    public boolean createCourse(String name) {
        if (storage.courseExists(name)) return false;
        ParkourCourse course = new ParkourCourse(name);
        course.setFailYLevel(plugin.getConfig().getDouble("settings.default-fail-y", 0));
        storage.addCourse(course);
        return true;
    }

    /** Sets the start location for a course. Returns false if course not found. */
    public boolean setStart(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        ParkourCourse course = opt.get();
        course.setStartLocation(new SerializableLocation(location));
        storage.updateCourse(course);
        return true;
    }

    /** Sets the finish location for a course. Returns false if course not found. */
    public boolean setFinish(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        ParkourCourse course = opt.get();
        course.setFinishLocation(new SerializableLocation(location));
        storage.updateCourse(course);
        return true;
    }

    /** Adds the next sequential checkpoint at the given location. Returns -1 if course not found. */
    public int addCheckpoint(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return -1;
        ParkourCourse course = opt.get();
        int order = course.getNextCheckpointOrder();
        ParkourCheckpoint cp = new ParkourCheckpoint(order, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
        course.addCheckpoint(cp);
        storage.updateCourse(course);
        return order;
    }

    /** Sets the fail Y level for a course. */
    public boolean setFailY(String courseName, double yLevel) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        opt.get().setFailYLevel(yLevel);
        storage.updateCourse(opt.get());
        return true;
    }

    public Optional<ParkourCourse> getCourse(String name) {
        return storage.getCourse(name);
    }

    public Collection<ParkourCourse> getAllCourses() {
        return storage.getAllCourses();
    }

    public boolean courseExists(String name) {
        return storage.courseExists(name);
    }
}
