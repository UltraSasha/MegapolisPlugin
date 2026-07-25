package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.modules.transport.VehicleType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VehicleSpawnCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eИспользование: /vehicle <тип>");
            player.sendMessage("§eТипы: CAR, MOTORCYCLE, BOAT, HELICOPTER");
            return true;
        }
        try {
            VehicleType type = VehicleType.valueOf(args[0].toUpperCase());
            MegapolisPlugin.getInstance().getModuleManager().getVehicleManager()
                    .spawnVehicle(player, type, player.getLocation());
            player.sendMessage("§aМашина создана!");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cНеизвестный тип транспорта.");
        }
        return true;
    }
}
