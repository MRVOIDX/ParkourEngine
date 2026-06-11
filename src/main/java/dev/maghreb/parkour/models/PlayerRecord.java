package dev.maghreb.parkour.models;

import java.util.UUID;

public class PlayerRecord {

    private UUID playerUUID;
    private String playerName;
    private String courseName;
    private long bestTimeMillis;

    public PlayerRecord() {}

    public PlayerRecord(UUID playerUUID, String playerName, String courseName, long bestTimeMillis) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.courseName = courseName;
        this.bestTimeMillis = bestTimeMillis;
    }

    public String getFormattedTime() {
        return ActiveRun.formatTime(bestTimeMillis);
    }

    public UUID getPlayerUUID()                      { return playerUUID; }
    public void setPlayerUUID(UUID playerUUID)       { this.playerUUID = playerUUID; }
    public String getPlayerName()                    { return playerName; }
    public void setPlayerName(String playerName)     { this.playerName = playerName; }
    public String getCourseName()                    { return courseName; }
    public void setCourseName(String courseName)     { this.courseName = courseName; }
    public long getBestTimeMillis()                  { return bestTimeMillis; }
    public void setBestTimeMillis(long t)            { this.bestTimeMillis = t; }
}
