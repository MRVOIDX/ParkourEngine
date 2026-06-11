package dev.maghreb.parkour.managers;

import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.data.JsonCourseStorage;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import dev.maghreb.parkour.models.SerializableLocation;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;

public class CourseManager {

    private final ParkourPlugin plugin;
    private final JsonCourseStorage storage;

    public CourseManager(ParkourPlugin plugin, JsonCourseStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean createCourse(String name) {
        if (storage.courseExists(name)) return false;
        ParkourCourse course = new ParkourCourse(name);
        course.setFailYLevel(plugin.getConfig().getDouble("settings.default-fail-y", 0));
        storage.addCourse(course);
        return true;
    }

    public boolean setStart(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        opt.get().setStartLocation(new SerializableLocation(location));
        storage.updateCourse(opt.get());
        return true;
    }

    public boolean setFinish(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        opt.get().setFinishLocation(new SerializableLocation(location));
        storage.updateCourse(opt.get());
        return true;
    }

    public int addCheckpoint(String courseName, Location location) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return -1;
        ParkourCourse course = opt.get();
        int order = course.getNextCheckpointOrder();
        course.addCheckpoint(new ParkourCheckpoint(order, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ()));
        storage.updateCourse(course);
        return order;
    }

    public boolean setFailY(String courseName, double yLevel) {
        Optional<ParkourCourse> opt = storage.getCourse(courseName);
        if (opt.isEmpty()) return false;
        opt.get().setFailYLevel(yLevel);
        storage.updateCourse(opt.get());
        return true;
    }

    public boolean deleteCourse(String courseName) {
        if (!storage.courseExists(courseName)) return false;
        storage.deleteCourse(courseName);
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
