package com.megapolis.core.modules.transport;

public enum VehicleType {
    CAR(1001, "Автомобиль", 120, 80, 100),
    MOTORCYCLE(2001, "Мотоцикл", 90, 30, 80),
    BOAT(3001, "Катер", 60, 50, 120),
    HELICOPTER(4001, "Вертолёт", 100, 60, 90);
    private final int modelId; private final String displayName; private final int maxSpeed; private final int fuelCapacity; private final int maxHealth;
    VehicleType(int modelId, String displayName, int maxSpeed, int fuelCapacity, int maxHealth) {
        this.modelId = modelId; this.displayName = displayName; this.maxSpeed = maxSpeed; this.fuelCapacity = fuelCapacity; this.maxHealth = maxHealth;
    }
    public int getModelId() { return modelId; }
    public String getDisplayName() { return displayName; }
    public int getMaxSpeed() { return maxSpeed; }
    public int getFuelCapacity() { return fuelCapacity; }
    public int getMaxHealth() { return maxHealth; }
}
