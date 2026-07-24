package com.megapolis.core.modules.transport;

import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class VehicleListener implements Listener {

    private final VehicleManager vehicleManager;

    public VehicleListener(VehicleManager vehicleManager) {
        this.vehicleManager = vehicleManager;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Horse horse) {
            Player player = event.getPlayer();
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                vehicleManager.boardVehicle(player, horse);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        vehicleManager.exitVehicle(event.getPlayer());
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            vehicleManager.exitVehicle(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Horse horse) {
            Vehicle vehicle = vehicleManager.getVehicleByEntity(horse);
            if (vehicle != null) {
                event.setCancelled(true);
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    int fallDamage = (int) (event.getDamage() * 2);
                    vehicleManager.damageVehicle(vehicle, fallDamage, null);
                }
            }
        }
    }
}