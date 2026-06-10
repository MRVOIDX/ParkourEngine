package dev.maghreb.parkour.models;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents the live state of a player's active parkour run.
 * This object is held entirely in memory — no DB writes until run completion.
 */
public class ActiveRun {

    public enum InvalidationReason {
        FLIGHT("Flight detected"),
        ENDER_PEARL("Ender pearl usage"),
        ELYTRA("Elytra gliding"),
        TELEPORT("External teleportation"),
        VELOCITY_EXPLOIT("Illegal velocity modification"),
        QUIT("Player quit"),
        SERVER_SHUTDOWN("Server shutting down"),
        ADMIN_RESET("Forcibly reset by admin");

        private final String display;
        InvalidationReason(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private final UUID playerUUID;
    private final String courseName;
    private final long startTimestamp;  // System.currentTimeMillis()
    private int lastCheckpointIndex;    // -1 = no checkpoint hit yet (respawn to start)
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

    /** Returns elapsed time in milliseconds since run start. */
    public long getElapsedMillis() {
        return System.currentTimeMillis() - startTimestamp;
    }

    /**
     * Formats elapsed time as MM:SS.mmm for the action bar.
     */
    public String getFormattedTime() {
        long millis = getElapsedMillis();
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }

    /**
     * Formats a given millisecond value — used for final display.
     */
    public static String formatTime(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getCourseName() { return courseName; }
    public long getStartTimestamp() { return startTimestamp; }

    public int getLastCheckpointIndex() { return lastCheckpointIndex; }
    public void setLastCheckpointIndex(int index) { this.lastCheckpointIndex = index; }

    public Location getLastCheckpointLocation() { return lastCheckpointLocation; }
    public void setLastCheckpointLocation(Location loc) { this.lastCheckpointLocation = loc.clone(); }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
