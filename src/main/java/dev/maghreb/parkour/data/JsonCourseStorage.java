package dev.maghreb.parkour.data;

import com.google.gson.*;
import dev.maghreb.parkour.ParkourPlugin;
import dev.maghreb.parkour.models.ParkourCheckpoint;
import dev.maghreb.parkour.models.ParkourCourse;
import dev.maghreb.parkour.models.SerializableLocation;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages asynchronous read/write of course data to courses.json using GSON.
 * Thread-safe via ConcurrentHashMap in-memory cache.
 */
public class JsonCourseStorage {

    private final ParkourPlugin plugin;
    private final Gson gson;
    private final Path storageFile;
    private final Map<String, ParkourCourse> courseCache;

    public JsonCourseStorage(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.storageFile = plugin.getDataFolder().toPath().resolve("courses.json");
        this.courseCache = new ConcurrentHashMap<>();
    }

    /** Loads courses from disk synchronously on startup. */
    public void load() {
        if (!Files.exists(storageFile)) {
            saveDefaultFile();
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("courses")) return;

            JsonArray arr = root.getAsJsonArray("courses");
            for (JsonElement el : arr) {
                ParkourCourse course = parseCourse(el.getAsJsonObject());
                if (course != null) {
                    courseCache.put(course.getName().toLowerCase(), course);
                }
            }
            plugin.getLogger().info("Loaded " + courseCache.size() + " course(s) from courses.json.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load courses.json", e);
        }
    }

    /** Saves all courses asynchronously. */
    public void saveAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveSync();
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Synchronous save — call from async context. */
    public synchronized void saveSync() {
        try {
            Files.createDirectories(storageFile.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();

            for (ParkourCourse course : courseCache.values()) {
                arr.add(serializeCourse(course));
            }
            root.add("courses", arr);

            try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save courses.json", e);
        }
    }

    public void addCourse(ParkourCourse course) {
        courseCache.put(course.getName().toLowerCase(), course);
        saveAsync();
    }

    public void updateCourse(ParkourCourse course) {
        courseCache.put(course.getName().toLowerCase(), course);
        saveAsync();
    }

    public Optional<ParkourCourse> getCourse(String name) {
        return Optional.ofNullable(courseCache.get(name.toLowerCase()));
    }

    public Collection<ParkourCourse> getAllCourses() {
        return Collections.unmodifiableCollection(courseCache.values());
    }

    public boolean courseExists(String name) {
        return courseCache.containsKey(name.toLowerCase());
    }

    // ── Serialization helpers ──────────────────────────────────────────────

    private JsonObject serializeCourse(ParkourCourse course) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", course.getName());

        if (course.getStartLocation() != null)
            obj.add("start_location", serializeLocation(course.getStartLocation()));
        if (course.getFinishLocation() != null)
            obj.add("finish_location", serializeLocation(course.getFinishLocation()));

        obj.addProperty("fail_y_level", course.getFailYLevel());

        JsonArray checkpoints = new JsonArray();
        for (ParkourCheckpoint cp : course.getCheckpoints()) {
            JsonObject cpObj = new JsonObject();
            cpObj.addProperty("order", cp.getOrder());
            cpObj.addProperty("world", cp.getWorld());
            cpObj.addProperty("x", cp.getX());
            cpObj.addProperty("y", cp.getY());
            cpObj.addProperty("z", cp.getZ());
            checkpoints.add(cpObj);
        }
        obj.add("checkpoints", checkpoints);
        return obj;
    }

    private JsonObject serializeLocation(SerializableLocation loc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", loc.getWorld());
        obj.addProperty("x", loc.getX());
        obj.addProperty("y", loc.getY());
        obj.addProperty("z", loc.getZ());
        obj.addProperty("yaw", loc.getYaw());
        obj.addProperty("pitch", loc.getPitch());
        return obj;
    }

    private ParkourCourse parseCourse(JsonObject obj) {
        try {
            ParkourCourse course = new ParkourCourse();
            course.setName(obj.get("name").getAsString());

            if (obj.has("start_location"))
                course.setStartLocation(parseLocation(obj.getAsJsonObject("start_location")));
            if (obj.has("finish_location"))
                course.setFinishLocation(parseLocation(obj.getAsJsonObject("finish_location")));
            if (obj.has("fail_y_level"))
                course.setFailYLevel(obj.get("fail_y_level").getAsDouble());

            if (obj.has("checkpoints")) {
                List<ParkourCheckpoint> cps = new ArrayList<>();
                for (JsonElement el : obj.getAsJsonArray("checkpoints")) {
                    JsonObject cpObj = el.getAsJsonObject();
                    ParkourCheckpoint cp = new ParkourCheckpoint();
                    cp.setOrder(cpObj.get("order").getAsInt());
                    cp.setWorld(cpObj.has("world") ? cpObj.get("world").getAsString() : "world");
                    cp.setX(cpObj.get("x").getAsDouble());
                    cp.setY(cpObj.get("y").getAsDouble());
                    cp.setZ(cpObj.get("z").getAsDouble());
                    cps.add(cp);
                }
                course.setCheckpoints(cps);
            }
            return course;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse a course entry: " + e.getMessage());
            return null;
        }
    }

    private SerializableLocation parseLocation(JsonObject obj) {
        SerializableLocation loc = new SerializableLocation();
        loc.setWorld(obj.get("world").getAsString());
        loc.setX(obj.get("x").getAsDouble());
        loc.setY(obj.get("y").getAsDouble());
        loc.setZ(obj.get("z").getAsDouble());
        if (obj.has("yaw")) loc.setYaw(obj.get("yaw").getAsFloat());
        if (obj.has("pitch")) loc.setPitch(obj.get("pitch").getAsFloat());
        return loc;
    }

    private void saveDefaultFile() {
        try {
            Files.createDirectories(storageFile.getParent());
            JsonObject root = new JsonObject();
            root.add("courses", new JsonArray());
            try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE)) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create courses.json", e);
        }
    }
}
