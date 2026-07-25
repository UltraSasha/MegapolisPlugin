package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NewSkinCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eИспользование: /newskin <название_скина>");
            player.sendMessage("§eПример: /newskin Bumblebee");
            return true;
        }

        String skinName = args[0];
        MegapolisPlugin.getInstance()
                .getModuleManager()
                .getSkinManager()
                .createSkinItemFromCurrent(player, skinName);
        return true;
    }
}
