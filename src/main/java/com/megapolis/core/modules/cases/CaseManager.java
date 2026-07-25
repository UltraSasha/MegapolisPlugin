package com.megapolis.core.modules.cases;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class CaseManager implements Listener {
    private final MegapolisPlugin plugin;
    public CaseManager(MegapolisPlugin plugin) { this.plugin = plugin; }
    public void openCasesGUI(Player player) { player.sendMessage("§eКейсы в разработке."); }
}
