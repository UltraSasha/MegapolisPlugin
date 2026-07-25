package com.megapolis.core.modules.messenger;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class MessengerManager implements Listener {
    private final MegapolisPlugin plugin;
    public MessengerManager(MegapolisPlugin plugin) { this.plugin = plugin; }
    public void openMessengerGUI(Player player) { player.sendMessage("§eМессенджер в разработке."); }
}
