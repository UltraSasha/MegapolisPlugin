package com.megapolis.core.commands;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NewSkinCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eИспользование: /newskin <название_скина>");
            player.sendMessage("§eПример: /newskin МойБамблби");
            return true;
        }

        String skinName = args[0];
        // Проверка на длину
        if (skinName.length() > 32) {
            player.sendMessage("§cНазвание скина не должно превышать 32 символа.");
            return true;
        }

        // Создаём предмет-скин, запоминающий текущий скин игрока
        ItemStack skinItem = MegapolisPlugin.getInstance()
                .getModuleManager()
                .getSkinManager()
                .createSkinItemFromCurrent(player, skinName);

        if (skinItem == null) {
            player.sendMessage("§cНе удалось создать предмет-скин.");
            return true;
        }

        // Проверка свободного места в инвентаре
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cВаш инвентарь полон!");
            return true;
        }

        player.getInventory().addItem(skinItem);
        player.sendMessage("§aПредмет-скин '" + skinName + "' создан! Используйте ПКМ по нему, чтобы применить запомненный скин.");
        return true;
    }
}