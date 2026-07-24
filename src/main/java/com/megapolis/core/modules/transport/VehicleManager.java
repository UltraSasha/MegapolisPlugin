package com.megapolis.core.modules.transport;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.data.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class VehicleManager implements Listener {

    private final MegapolisPlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, Vehicle> vehicles = new HashMap<>();
    private final Map<UUID, UUID> playerToVehicle = new HashMap<>();
    private final Map<UUID, Inventory> trunkInventories = new HashMap<>();
    // Кэш доступных моделей из конфига (название → данные)
    private final Map<String, VehicleModelData> availableModels = new HashMap<>();

    public VehicleManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        loadVehicles();
        loadAvailableModels();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // === Загрузка данных о доступных моделях из config.yml ===
    private void loadAvailableModels() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection vehiclesSection = config.getConfigurationSection("vehicles");
        if (vehiclesSection == null) {
            plugin.getLogger().warning("Секция vehicles в config.yml не найдена!");
            return;
        }

        for (String key : vehiclesSection.getKeys(false)) {
            ConfigurationSection modelSection = vehiclesSection.getConfigurationSection(key);
            if (modelSection == null) continue;

            String name = modelSection.getString("name", key);
            int modelId = modelSection.getInt("model_id", 0);
            int maxSpeed = modelSection.getInt("max_speed", 100);
            int fuelCapacity = modelSection.getInt("fuel_capacity", 50);
            int health = modelSection.getInt("health", 100);
            // Цена задаётся в конфиге, если нет — рассчитываем по характеристикам
            double price = modelSection.getDouble("price", maxSpeed * fuelCapacity / 10.0);

            VehicleModelData data = new VehicleModelData(key, name, modelId, maxSpeed, fuelCapacity, health, price);
            availableModels.put(key, data);
        }
        plugin.getLogger().info("Загружено " + availableModels.size() + " моделей транспорта для авторынка.");
    }

    // === Авторынок (открывается из планшета) ===
    public void openAutoMarket(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§bАвторынок");

        int slot = 0;
        for (VehicleModelData model : availableModels.values()) {
            if (slot >= 53) break; // максимум 54 слота (0-53)
            ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(model.getModelId());
            meta.setDisplayName("§6" + model.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Макс. скорость: §f" + model.getMaxSpeed());
            lore.add("§7Ёмкость топлива: §f" + model.getFuelCapacity());
            lore.add("§7Прочность: §f" + model.getHealth());
            lore.add("§7Цена: §a" + String.format("%,.0f", model.getPrice()) + " монет");
            lore.add("§eНажмите для покупки");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);

            // Сохраняем идентификатор модели в NBT (через ItemMeta нельзя, используем отдельную карту или lore)
            // Для простоты будем определять модель по имени в обработчике
            slot++;
        }

        // Закрывающая кнопка
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onAutoMarketClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§bАвторынок")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= availableModels.size()) return;

        // Определяем, на какую модель нажали (по порядку в availableModels)
        String[] modelKeys = availableModels.keySet().toArray(new String[0]);
        String selectedKey = modelKeys[slot];
        VehicleModelData model = availableModels.get(selectedKey);
        if (model == null) return;

        // Проверка баланса (Vault)
        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < model.getPrice()) {
            player.sendMessage("§cНедостаточно денег! Нужно: " + String.format("%,.0f", model.getPrice()) + " монет.");
            player.closeInventory();
            return;
        }

        // Создаём машину
        Location spawnLoc = player.getLocation().add(0, 0, 3); // немного впереди
        Vehicle vehicle = spawnVehicle(player, model, spawnLoc);
        if (vehicle == null) {
            player.sendMessage("§cНе удалось создать машину.");
            player.closeInventory();
            return;
        }

        // Списываем деньги (можно перевести ServerBot)
        plugin.getEconomyManager().withdraw(player, model.getPrice());
        plugin.getEconomyManager().payToServerBot(player, model.getPrice());

        // Выдаём ПТС (в чат)
        player.sendMessage("§aВы купили машину " + model.getName() + " за " + String.format("%,.0f", model.getPrice()) + " монет.");
        player.sendMessage("§6ПТС: §7Модель: " + model.getName() +
                           ", Владелец: " + player.getName() +
                           ", ID: " + vehicle.getVehicleId());

        player.closeInventory();
    }

    // === Вспомогательный метод для спавна машины по модели из авторынка ===
    private Vehicle spawnVehicle(Player owner, VehicleModelData model, Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        Horse horse = world.spawn(location, Horse.class);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(owner);
        horse.setDomestication(1);
        horse.setCustomName(model.getName());
        horse.setCustomNameVisible(true);

        ItemStack armor = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = armor.getItemMeta();
        meta.setCustomModelData(model.getModelId());
        armor.setItemMeta(meta);
        horse.getInventory().setArmor(armor);

        NamespacedKey key = new NamespacedKey(plugin, "vehicle_id");
        String vehicleIdStr = UUID.randomUUID().toString();
        horse.getPersistentDataContainer().set(key, PersistentDataType.STRING, vehicleIdStr);

        UUID vehicleId = UUID.fromString(vehicleIdStr);
        VehicleType type = VehicleType.valueOf(model.getTypeKey().toUpperCase()); // нужно, чтобы тип совпадал с enum
        // Если тип не совпадает, используем CAR по умолчанию
        VehicleType realType;
        try {
            realType = VehicleType.valueOf(model.getTypeKey().toUpperCase());
        } catch (IllegalArgumentException e) {
            realType = VehicleType.CAR;
        }

        Vehicle vehicle = new Vehicle(vehicleId, owner.getUniqueId(), realType, location, model.getHealth(), 100);
        vehicles.put(vehicleId, vehicle);
        playerToVehicle.put(owner.getUniqueId(), vehicleId);

        Inventory trunk = Bukkit.createInventory(null, 27, "Багажник " + model.getName());
        trunkInventories.put(vehicleId, trunk);

        dataManager.save("vehicles/" + vehicleId, vehicle);
        return vehicle;
    }

    // === Остальные методы (без изменений, но я приведу их для полноты) ===
    public boolean boardVehicle(Player player, Horse horse) {
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_id");
        String vehicleIdStr = horse.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (vehicleIdStr == null) {
            player.sendMessage("§cЭта лошадь не является зарегистрированной машиной.");
            return false;
        }
        UUID vehicleId = UUID.fromString(vehicleIdStr);
        if (!vehicles.containsKey(vehicleId)) {
            player.sendMessage("§cМашина не найдена в системе.");
            return false;
        }
        if (playerToVehicle.containsKey(player.getUniqueId())) {
            player.sendMessage("§cВы уже сидите в машине.");
            return false;
        }

        Vehicle vehicle = vehicles.get(vehicleId);
        if (!vehicle.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cВы не владелец этой машины.");
            return false;
        }

        horse.addPassenger(player);
        playerToVehicle.put(player.getUniqueId(), vehicleId);
        player.sendMessage("§aВы сели в машину. §eF — двигатель, §aПробел — нитро, W/S — газ/тормоз, Shift — выход.");
        return true;
    }

    public void exitVehicle(Player player) {
        UUID vehicleId = playerToVehicle.remove(player.getUniqueId());
        if (vehicleId == null) return;
        Entity entity = Bukkit.getEntity(vehicleId);
        if (entity instanceof Horse horse) {
            horse.removePassenger(player);
        }
        player.sendMessage("§eВы вышли из машины.");
    }

    public Vehicle getPlayerVehicle(Player player) {
        UUID vehicleId = playerToVehicle.get(player.getUniqueId());
        return vehicleId == null ? null : vehicles.get(vehicleId);
    }

    public Vehicle getNearestVehicle(Player player) {
        Vehicle nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Vehicle v : vehicles.values()) {
            Entity entity = Bukkit.getEntity(v.getVehicleId());
            if (entity == null) continue;
            double dist = entity.getLocation().distance(player.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = v;
            }
        }
        return nearest;
    }

    public Vehicle getVehicleByEntity(Entity entity) {
        if (entity == null) return null;
        return vehicles.get(entity.getUniqueId());
    }

    public void handleMovement(Player player, boolean forward, boolean backward, boolean nitro) {
        Vehicle vehicle = getPlayerVehicle(player);
        if (vehicle == null) return;
        Entity entity = Bukkit.getEntity(vehicle.getVehicleId());
        if (!(entity instanceof Horse horse)) return;

        if (!vehicle.getEngineRunning()) {
            horse.setVelocity(horse.getVelocity().multiply(0));
            return;
        }

        double speed = 0.3;
        if (nitro && vehicle.getNitroLevel() > 0) {
            speed *= 1.0 + (vehicle.getNitroLevel() * 0.35);
        }

        Location eyeLoc = player.getEyeLocation();
        float yaw = eyeLoc.getYaw();
        float currentYaw = horse.getLocation().getYaw();
        float diff = yaw - currentYaw;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        double driftFactor = 0.0;
        if (Math.abs(diff) > 30 && speed > 0.2) {
            driftFactor = Math.min(0.4, Math.abs(diff) / 360.0 * 0.8);
            horse.setVelocity(horse.getVelocity().add(
                horse.getLocation().getDirection().multiply(-driftFactor * 0.1)
            ));
        }

        if (forward) {
            horse.setVelocity(horse.getLocation().getDirection().multiply(speed));
        } else if (backward) {
            horse.setVelocity(horse.getLocation().getDirection().multiply(-speed * 0.5));
        } else {
            horse.setVelocity(horse.getVelocity().multiply(0.9));
        }

        if (Math.abs(diff) > 5) {
            float newYaw = currentYaw + diff * 0.1f;
            horse.teleport(new Location(horse.getWorld(), horse.getLocation().getX(),
                horse.getLocation().getY(), horse.getLocation().getZ(), newYaw, horse.getLocation().getPitch()));
        }

        if (forward || backward) {
            int fuel = vehicle.getFuel();
            int consumption = nitro ? 3 : 1;
            fuel = Math.max(0, fuel - consumption);
            vehicle.setFuel(fuel);
            if (fuel == 0) {
                player.sendMessage("§cТопливо закончилось! Двигатель заглох.");
                vehicle.setEngineRunning(false);
                horse.setVelocity(horse.getVelocity().multiply(0));
            }
        }
    }

    public void damageVehicle(Vehicle vehicle, int damage, Player damager) {
        if (vehicle == null) return;
        int newHealth = vehicle.getHealth() - damage;
        if (newHealth < 0) newHealth = 0;
        vehicle.setHealth(newHealth);

        if (newHealth == 0) {
            explodeVehicle(vehicle);
        } else {
            spawnDamageParticles(vehicle);
            Entity entity = Bukkit.getEntity(vehicle.getVehicleId());
            if (entity != null) {
                for (Entity passenger : entity.getPassengers()) {
                    if (passenger instanceof Player player) {
                        double playerDamage = damage / 2.0;
                        player.damage(playerDamage);
                        player.sendMessage("§6Машина повреждена! Здоровье: §c" + newHealth + "%");
                    }
                }
            }
        }
        dataManager.save("vehicles/" + vehicle.getVehicleId(), vehicle);
    }

    private void explodeVehicle(Vehicle vehicle) {
        Entity entity = Bukkit.getEntity(vehicle.getVehicleId());
        if (entity == null) return;
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.createExplosion(loc, 4.0f, true, true);

        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                player.damage(20.0);
                player.sendMessage("§cВаша машина взорвалась! Вы получили сильные повреждения.");
            }
        }

        entity.remove();
        vehicles.remove(vehicle.getVehicleId());
        for (UUID playerId : playerToVehicle.keySet()) {
            if (playerToVehicle.get(playerId).equals(vehicle.getVehicleId())) {
                playerToVehicle.remove(playerId);
            }
        }
    }

    private void spawnDamageParticles(Vehicle vehicle) {
        Entity entity = Bukkit.getEntity(vehicle.getVehicleId());
        if (entity == null) return;
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.SMOKE_LARGE, loc, 20, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.FLAME, loc, 10, 1, 1, 1, 0.1);
    }

    public void openTrunk(Player player) {
        Vehicle vehicle = getPlayerVehicle(player);
        if (vehicle == null) {
            player.sendMessage("§cВы не в машине.");
            return;
        }
        Inventory trunk = trunkInventories.get(vehicle.getVehicleId());
        if (trunk == null) {
            trunk = Bukkit.createInventory(null, 27, "Багажник " + vehicle.getType().getDisplayName());
            trunkInventories.put(vehicle.getVehicleId(), trunk);
        }
        player.openInventory(trunk);
    }

    private String saveTrunkToString(Inventory trunk) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(trunk.getSize());
            for (int i = 0; i < trunk.getSize(); i++) {
                dataOutput.writeObject(trunk.getItem(i));
            }
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка сохранения багажника: " + e.getMessage());
            return "";
        }
    }

    private void loadTrunkFromString(Inventory trunk, String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int size = dataInput.readInt();
            for (int i = 0; i < size; i++) {
                trunk.setItem(i, (ItemStack) dataInput.readObject());
            }
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().warning("Ошибка загрузки багажника: " + e.getMessage());
        }
    }

    public void showPTS(Player player) {
        Vehicle vehicle = getNearestVehicle(player);
        if (vehicle == null) {
            player.sendMessage("§cРядом нет машин.");
            return;
        }
        player.sendMessage("§6=== ПТС ===\n§7Модель: " + vehicle.getType().getDisplayName() +
                           "\n§7Владелец: " + Bukkit.getOfflinePlayer(vehicle.getOwner()).getName() +
                           "\n§7Здоровье: " + vehicle.getHealth() + "%" +
                           "\n§7Топливо: " + vehicle.getFuel() + "%" +
                           "\n§7Двигатель: " + (vehicle.getEngineRunning() ? "§aЗаведён" : "§cЗаглушён") +
                           "\n§7Уровень нитро: " + vehicle.getNitroLevel());
    }

    public void toggleEngine(Player player) {
        Vehicle vehicle = getPlayerVehicle(player);
        if (vehicle == null) {
            player.sendMessage("§cВы не в машине.");
            return;
        }
        boolean newState = !vehicle.getEngineRunning();
        vehicle.setEngineRunning(newState);
        player.sendMessage("§6Двигатель " + (newState ? "§aзаведён" : "§cзаглушён"));
    }

    private void loadVehicles() {
        // Загрузка из data/vehicles/ (реализовать позже)
        plugin.getLogger().info("Загрузка машин (заглушка, но после первого спавна всё работает)");
    }

    public void saveAll() {
        for (Map.Entry<UUID, Vehicle> entry : vehicles.entrySet()) {
            UUID vehicleId = entry.getKey();
            Vehicle vehicle = entry.getValue();
            Inventory trunk = trunkInventories.get(vehicleId);
            if (trunk != null) {
                String trunkData = saveTrunkToString(trunk);
                vehicle.setTrunkData(trunkData);
            }
            dataManager.save("vehicles/" + vehicleId, vehicle);
        }
    }

    // === Внутренний класс для данных модели ===
    private static class VehicleModelData {
        private final String typeKey;
        private final String name;
        private final int modelId;
        private final int maxSpeed;
        private final int fuelCapacity;
        private final int health;
        private final double price;

        public VehicleModelData(String typeKey, String name, int modelId, int maxSpeed, int fuelCapacity, int health, double price) {
            this.typeKey = typeKey;
            this.name = name;
            this.modelId = modelId;
            this.maxSpeed = maxSpeed;
            this.fuelCapacity = fuelCapacity;
            this.health = health;
            this.price = price;
        }

        public String getTypeKey() { return typeKey; }
        public String getName() { return name; }
        public int getModelId() { return modelId; }
        public int getMaxSpeed() { return maxSpeed; }
        public int getFuelCapacity() { return fuelCapacity; }
        public int getHealth() { return health; }
        public double getPrice() { return price; }
    }
}