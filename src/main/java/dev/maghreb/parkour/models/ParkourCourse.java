package dev.maghreb.parkour.models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ParkourCourse {

    private String name;
    private SerializableLocation startLocation;
    private SerializableLocation finishLocation;
    private double failYLevel;
    private List<ParkourCheckpoint> checkpoints;

    public ParkourCourse() {
        this.checkpoints = new ArrayList<>();
    }

    public ParkourCourse(String name) {
        this.name = name;
        this.checkpoints = new ArrayList<>();
        this.failYLevel = Double.MIN_VALUE;
    }

    public void addCheckpoint(ParkourCheckpoint checkpoint) {
        checkpoints.add(checkpoint);
        checkpoints.sort(Comparator.comparingInt(ParkourCheckpoint::getOrder));
    }

    public int getNextCheckpointOrder() {
        return checkpoints.stream().mapToInt(ParkourCheckpoint::getOrder).max().orElse(0) + 1;
    }

    public boolean isFullyConfigured() {
        return startLocation != null && finishLocation != null;
    }

    public String getName()                                        { return name; }
    public void setName(String name)                              { this.name = name; }
    public SerializableLocation getStartLocation()                { return startLocation; }
    public void setStartLocation(SerializableLocation l)          { this.startLocation = l; }
    public SerializableLocation getFinishLocation()               { return finishLocation; }
    public void setFinishLocation(SerializableLocation l)         { this.finishLocation = l; }
    public double getFailYLevel()                                  { return failYLevel; }
    public void setFailYLevel(double y)                           { this.failYLevel = y; }
    public List<ParkourCheckpoint> getCheckpoints()               { return checkpoints; }
    public void setCheckpoints(List<ParkourCheckpoint> list)      { this.checkpoints = list; }
}
