package com.megapolis.core.modules.events;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class EventManager {

    private final MegapolisPlugin plugin;
    private final Map<String, CustomEvent> activeEvents = new HashMap<>();

    public EventManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerEvent(String eventId, long durationTicks, Consumer<Player> onStart, Consumer<Player> onEnd) {
        if (activeEvents.containsKey(eventId)) return;

        CustomEvent event = new CustomEvent(eventId, durationTicks, onStart, onEnd);
        activeEvents.put(eventId, event);
        event.start();

        plugin.getLogger().info("Ивент " + eventId + " запущен на " + (durationTicks / 20) + " сек.");
    }

    public void stopEvent(String eventId) {
        CustomEvent event = activeEvents.remove(eventId);
        if (event != null) {
            event.stop();
        }
    }

    public boolean isEventActive(String eventId) {
        return activeEvents.containsKey(eventId);
    }

    private class CustomEvent {
        private final String id;
        private final long duration;
        private final Consumer<Player> onStart;
        private final Consumer<Player> onEnd;
        private BukkitRunnable task;

        public CustomEvent(String id, long duration, Consumer<Player> onStart, Consumer<Player> onEnd) {
            this.id = id;
            this.duration = duration;
            this.onStart = onStart;
            this.onEnd = onEnd;
        }

        public void start() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                onStart.accept(p);
            }

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        onEnd.accept(p);
                    }
                    activeEvents.remove(id);
                    plugin.getLogger().info("Ивент " + id + " завершён.");
                }
            };
            task.runTaskLater(plugin, duration);
        }

        public void stop() {
            if (task != null) {
                task.cancel();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    onEnd.accept(p);
                }
                activeEvents.remove(id);
            }
        }
    }
}