package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkinCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eИспользование: /skin <имя_скина>");
            player.sendMessage("§eПример: /skin Bumblebee");
            return true;
        }

        String skinName = args[0];
        boolean success = MegapolisPlugin.getInstance()
                .getModuleManager()
                .getSkinManager()
                .applySkin(player, skinName);

        if (!success) {
            player.sendMessage("§cНе удалось применить скин '" + skinName + "'.");
        }
        return true;
    }
}
