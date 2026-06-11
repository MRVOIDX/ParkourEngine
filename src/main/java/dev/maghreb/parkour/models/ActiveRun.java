package dev.maghreb.parkour.models;

import org.bukkit.Location;
import java.util.UUID;

public class ActiveRun {

    public enum InvalidationReason {
        FLIGHT("Flight detected"),
        ENDER_PEARL("Ender pearl usage"),
        ELYTRA("Elytra gliding"),
        TELEPORT("External teleportation"),
        VELOCITY_EXPLOIT("Illegal velocity boost"),
        QUIT("Player disconnected"),
        SERVER_SHUTDOWN("Server shutting down"),
        ADMIN_RESET("Reset by admin");

        private final String display;
        InvalidationReason(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private final UUID playerUUID;
    private final String courseName;
    private final long startTimestamp;
    private int lastCheckpointIndex;
    private Location lastCheckpointLocation;
    private boolean active;

    public ActiveRun(UUID playerUUID, String courseName, Location startLocation) {
        this.playerUUID = playerUUID;
        this.courseName = courseName;
        this.startTimestamp = System.currentTimeMillis();
        this.lastCheckpointIndex = -1;
        this.lastCheckpointLocation = startLocation.clone();
        this.active = true;
    }

    public long getElapsedMillis() {
        return System.currentTimeMillis() - startTimestamp;
    }

    public String getFormattedTime() {
        return formatTime(getElapsedMillis());
    }

    public static String formatTime(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms      = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }

    public UUID getPlayerUUID()                    { return playerUUID; }
    public String getCourseName()                  { return courseName; }
    public long getStartTimestamp()                { return startTimestamp; }
    public int getLastCheckpointIndex()            { return lastCheckpointIndex; }
    public void setLastCheckpointIndex(int index)  { this.lastCheckpointIndex = index; }
    public Location getLastCheckpointLocation()    { return lastCheckpointLocation; }
    public void setLastCheckpointLocation(Location loc) { this.lastCheckpointLocation = loc.clone(); }
    public boolean isActive()                      { return active; }
    public void setActive(boolean active)          { this.active = active; }
}
