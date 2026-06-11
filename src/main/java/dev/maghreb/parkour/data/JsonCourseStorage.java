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

public class JsonCourseStorage {

    private final ParkourPlugin plugin;
    private final Gson gson;
    private final Path storageFile;
    private final Map<String, ParkourCourse> cache;

    public JsonCourseStorage(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.storageFile = plugin.getDataFolder().toPath().resolve("courses.json");
        this.cache = new ConcurrentHashMap<>();
    }

    public void load() {
        if (!Files.exists(storageFile)) {
            writeEmpty();
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("courses")) return;
            for (JsonElement el : root.getAsJsonArray("courses")) {
                ParkourCourse course = parseCourse(el.getAsJsonObject());
                if (course != null) cache.put(course.getName().toLowerCase(), course);
            }
            plugin.getLogger().info("Loaded " + cache.size() + " course(s).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load courses.json", e);
        }
    }

    public void saveAsync() {
        new BukkitRunnable() {
            @Override public void run() { saveSync(); }
        }.runTaskAsynchronously(plugin);
    }

    public synchronized void saveSync() {
        try {
            Files.createDirectories(storageFile.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (ParkourCourse course : cache.values()) arr.add(serializeCourse(course));
            root.add("courses", arr);
            try (Writer w = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save courses.json", e);
        }
    }

    public void addCourse(ParkourCourse course) {
        cache.put(course.getName().toLowerCase(), course);
        saveAsync();
    }

    public void updateCourse(ParkourCourse course) {
        cache.put(course.getName().toLowerCase(), course);
        saveAsync();
    }

    public void deleteCourse(String name) {
        cache.remove(name.toLowerCase());
        saveAsync();
    }

    public Optional<ParkourCourse> getCourse(String name) {
        return Optional.ofNullable(cache.get(name.toLowerCase()));
    }

    public Collection<ParkourCourse> getAllCourses() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public boolean courseExists(String name) {
        return cache.containsKey(name.toLowerCase());
    }

    private JsonObject serializeCourse(ParkourCourse course) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", course.getName());
        if (course.getStartLocation() != null)
            obj.add("start_location", serializeLocation(course.getStartLocation()));
        if (course.getFinishLocation() != null)
            obj.add("finish_location", serializeLocation(course.getFinishLocation()));
        obj.addProperty("fail_y_level", course.getFailYLevel());
        JsonArray cps = new JsonArray();
        for (ParkourCheckpoint cp : course.getCheckpoints()) {
            JsonObject c = new JsonObject();
            c.addProperty("order", cp.getOrder());
            c.addProperty("world", cp.getWorld());
            c.addProperty("x", cp.getX());
            c.addProperty("y", cp.getY());
            c.addProperty("z", cp.getZ());
            cps.add(c);
        }
        obj.add("checkpoints", cps);
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
                List<ParkourCheckpoint> list = new ArrayList<>();
                for (JsonElement el : obj.getAsJsonArray("checkpoints")) {
                    JsonObject c = el.getAsJsonObject();
                    ParkourCheckpoint cp = new ParkourCheckpoint();
                    cp.setOrder(c.get("order").getAsInt());
                    cp.setWorld(c.has("world") ? c.get("world").getAsString() : "world");
                    cp.setX(c.get("x").getAsDouble());
                    cp.setY(c.get("y").getAsDouble());
                    cp.setZ(c.get("z").getAsDouble());
                    list.add(cp);
                }
                course.setCheckpoints(list);
            }
            return course;
        } catch (Exception e) {
            plugin.getLogger().warning("Skipped a malformed course entry: " + e.getMessage());
            return null;
        }
    }

    private SerializableLocation parseLocation(JsonObject obj) {
        SerializableLocation loc = new SerializableLocation();
        loc.setWorld(obj.get("world").getAsString());
        loc.setX(obj.get("x").getAsDouble());
        loc.setY(obj.get("y").getAsDouble());
        loc.setZ(obj.get("z").getAsDouble());
        if (obj.has("yaw"))   loc.setYaw(obj.get("yaw").getAsFloat());
        if (obj.has("pitch")) loc.setPitch(obj.get("pitch").getAsFloat());
        return loc;
    }

    private void writeEmpty() {
        try {
            Files.createDirectories(storageFile.getParent());
            JsonObject root = new JsonObject();
            root.add("courses", new JsonArray());
            try (Writer w = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create courses.json", e);
        }
    }
}
