package com.megapolis.core.modules.transport;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Vehicle {

    private UUID vehicleId;
    private UUID owner;
    private VehicleType type;
    private LocationData location;
    private int health;
    private int fuel;
    private String plateNumber;
    private boolean engineRunning;
    private int nitroLevel;
    private String trunkData;

    public Vehicle(UUID vehicleId, UUID owner, VehicleType type, Location location, int health, int fuel) {
        this.vehicleId = vehicleId;
        this.owner = owner;
        this.type = type;
        this.location = new LocationData(location);
        this.health = Math.min(100, Math.max(0, health));
        this.fuel = Math.min(100, Math.max(0, fuel));
        this.engineRunning = true;
        this.nitroLevel = 0;
        this.trunkData = "";
    }

    public UUID getVehicleId() { return vehicleId; }
    public UUID getOwner() { return owner; }
    public VehicleType getType() { return type; }
    public LocationData getLocationData() { return location; }
    public void setLocation(Location loc) { this.location = new LocationData(loc); }
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = Math.min(100, Math.max(0, health)); }
    public int getFuel() { return fuel; }
    public void setFuel(int fuel) { this.fuel = Math.min(100, Math.max(0, fuel)); }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public boolean getEngineRunning() { return engineRunning; }
    public void setEngineRunning(boolean running) { this.engineRunning = running; }
    public int getNitroLevel() { return nitroLevel; }
    public void setNitroLevel(int level) { this.nitroLevel = Math.min(3, Math.max(0, level)); }
    public String getTrunkData() { return trunkData; }
    public void setTrunkData(String trunkData) { this.trunkData = trunkData; }

    public static class LocationData {
        public String world;
        public double x, y, z;
        public float yaw, pitch;

        public LocationData(Location loc) {
            if (loc != null && loc.getWorld() != null) {
                this.world = loc.getWorld().getName();
                this.x = loc.getX();
                this.y = loc.getY();
                this.z = loc.getZ();
                this.yaw = loc.getYaw();
                this.pitch = loc.getPitch();
            }
        }

        public Location toLocation() {
            if (world == null) return null;
            World w = org.bukkit.Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }
}