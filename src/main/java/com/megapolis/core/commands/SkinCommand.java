package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkinCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return true; }
        if (args.length == 0) { player.sendMessage("§eИспользование: /skin <имя_скина>"); return true; }
        MegapolisPlugin.getInstance().getModuleManager().getSkinManager().applySkin(player, args[0]);
        return true;
    }
}
