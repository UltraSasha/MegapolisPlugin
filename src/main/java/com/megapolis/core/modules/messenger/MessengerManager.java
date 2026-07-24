package com.megapolis.core.modules.messenger;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MessengerManager implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<UUID, List<Message>> privateMessages = new HashMap<>();
    private final Map<String, List<Message>> groupChats = new HashMap<>(); // название чата → сообщения
    private final Map<UUID, UUID> currentDialog = new HashMap<>(); // игрок → с кем переписка
    private final Map<UUID, String> currentGroup = new HashMap<>(); // игрок → название группы

    public MessengerManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Создаём стандартные чаты
        groupChats.put("Общий", new ArrayList<>());
        groupChats.put("Объявления", new ArrayList<>());
        groupChats.put("Помощь", new ArrayList<>());
    }

    // === Открыть GUI мессенджера ===
    public void openMessengerGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§6Мессенджер");
        inv.setItem(0, createButton(Material.WRITABLE_BOOK, "Личные сообщения", "Написать игроку"));
        inv.setItem(1, createButton(Material.BOOK, "Групповые чаты", "Общий, объявления, помощь"));
        inv.setItem(2, createButton(Material.PAPER, "Непрочитанные", "Просмотреть новые сообщения"));
        inv.setItem(3, createButton(Material.BARRIER, "Закрыть", "Закрыть мессенджер"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onMessengerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Мессенджер")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        switch (slot) {
            case 0 -> openPrivateChats(player);
            case 1 -> openGroupChats(player);
            case 2 -> showUnread(player);
            case 3 -> player.closeInventory();
        }
    }

    // === Личные сообщения ===
    private void openPrivateChats(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Личные сообщения");
        // Список всех игроков (кроме себя)
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(player)) continue;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§a" + p.getName());
            meta.setLore(Collections.singletonList("§7Нажмите для начала диалога"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        inv.setItem(53, createButton(Material.BARRIER, "Назад", "Вернуться в мессенджер"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onPrivateChatClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Личные сообщения")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 53) { openMessengerGUI(player); return; }
        if (slot < 0 || slot >= Bukkit.getOnlinePlayers().size() - 1) return;

        Player target = (Player) Bukkit.getOnlinePlayers().toArray()[slot];
        if (target == null || target.equals(player)) return;

        currentDialog.put(player.getUniqueId(), target.getUniqueId());
        player.closeInventory();
        player.sendMessage("§eВы начали диалог с " + target.getName() + ". Пишите в чат для отправки сообщения.");
        player.sendMessage("§eДля выхода из диалога напишите /msg exit");
    }

    // Обработка входящих сообщений в чате
    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage();

        // Проверка, находится ли игрок в личном диалоге
        if (currentDialog.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            UUID targetId = currentDialog.get(player.getUniqueId());
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.sendMessage("§6[ЛС] " + player.getName() + ": " + msg);
                player.sendMessage("§6[ЛС] Вы -> " + target.getName() + ": " + msg);
                // Сохраняем сообщение
                savePrivateMessage(player, target, msg);
            } else {
                player.sendMessage("§cИгрок вышел из игры.");
                currentDialog.remove(player.getUniqueId());
            }
            return;
        }

        // Проверка групповых чатов
        if (currentGroup.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String groupName = currentGroup.get(player.getUniqueId());
            List<Message> chat = groupChats.get(groupName);
            if (chat != null) {
                String formatted = "§7[" + groupName + "] §f" + player.getName() + ": " + msg;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (currentGroup.containsKey(p.getUniqueId()) && currentGroup.get(p.getUniqueId()).equals(groupName)) {
                        p.sendMessage(formatted);
                    }
                }
                chat.add(new Message(player.getUniqueId(), msg, new Date()));
            }
            return;
        }
    }

    private void savePrivateMessage(Player from, Player to, String text) {
        Message msg = new Message(from.getUniqueId(), text, new Date());
        privateMessages.computeIfAbsent(from.getUniqueId(), k -> new ArrayList<>()).add(msg);
        privateMessages.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>()).add(msg);
    }

    // === Групповые чаты ===
    private void openGroupChats(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Групповые чаты");
        int slot = 0;
        for (String name : groupChats.keySet()) {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6" + name);
            meta.setLore(Collections.singletonList("§7Сообщений: " + groupChats.get(name).size()));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        inv.setItem(26, createButton(Material.BARRIER, "Назад", "Вернуться в мессенджер"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onGroupChatClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Групповые чаты")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 26) { openMessengerGUI(player); return; }
        if (slot >= groupChats.size()) return;

        String groupName = (String) groupChats.keySet().toArray()[slot];
        currentGroup.put(player.getUniqueId(), groupName);
        player.closeInventory();
        player.sendMessage("§eВы присоединились к чату " + groupName + ". Пишите в чат для отправки.");
        player.sendMessage("§eДля выхода напишите /group exit");
    }

    // === Непрочитанные сообщения ===
    private void showUnread(Player player) {
        List<Message> unread = new ArrayList<>();
        for (Message msg : privateMessages.getOrDefault(player.getUniqueId(), new ArrayList<>())) {
            if (!msg.isRead() && !msg.getSender().equals(player.getUniqueId())) {
                unread.add(msg);
            }
        }
        if (unread.isEmpty()) {
            player.sendMessage("§eНепрочитанных сообщений нет.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, "§6Непрочитанные");
        int slot = 0;
        for (Message msg : unread) {
            String senderName = Bukkit.getOfflinePlayer(msg.getSender()).getName();
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6От: " + senderName);
            meta.setLore(Arrays.asList("§7" + msg.getText(), "§7" + msg.getDate()));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            msg.setRead(true);
            slot++;
        }
        player.openInventory(inv);
    }

    // === Команды для выхода из диалога/группы ===
    public void exitDialog(Player player) {
        currentDialog.remove(player.getUniqueId());
        currentGroup.remove(player.getUniqueId());
        player.sendMessage("§eВы вышли из диалога/группового чата.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        currentDialog.remove(player.getUniqueId());
        currentGroup.remove(player.getUniqueId());
    }

    // === Вспомогательные классы ===
    private static class Message {
        private final UUID sender;
        private final String text;
        private final Date date;
        private boolean read;

        public Message(UUID sender, String text, Date date) {
            this.sender = sender;
            this.text = text;
            this.date = date;
            this.read = false;
        }

        public UUID getSender() { return sender; }
        public String getText() { return text; }
        public Date getDate() { return date; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
    }

    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }
}