package me.uc.hussein.ultrashomes.model;

/** One saved home. Coordinates are stored exactly as captured - never adjusted. */
public final class Home {
    private final int number;
    private final String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final long createdAt;
    private long updatedAt;

    public Home(int number, String name, String world, double x, double y, double z,
                float yaw, float pitch, long createdAt, long updatedAt) {
        this.number = number;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int number() { return number; }
    public String name() { return name; }
    public String world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public long createdAt() { return createdAt; }
    public long updatedAt() { return updatedAt; }
}
