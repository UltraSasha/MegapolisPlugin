package com.megapolis.core.modules.transport;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.data.DataManager;
import com.megapolis.core.modules.locations.LocationManager.LocationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private final Map<String, VehicleModelData> availableModels = new HashMap<>();
    private final Map<UUID, String> selectedVehicleForTuning = new HashMap<>();

    public VehicleManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        loadVehicles();
        loadAvailableModels();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadAvailableModels() {
        ConfigurationSection vehiclesSection = plugin.getConfig().getConfigurationSection("vehicles");
        if (vehiclesSection == null) {
            plugin.getLogger().warning("Секция vehicles в config.yml не найдена!");
            return;
        }
        for (String key : vehiclesSection.getKeys(false)) {
            ConfigurationSection modelSection = vehiclesSection.getConfigurationSection(key);
            String name = modelSection.getString("name", key);
            int modelId = modelSection.getInt("model_id", 0);
            int maxSpeed = modelSection.getInt("max_speed", 100);
            int fuelCapacity = modelSection.getInt("fuel_capacity", 50);
            int health = modelSection.getInt("health", 100);
            double price = modelSection.getDouble("price", maxSpeed * fuelCapacity / 10.0);
            VehicleModelData data = new VehicleModelData(key, name, modelId, maxSpeed, fuelCapacity, health, price);
            availableModels.put(key, data);
        }
        plugin.getLogger().info("Загружено " + availableModels.size() + " моделей транспорта.");
    }

    public void openAutoMarket(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§bАвторынок");
        int slot = 0;
        for (VehicleModelData model : availableModels.values()) {
            if (slot >= 53) break;
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
            slot++;
        }
        inv.setItem(53, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onAutoMarketClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§bАвторынок")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 53) { player.closeInventory(); return; }
        if (slot < 0 || slot >= availableModels.size()) return;

        String[] keys = availableModels.keySet().toArray(new String[0]);
        String selectedKey = keys[slot];
        VehicleModelData model = availableModels.get(selectedKey);
        if (model == null) return;

        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < model.getPrice()) {
            player.sendMessage("§cНедостаточно денег! Нужно: " + String.format("%,.0f", model.getPrice()) + " монет.");
            player.closeInventory();
            return;
        }
        Location spawnLoc = player.getLocation().add(0, 0, 3);
        VehicleType type = VehicleType.valueOf(selectedKey.toUpperCase());
        Vehicle vehicle = spawnVehicle(player, type, spawnLoc);
        if (vehicle == null) {
            player.sendMessage("§cНе удалось создать машину.");
            player.closeInventory();
            return;
        }
        plugin.getEconomyManager().withdraw(player, model.getPrice());
        plugin.getEconomyManager().payToServerBot(player, model.getPrice());
        player.sendMessage("§aВы купили машину " + model.getName() + " за " + String.format("%,.0f", model.getPrice()) + " монет.");
        player.sendMessage("§6ПТС: §7Модель: " + model.getName() + ", Владелец: " + player.getName());
        player.closeInventory();
    }

    public Vehicle spawnVehicle(Player owner, VehicleType type, Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        Horse horse = world.spawn(location, Horse.class);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(owner);
        horse.setDomestication(1);
        horse.setCustomName(type.getDisplayName());
        horse.setCustomNameVisible(true);

        ItemStack armor = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = armor.getItemMeta();
        meta.setCustomModelData(type.getModelId());
        armor.setItemMeta(meta);
        horse.getInventory().setArmor(armor);

        NamespacedKey key = new NamespacedKey(plugin, "vehicle_id");
        String vehicleIdStr = UUID.randomUUID().toString();
        horse.getPersistentDataContainer().set(key, PersistentDataType.STRING, vehicleIdStr);

        UUID vehicleId = UUID.fromString(vehicleIdStr);
        Vehicle vehicle = new Vehicle(vehicleId, owner.getUniqueId(), type, location, 100, 100);
        vehicles.put(vehicleId, vehicle);
        playerToVehicle.put(owner.getUniqueId(), vehicleId);

        Inventory trunk = Bukkit.createInventory(null, 27, "Багажник " + type.getDisplayName());
        trunkInventories.put(vehicleId, trunk);

        dataManager.save("vehicles/" + vehicleId, vehicle);
        return vehicle;
    }

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
        return entity == null ? null : vehicles.get(entity.getUniqueId());
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

    // === GUI ЛОКАЦИЙ ===

    public void openParkingGUI(Player player, LocationData locationData) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Стоянка: " + locationData.getDisplayName());
        int slot = 0;
        for (Vehicle v : vehicles.values()) {
            if (v.getOwner().equals(player.getUniqueId())) {
                ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
                ItemMeta meta = item.getItemMeta();
                meta.setCustomModelData(v.getType().getModelId());
                meta.setDisplayName("§6" + v.getType().getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7ID: " + v.getVehicleId().toString().substring(0, 8));
                lore.add("§7Здоровье: " + v.getHealth() + "%");
                lore.add("§7Топливо: " + v.getFuel() + "%");
                lore.add("§eЛКМ — сесть, ПКМ — вызвать сюда");
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                slot++;
            }
        }
        inv.setItem(53, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onParkingClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6Стоянка:")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 53) { player.closeInventory(); return; }
        if (slot < 0 || slot >= 53) return;

        int index = 0;
        Vehicle target = null;
        for (Vehicle v : vehicles.values()) {
            if (v.getOwner().equals(player.getUniqueId())) {
                if (index == slot) {
                    target = v;
                    break;
                }
                index++;
            }
        }
        if (target == null) return;

        Entity entity = Bukkit.getEntity(target.getVehicleId());
        if (event.isLeftClick()) {
            if (entity instanceof Horse horse) {
                if (entity.getLocation().distance(player.getLocation()) < 10) {
                    boardVehicle(player, horse);
                    player.closeInventory();
                } else {
                    player.sendMessage("§cМашина слишком далеко. Подойдите ближе.");
                }
            }
        } else if (event.isRightClick()) {
            if (entity != null) {
                entity.teleport(player.getLocation());
                player.sendMessage("§aМашина вызвана к вам!");
                player.closeInventory();
            }
        }
    }

    public void openTuningGUI(Player player, LocationData locationData) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Тюнинг: " + locationData.getDisplayName());
        inv.setItem(0, createButton(Material.FURNACE, "§6Двигатель", "Уровень: 0/3, цена: 100000"));
        inv.setItem(1, createButton(Material.IRON_BARS, "§6Тормоза", "Уровень: 0/3, цена: 80000"));
        inv.setItem(2, createButton(Material.PISTON, "§6Гидравлика", "Уровень: 0/2, цена: 150000"));
        inv.setItem(3, createButton(Material.BLAZE_POWDER, "§6Нитро", "Уровень: 0/3, цена: 200000"));
        inv.setItem(4, createButton(Material.WHITE_DYE, "§6Окрас", "Выберите цвет"));
        inv.setItem(26, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onTuningClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6Тюнинг:")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) { player.closeInventory(); return; }
        if (slot < 0 || slot > 4) return;

        Vehicle vehicle = getPlayerVehicle(player);
        if (vehicle == null) {
            player.sendMessage("§cВы не в машине!");
            player.closeInventory();
            return;
        }

        ConfigurationSection tuningSection = plugin.getConfig().getConfigurationSection("tuning");
        if (tuningSection == null) return;

        switch (slot) {
            case 0 -> {
                int level = vehicle.getEngineLevel();
                if (level >= 3) { player.sendMessage("§cМаксимальный уровень!"); return; }
                int price = tuningSection.getInt("engine.price_per_level", 100000);
                if (plugin.getEconomyManager().getBalance(player) < price) { player.sendMessage("§cНедостаточно денег!"); return; }
                plugin.getEconomyManager().withdraw(player, price);
                vehicle.setEngineLevel(level + 1);
                player.sendMessage("§aДвигатель улучшен до уровня " + (level + 1));
            }
            case 1 -> {
                int level = vehicle.getBrakesLevel();
                if (level >= 3) { player.sendMessage("§cМаксимальный уровень!"); return; }
                int price = tuningSection.getInt("brakes.price_per_level", 80000);
                if (plugin.getEconomyManager().getBalance(player) < price) { player.sendMessage("§cНедостаточно денег!"); return; }
                plugin.getEconomyManager().withdraw(player, price);
                vehicle.setBrakesLevel(level + 1);
                player.sendMessage("§aТормоза улучшены до уровня " + (level + 1));
            }
            case 2 -> {
                int level = vehicle.getHydraulicsLevel();
                if (level >= 2) { player.sendMessage("§cМаксимальный уровень!"); return; }
                int price = tuningSection.getInt("hydraulics.price_per_level", 150000);
                if (plugin.getEconomyManager().getBalance(player) < price) { player.sendMessage("§cНедостаточно денег!"); return; }
                plugin.getEconomyManager().withdraw(player, price);
                vehicle.setHydraulicsLevel(level + 1);
                player.sendMessage("§aГидравлика улучшена до уровня " + (level + 1));
            }
            case 3 -> {
                int level = vehicle.getNitroLevel();
                if (level >= 3) { player.sendMessage("§cМаксимальный уровень!"); return; }
                int price = tuningSection.getInt("nitro.price_per_level", 200000);
                if (plugin.getEconomyManager().getBalance(player) < price) { player.sendMessage("§cНедостаточно денег!"); return; }
                plugin.getEconomyManager().withdraw(player, price);
                vehicle.setNitroLevel(level + 1);
                player.sendMessage("§aНитро улучшена до уровня " + (level + 1));
            }
            case 4 -> openColorSelectionGUI(player, vehicle);
        }
        player.closeInventory();
        openTuningGUI(player, null);
    }

    private void openColorSelectionGUI(Player player, Vehicle vehicle) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Выбор окраса");
        String[] colors = {"RED", "BLUE", "GREEN", "YELLOW", "BLACK", "WHITE", "ORANGE", "PURPLE"};
        for (int i = 0; i < colors.length && i < 26; i++) {
            ItemStack item = new ItemStack(Material.valueOf(colors[i] + "_LEATHER_HORSE_ARMOR"));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6" + colors[i]);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        inv.setItem(26, createButton(Material.BARRIER, "§cНазад", ""));
        player.openInventory(inv);
        selectedVehicleForTuning.put(player.getUniqueId(), vehicle.getVehicleId().toString());
    }

    @EventHandler
    public void onColorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Выбор окраса")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) {
            player.closeInventory();
            openTuningGUI(player, null);
            return;
        }
        String vehicleIdStr = selectedVehicleForTuning.get(player.getUniqueId());
        if (vehicleIdStr == null) return;
        UUID vehicleId = UUID.fromString(vehicleIdStr);
        Vehicle vehicle = vehicles.get(vehicleId);
        if (vehicle == null) return;

        Entity entity = Bukkit.getEntity(vehicleId);
        if (entity instanceof Horse horse) {
            String[] colors = {"RED", "BLUE", "GREEN", "YELLOW", "BLACK", "WHITE", "ORANGE", "PURPLE"};
            if (slot < colors.length) {
                Material armorMat = Material.valueOf(colors[slot] + "_LEATHER_HORSE_ARMOR");
                ItemStack newArmor = new ItemStack(armorMat);
                ItemStack oldArmor = horse.getInventory().getArmor();
                if (oldArmor != null && oldArmor.hasItemMeta() && oldArmor.getItemMeta().hasCustomModelData()) {
                    ItemMeta meta = newArmor.getItemMeta();
                    meta.setCustomModelData(oldArmor.getItemMeta().getCustomModelData());
                    newArmor.setItemMeta(meta);
                }
                horse.getInventory().setArmor(newArmor);
                player.sendMessage("§aЦвет изменён на " + colors[slot]);
            }
        }
        player.closeInventory();
        openTuningGUI(player, null);
    }

    public void openPoliceGUI(Player player, LocationData locationData) {
        Inventory inv = Bukkit.createInventory(null, 27, "§cПолиция: " + locationData.getDisplayName());
        inv.setItem(0, createButton(Material.BOOK, "§6Штрафы", "Список ваших штрафов"));
        inv.setItem(1, createButton(Material.PAPER, "§6Лицензии", "Купить лицензию"));
        inv.setItem(2, createButton(Material.GOLD_INGOT, "§6Оплатить штраф", "Оплатить все штрафы"));
        inv.setItem(26, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onPoliceClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§cПолиция:")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) { player.closeInventory(); return; }
        switch (slot) {
            case 0 -> player.sendMessage("§eУ вас нет штрафов.");
            case 1 -> player.sendMessage("§eЛицензии можно купить за 50,000 монет.");
            case 2 -> player.sendMessage("§eУ вас нет штрафов для оплаты.");
        }
    }

    public void openPostomatGUI(Player player, LocationData locationData) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Постомат: " + locationData.getDisplayName());
        inv.setItem(0, createButton(Material.CHEST, "§6Отправить посылку", "Выберите предмет"));
        inv.setItem(1, createButton(Material.ENDER_CHEST, "§6Получить посылку", "Получить входящие"));
        inv.setItem(26, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onPostomatClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6Постомат:")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) { player.closeInventory(); return; }
        if (slot == 0) player.sendMessage("§eФункция отправки посылок в разработке.");
        if (slot == 1) player.sendMessage("§eУ вас нет посылок.");
    }

    public void openBusinessGUI(Player player, LocationData locationData) {
        plugin.getModuleManager().getBusinessManager().openBusinessLocationGUI(player, locationData);
    }

    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void loadVehicles() {
        plugin.getLogger().info("Загрузка машин (заглушка)");
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
