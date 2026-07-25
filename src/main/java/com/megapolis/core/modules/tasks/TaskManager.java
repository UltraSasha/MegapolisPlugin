package com.megapolis.core.modules.tasks;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TaskManager implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<UUID, List<Task>> playerTasks = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> taskProgress = new HashMap<>();

    public TaskManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void generateDailyTasks(Player player) {
        UUID uuid = player.getUniqueId();
        List<Task> tasks = new ArrayList<>();
        tasks.add(generateRandomTask(false));
        tasks.add(generateRandomTask(false));
        tasks.add(generateRandomTask(true));
        playerTasks.put(uuid, tasks);
        Map<String, Integer> progress = new HashMap<>();
        for (Task t : tasks) {
            progress.put(t.getObjective(), 0);
        }
        taskProgress.put(uuid, progress);
        player.sendMessage("§eПолучены новые ежедневные задания! Используйте /tasks для просмотра.");
    }

    private Task generateRandomTask(boolean business) {
        String[] simpleObjectives = {"Убей %d зомби", "Добудь %d алмазов", "Пройди %d блоков", "Съешь %d яблок"};
        String[] businessObjectives = {"Убей %d игроков на PvP-арене подряд", "Продай %d товаров", "Заработай %d монет"};

        Random rand = new Random();
        String objective;
        int amount;
        if (business) {
            String template = businessObjectives[rand.nextInt(businessObjectives.length)];
            amount = 5 + rand.nextInt(15);
            objective = String.format(template, amount);
        } else {
            String template = simpleObjectives[rand.nextInt(simpleObjectives.length)];
            amount = 10 + rand.nextInt(40);
            objective = String.format(template, amount);
        }
        int reward = business ? 50000 + rand.nextInt(100000) : 10000 + rand.nextInt(20000);
        return new Task(objective, reward, business);
    }

    public void openTasksGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§eЕжедневные задания");
        List<Task> tasks = playerTasks.get(player.getUniqueId());
        if (tasks == null) {
            generateDailyTasks(player);
            tasks = playerTasks.get(player.getUniqueId());
        }
        Map<String, Integer> progress = taskProgress.get(player.getUniqueId());
        int slot = 0;
        for (Task task : tasks) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            String status = task.isCompleted() ? "§aВыполнено!" : "§cНе выполнено";
            int prog = progress.getOrDefault(task.getObjective(), 0);
            meta.setDisplayName("§6" + task.getObjective());
            meta.setLore(Arrays.asList("§7Прогресс: §f" + prog + "/" + task.getRequiredAmount(),
                                       "§7Награда: §a" + task.getReward() + " монет",
                                       "§7Статус: " + status,
                                       task.isBusiness() ? "§cБизнес-задание" : "§fОбычное"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        String objective = "Убей %d зомби";
        updateProgress(player, objective, 1);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        String objective = "Пройди %d блоков";
        updateProgress(player, objective, 1);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() == Material.APPLE) {
            String objective = "Съешь %d яблок";
            updateProgress(player, objective, 1);
        }
    }

    private void updateProgress(Player player, String objectiveTemplate, int amount) {
        UUID uuid = player.getUniqueId();
        List<Task> tasks = playerTasks.get(uuid);
        if (tasks == null) return;
        Map<String, Integer> progress = taskProgress.get(uuid);
        if (progress == null) return;

        for (Task task : tasks) {
            if (task.isCompleted()) continue;
            String obj = task.getObjective();
            if (obj.startsWith(objectiveTemplate.replace("%d", "").trim())) {
                int current = progress.getOrDefault(obj, 0);
                current += amount;
                progress.put(obj, current);
                if (current >= task.getRequiredAmount()) {
                    task.setCompleted(true);
                    plugin.getEconomyManager().deposit(player, task.getReward());
                    player.sendMessage("§aЗадание выполнено! Вы получили " + task.getReward() + " монет.");
                }
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().equals("§eЕжедневные задания")) {
            event.setCancelled(true);
        }
    }

    public static class Task {
        private final String objective;
        private final int reward;
        private final boolean business;
        private boolean completed;

        public Task(String objective, int reward, boolean business) {
            this.objective = objective;
            this.reward = reward;
            this.business = business;
            this.completed = false;
        }

        public String getObjective() { return objective; }
        public int getReward() { return reward; }
        public boolean isBusiness() { return business; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public int getRequiredAmount() {
            String[] parts = objective.split(" ");
            for (String part : parts) {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException ignored) {}
            }
            return 0;
        }
    }
}
