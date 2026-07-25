package com.megapolis.core.modules.transformers;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.modules.transport.Vehicle;
import com.megapolis.core.modules.transport.VehicleType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransformerManager {

    private final MegapolisPlugin plugin;
    private final Map<UUID, Boolean> transformerActive = new HashMap<>();
    private final Map<UUID, UUID> transformedToVehicle = new HashMap<>(); // playerId → vehicleId

    public TransformerManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggleTransformer(Player player) {
        UUID playerId = player.getUniqueId();
        boolean isActive = transformerActive.getOrDefault(playerId, false);

        if (isActive) {
            revertToSkin(player);
        } else {
            transformToVehicle(player);
        }
    }

    private void transformToVehicle(Player player) {
        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // 1. Скрываем игрока
        player.setInvisible(true);
        player.setInvulnerable(true);

        // 2. Создаём лошадь-трансформер
        Horse horse = world.spawn(loc, Horse.class);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setDomestication(1);
        horse.setCustomName("Трансформер");
        horse.setCustomNameVisible(false);

        // Устанавливаем модель (5001 — ID для трансформера)
        ItemStack armor = new ItemStack(org.bukkit.Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = armor.getItemMeta();
        meta.setCustomModelData(5001);
        armor.setItemMeta(meta);
        horse.getInventory().setArmor(armor);

        // 3. Регистрируем машину в VehicleManager (как обычную, но с типом CAR)
        // Чтобы ПКМ по ней работал, нужно зарегистрировать её в VehicleManager
        // Для этого создаём Vehicle и добавляем в Map
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_id");
        String vehicleIdStr = UUID.randomUUID().toString();
        horse.getPersistentDataContainer().set(key, PersistentDataType.STRING, vehicleIdStr);

        UUID vehicleId = UUID.fromString(vehicleIdStr);
        // Используем тип CAR, так как трансформер — это машина
        Vehicle vehicle = new Vehicle(vehicleId, player.getUniqueId(), VehicleType.CAR, loc, 100, 100);
        // Добавляем в VehicleManager (через reflection или публичный метод)
        plugin.getModuleManager().getVehicleManager().registerVehicle(vehicle, horse);

        // 4. Сажаем игрока на лошадь (автоматически)
        horse.addPassenger(player);

        // Сохраняем связь
        transformedToVehicle.put(playerId, vehicleId);
        transformerActive.put(playerId, true);

        // 5. Эффекты
        spawnTransformationParticles(loc, 100);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        player.sendMessage("§6Вы превратились в машину! Нажмите Shift, чтобы выйти.");
    }

    private void revertToSkin(Player player) {
        UUID playerId = player.getUniqueId();
        UUID vehicleId = transformedToVehicle.remove(playerId);
        if (vehicleId == null) return;

        Entity vehicle = Bukkit.getEntity(vehicleId);
        Location loc = player.getLocation();
        if (vehicle != null) {
            loc = vehicle.getLocation();
            vehicle.remove();
        }

        // Удаляем из VehicleManager
        plugin.getModuleManager().getVehicleManager().unregisterVehicle(vehicleId);

        player.setInvisible(false);
        player.setInvulnerable(false);

        // Восстанавливаем скин через SkinRestorer
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin update " + player.getName());

        spawnTransformationParticles(loc, 100);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f);

        transformerActive.put(playerId, false);
        player.sendMessage("§eВы вернулись в человеческий облик.");
    }

    private void spawnTransformationParticles(Location loc, int count) {
        World world = loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION_HUGE, loc, 5);
        world.spawnParticle(Particle.CLOUD, loc, count, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.FLAME, loc, count / 2, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.ENCHANTMENT_TABLE, loc, count / 2, 2, 2, 2, 0.5);
    }

    public boolean isTransformed(Player player) {
        return transformerActive.getOrDefault(player.getUniqueId(), false);
    }

    public Entity getTransformerVehicle(Player player) {
        UUID vehicleId = transformedToVehicle.get(player.getUniqueId());
        return vehicleId == null ? null : Bukkit.getEntity(vehicleId);
    }

    public void onTransformerExit(Player player) {
        if (isTransformed(player)) {
            revertToSkin(player);
        }
    }
}
