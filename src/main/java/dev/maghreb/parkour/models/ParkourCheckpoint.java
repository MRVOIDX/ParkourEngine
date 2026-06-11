package dev.maghreb.parkour.models;

public class ParkourCheckpoint {

    private int order;
    private double x;
    private double y;
    private double z;
    private String world;

    public ParkourCheckpoint() {}

    public ParkourCheckpoint(int order, String world, double x, double y, double z) {
        this.order = order;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SerializableLocation toSerializableLocation() {
        return new SerializableLocation(world, x, y, z);
    }

    public int getOrder()             { return order; }
    public void setOrder(int order)   { this.order = order; }
    public double getX()              { return x; }
    public void setX(double x)        { this.x = x; }
    public double getY()              { return y; }
    public void setY(double y)        { this.y = y; }
    public double getZ()              { return z; }
    public void setZ(double z)        { this.z = z; }
    public String getWorld()          { return world; }
    public void setWorld(String w)    { this.world = w; }
}
