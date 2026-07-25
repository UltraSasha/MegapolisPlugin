package com.megapolis.core.modules.transformers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class TransformerListener implements Listener {
    private final TransformerManager manager;
    public TransformerListener(TransformerManager manager) { this.manager = manager; }

    @EventHandler public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && manager.isTransformed(player)) manager.onTransformerExit(player);
    }
    @EventHandler public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player && manager.isTransformed(player)) manager.onTransformerExit(player);
    }
}
