package com.megapolis.core.modules.events;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.event.Listener;

public class EventManager implements Listener {
    private final MegapolisPlugin plugin;
    public EventManager(MegapolisPlugin plugin) { this.plugin = plugin; }
}
