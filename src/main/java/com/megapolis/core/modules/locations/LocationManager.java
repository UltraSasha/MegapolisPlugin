package com.megapolis.core.modules.locations;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocationManager implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<String, LocationData> locations = new HashMap<>();
    private final Map<UUID, Set<String>> playerActiveZones = new ConcurrentHashMap<>();

    public LocationManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadLocations();
    }

    private void loadLocations() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("locations");
        if (section == null) {
            plugin.getLogger().warning("Секция 'locations' в config.yml не найдена!");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection locSection = section.getConfigurationSection(id);
            if (locSection == null) continue;

            String type = locSection.getString("type");
            String owner = locSection.getString("owner", "none");
            String displayName = locSection.getString("display_name", id);
            String worldName = locSection.getString("world");
            double x = locSection.getDouble("x");
            double y = locSection.getDouble("y");
            double z = locSection.getDouble("z");
            double radius = locSection.getDouble("radius", 5.0);

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Мир '" + worldName + "' не найден для локации " + id);
                continue;
            }

            Location location = new Location(world, x, y, z);
            LocationData data = new LocationData(id, type, owner, displayName, location, radius);
            locations.put(id, data);
            plugin.getLogger().info("Загружена локация: " + id + " (" + type + ")");
        }
        plugin.getLogger().info("Загружено " + locations.size() + " локаций.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Set<String> activeZones = playerActiveZones.getOrDefault(uuid, new HashSet<>());
        Set<String> newActiveZones = new HashSet<>();

        for (LocationData data : locations.values()) {
            if (data.getLocation().distance(player.getLocation()) <= data.getRadius()) {
                newActiveZones.add(data.getId());
            }
        }

        for (String zoneId : newActiveZones) {
            if (!activeZones.contains(zoneId)) {
                onPlayerEnterZone(player, zoneId);
            }
        }

        for (String zoneId : activeZones) {
            if (!newActiveZones.contains(zoneId)) {
                onPlayerExitZone(player, zoneId);
            }
        }

        if (!newActiveZones.isEmpty()) {
            playerActiveZones.put(uuid, newActiveZones);
        } else {
            playerActiveZones.remove(uuid);
        }
    }

    private void onPlayerEnterZone(Player player, String zoneId) {
        LocationData data = locations.get(zoneId);
        if (data == null) return;

        player.sendMessage("§eВы вошли в зону: §6" + data.getDisplayName());

        switch (data.getType().toLowerCase()) {
            case "parking" -> plugin.getModuleManager().getVehicleManager().openParkingGUI(player, data);
            case "police" -> plugin.getModuleManager().getVehicleManager().openPoliceGUI(player, data);
            case "business" -> plugin.getModuleManager().getVehicleManager().openBusinessGUI(player, data);
            case "postomat" -> plugin.getModuleManager().getVehicleManager().openPostomatGUI(player, data);
            case "tuning" -> plugin.getModuleManager().getVehicleManager().openTuningGUI(player, data);
            default -> player.sendMessage("§7Тип: " + data.getType() + ", Владелец: " + data.getOwner());
        }
    }

    private void onPlayerExitZone(Player player, String zoneId) {
        LocationData data = locations.get(zoneId);
        if (data == null) return;
        player.sendMessage("§eВы вышли из зоны: §6" + data.getDisplayName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerActiveZones.remove(event.getPlayer().getUniqueId());
    }

    public Map<String, LocationData> getAllLocations() {
        return locations;
    }

    public LocationData getLocation(String id) {
        return locations.get(id);
    }

    public static class LocationData {
        private final String id;
        private final String type;
        private final String owner;
        private final String displayName;
        private final Location location;
        private final double radius;

        public LocationData(String id, String type, String owner, String displayName, Location location, double radius) {
            this.id = id;
            this.type = type;
            this.owner = owner;
            this.displayName = displayName;
            this.location = location;
            this.radius = radius;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getOwner() { return owner; }
        public String getDisplayName() { return displayName; }
        public Location getLocation() { return location; }
        public double getRadius() { return radius; }
    }
}
