package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MegapolisCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            MegapolisPlugin.getInstance().reloadConfig();
            sender.sendMessage("§aКонфигурация перезагружена!");
            return true;
        }
        sender.sendMessage("§eИспользование: /megapolis reload");
        return true;
    }
}
