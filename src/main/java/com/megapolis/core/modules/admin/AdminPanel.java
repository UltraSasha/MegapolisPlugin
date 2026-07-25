package com.megapolis.core.modules.admin;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AdminPanel implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<UUID, List<ChatMessage>> chatHistory = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    // Кэши для выбора игрока
    private final Map<UUID, String> pendingAction = new HashMap<>(); // playerId → action

    public AdminPanel(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadChatHistory();
    }

    // --- Загрузка/сохранение истории чата ---
    private void loadChatHistory() {
        // Заглушка заменена на загрузку из JSON
        Map<String, List<ChatMessage>> loaded = plugin.getDataManager().load("chat_history", Map.class);
        if (loaded != null) {
            for (Map.Entry<String, List<ChatMessage>> entry : loaded.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                chatHistory.put(uuid, entry.getValue());
            }
        }
    }

    public void saveChatHistory() {
        plugin.getDataManager().save("chat_history", chatHistory);
    }

    // --- Добавление сообщения в историю ---
    public void addChatMessage(Player player, String message) {
        ChatMessage msg = new ChatMessage(player.getName(), message, System.currentTimeMillis());
        chatHistory.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(msg);
        // Ограничиваем историю 100 сообщениями на игрока
        List<ChatMessage> history = chatHistory.get(player.getUniqueId());
        if (history.size() > 100) {
            history.remove(0);
        }
        saveChatHistory();
    }

    // === ГЛАВНОЕ МЕНЮ АДМИН-ПАНЕЛИ ===
    public void openAdminPanel(Player player) {
        if (!player.hasPermission("megapolis.admin")) {
            player.sendMessage("§cУ вас нет прав на использование этой команды.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, "§cАдмин-панель");
        inv.setItem(0, createButton(Material.BOOK, "§6Консоль", "Посмотреть последние логи"));
        inv.setItem(1, createButton(Material.PAPER, "§6История чата", "Посмотреть сообщения игроков"));
        inv.setItem(2, createButton(Material.BARRIER, "§cБан", "Забанить игрока"));
        inv.setItem(3, createButton(Material.NAME_TAG, "§6Мут", "Замутить игрока"));
        inv.setItem(4, createButton(Material.IRON_BARS, "§6Тюрьма", "Отправить в тюрьму"));
        inv.setItem(5, createButton(Material.COMPASS, "§6Телепорт", "Телепортироваться к игроку"));
        inv.setItem(6, createButton(Material.GOLD_INGOT, "§6Выдать деньги", "Выдать валюту игроку"));
        inv.setItem(26, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onAdminPanelClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§cАдмин-панель")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 26) { player.closeInventory(); return; }

        switch (slot) {
            case 0 -> showConsole(player);
            case 1 -> showChatHistoryGUI(player);
            case 2 -> startBanProcess(player);
            case 3 -> startMuteProcess(player);
            case 4 -> startJailProcess(player);
            case 5 -> startTeleportProcess(player);
            case 6 -> startGiveMoneyProcess(player);
        }
    }

    // === 1. КОНСОЛЬ (логи) ===
    private void showConsole(Player player) {
        List<String> logs = getLastConsoleLogs(30);
        Inventory inv = Bukkit.createInventory(null, 54, "§6Консоль (последние логи)");
        int slot = 0;
        for (String line : logs) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§7" + line);
            // Обрезаем длинные строки
            if (line.length() > 40) line = line.substring(0, 37) + "...";
            meta.setLore(Arrays.asList(line));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
            if (slot >= 53) break;
        }
        inv.setItem(53, createButton(Material.BARRIER, "§cНазад", "Вернуться в админ-панель"));
        player.openInventory(inv);
    }

    private List<String> getLastConsoleLogs(int lines) {
        File logFile = new File("logs/latest.log");
        List<String> logs = new ArrayList<>();
        if (!logFile.exists()) {
            logs.add("§cФайл логов не найден.");
            return logs;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            List<String> allLines = reader.lines().collect(Collectors.toList());
            int start = Math.max(0, allLines.size() - lines);
            for (int i = start; i < allLines.size(); i++) {
                logs.add(allLines.get(i));
            }
        } catch (IOException e) {
            logs.add("§cОшибка чтения логов: " + e.getMessage());
        }
        return logs;
    }

    @EventHandler
    public void onConsoleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Консоль (последние логи)")) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 53) {
            player.closeInventory();
            openAdminPanel(player);
        }
    }

    // === 2. ИСТОРИЯ ЧАТА ===
    private void showChatHistoryGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6История чата");
        int slot = 0;
        // Показываем последние 50 сообщений от всех игроков
        List<ChatMessage> allMessages = new ArrayList<>();
        for (List<ChatMessage> msgs : chatHistory.values()) {
            allMessages.addAll(msgs);
        }
        allMessages.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        int start = Math.max(0, allMessages.size() - 50);
        for (int i = start; i < allMessages.size(); i++) {
            ChatMessage msg = allMessages.get(i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            String time = dateFormat.format(new Date(msg.timestamp));
            meta.setDisplayName("§e" + msg.sender + " §7(" + time + ")");
            meta.setLore(Arrays.asList("§f" + msg.message));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
            if (slot >= 53) break;
        }
        inv.setItem(53, createButton(Material.BARRIER, "§cНазад", "Вернуться в админ-панель"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onChatHistoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6История чата")) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 53) {
            player.closeInventory();
            openAdminPanel(player);
        }
    }

    // === 3. БАН ===
    private void startBanProcess(Player player) {
        player.closeInventory();
        player.sendMessage("§eВведите имя игрока для бана (или 'отмена' для отмены):");
        pendingAction.put(player.getUniqueId(), "ban");
    }

    public void handleBanInput(Player admin, String input) {
        if (input.equalsIgnoreCase("отмена")) {
            pendingAction.remove(admin.getUniqueId());
            admin.sendMessage("§cДействие отменено.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        if (target == null || !target.hasPlayedBefore()) {
            admin.sendMessage("§cИгрок не найден.");
            return;
        }
        // Выполняем бан через консольную команду (EssentialsX или Vanilla)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + target.getName() + " Нарушение правил");
        admin.sendMessage("§aИгрок " + target.getName() + " забанен.");
        pendingAction.remove(admin.getUniqueId());
    }

    // === 4. МУТ ===
    private void startMuteProcess(Player player) {
        player.closeInventory();
        player.sendMessage("§eВведите имя игрока для мута (или 'отмена'):");
        pendingAction.put(player.getUniqueId(), "mute");
    }

    public void handleMuteInput(Player admin, String input) {
        if (input.equalsIgnoreCase("отмена")) {
            pendingAction.remove(admin.getUniqueId());
            admin.sendMessage("§cДействие отменено.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        if (target == null || !target.hasPlayedBefore()) {
            admin.sendMessage("§cИгрок не найден.");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mute " + target.getName() + " 1h");
        admin.sendMessage("§aИгрок " + target.getName() + " замучен на 1 час.");
        pendingAction.remove(admin.getUniqueId());
    }

    // === 5. ТЮРЬМА ===
    private void startJailProcess(Player player) {
        player.closeInventory();
        player.sendMessage("§eВведите имя игрока для отправки в тюрьму (или 'отмена'):");
        pendingAction.put(player.getUniqueId(), "jail");
    }

    public void handleJailInput(Player admin, String input) {
        if (input.equalsIgnoreCase("отмена")) {
            pendingAction.remove(admin.getUniqueId());
            admin.sendMessage("§cДействие отменено.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        if (target == null || !target.hasPlayedBefore()) {
            admin.sendMessage("§cИгрок не найден.");
            return;
        }
        // Используем команду jail (EssentialsX)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "jail " + target.getName() + " adm_jail 10m");
        admin.sendMessage("§aИгрок " + target.getName() + " отправлен в тюрьму на 10 минут.");
        pendingAction.remove(admin.getUniqueId());
    }

    // === 6. ТЕЛЕПОРТ ===
    private void startTeleportProcess(Player player) {
        player.closeInventory();
        player.sendMessage("§eВведите имя игрока для телепортации (или 'отмена'):");
        pendingAction.put(player.getUniqueId(), "teleport");
    }

    public void handleTeleportInput(Player admin, String input) {
        if (input.equalsIgnoreCase("отмена")) {
            pendingAction.remove(admin.getUniqueId());
            admin.sendMessage("§cДействие отменено.");
            return;
        }
        Player target = Bukkit.getPlayer(input);
        if (target == null) {
            admin.sendMessage("§cИгрок не в сети.");
            return;
        }
        admin.teleport(target);
        admin.sendMessage("§aВы телепортированы к " + target.getName());
        pendingAction.remove(admin.getUniqueId());
    }

    // === 7. ВЫДАЧА ДЕНЕГ ===
    private void startGiveMoneyProcess(Player player) {
        player.closeInventory();
        player.sendMessage("§eВведите сумму для выдачи в чат (или 'отмена'):");
        pendingAction.put(player.getUniqueId(), "givemoney");
    }

    public void handleGiveMoneyInput(Player admin, String input) {
        if (input.equalsIgnoreCase("отмена")) {
            pendingAction.remove(admin.getUniqueId());
            admin.sendMessage("§cДействие отменено.");
            return;
        }
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                admin.sendMessage("§cСумма должна быть больше 0.");
                return;
            }
            // Выдаём деньги через экономику
            plugin.getEconomyManager().deposit(admin, amount);
            admin.sendMessage("§aВы выдали себе " + amount + " " + plugin.getConfig().getString("main_currency", "RUB"));
            pendingAction.remove(admin.getUniqueId());
        } catch (NumberFormatException e) {
            admin.sendMessage("§cВведите корректное число.");
        }
    }

    // === ОБРАБОТЧИК ВВОДА В ЧАТЕ ===
    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.PlayerChatEvent event) {
        // Этот метод обрабатывает ввод для админ-панели
        // Используем низкоуровневый слушатель, чтобы перехватывать сообщения
    }

    // Вспомогательный метод для обработки ввода из чата (вызывается из другого слушателя)
    public void processAdminInput(Player player, String message) {
        String action = pendingAction.get(player.getUniqueId());
        if (action == null) return;
        switch (action) {
            case "ban" -> handleBanInput(player, message);
            case "mute" -> handleMuteInput(player, message);
            case "jail" -> handleJailInput(player, message);
            case "teleport" -> handleTeleportInput(player, message);
            case "givemoney" -> handleGiveMoneyInput(player, message);
            default -> pendingAction.remove(player.getUniqueId());
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===
    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // === ВНУТРЕННИЙ КЛАСС ДЛЯ СООБЩЕНИЙ ЧАТА ===
    private static class ChatMessage {
        String sender;
        String message;
        long timestamp;

        public ChatMessage(String sender, String message, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
        }
