package dev.maghreb.parkour;

import dev.maghreb.parkour.commands.ParkourCommand;
import dev.maghreb.parkour.data.DatabaseManager;
import dev.maghreb.parkour.data.JsonCourseStorage;
import dev.maghreb.parkour.events.*;
import dev.maghreb.parkour.managers.CourseManager;
import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.managers.ParticleManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ParkourPlugin extends JavaPlugin {

    private static ParkourPlugin instance;

    private DatabaseManager databaseManager;
    private JsonCourseStorage courseStorage;
    private CourseManager courseManager;
    private RunManager runManager;
    private ParticleManager particleManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database. Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.courseStorage = new JsonCourseStorage(this);
        this.courseStorage.load();

        this.courseManager = new CourseManager(this, courseStorage);
        this.runManager = new RunManager(this, databaseManager);
        this.particleManager = new ParticleManager(this, courseManager);

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this, runManager, courseManager), this);
        getServer().getPluginManager().registerEvents(new AntiExploitListener(this, runManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(runManager), this);

        ParkourCommand cmd = new ParkourCommand(this, courseManager, runManager, databaseManager);
        getCommand("parkour").setExecutor(cmd);
        getCommand("parkour").setTabCompleter(cmd);

        particleManager.startTask();
        runManager.startActionBarTask();

        getLogger().info("ParkourEngine v" + getDescription().getVersion() + " enabled. Author: MrVoidx");
    }

    @Override
    public void onDisable() {
        if (runManager != null) runManager.invalidateAllRuns("Server shutting down");
        if (particleManager != null) particleManager.stopTask();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("ParkourEngine disabled.");
    }

    public static ParkourPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public JsonCourseStorage getCourseStorage() { return courseStorage; }
    public CourseManager getCourseManager() { return courseManager; }
    public RunManager getRunManager() { return runManager; }
    public ParticleManager getParticleManager() { return particleManager; }
}
