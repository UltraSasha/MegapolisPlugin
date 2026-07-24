package com.megapolis.core.modules.business;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class BusinessManager implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<UUID, Business> businesses = new HashMap<>();
    private final Map<UUID, List<UUID>> businessEmployees = new HashMap<>(); // бизнес → список игроков

    public BusinessManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadBusinesses();
        // Создаём стартовую строительную компанию для Deezercraft2024
        createStartupCompany();
    }

    private void createStartupCompany() {
        Player owner = Bukkit.getPlayer("Deezercraft2024");
        if (owner == null) owner = Bukkit.getOfflinePlayer("Deezercraft2024").getPlayer();
        if (owner != null) {
            createBusiness(owner, "Megapolis Строй", "СТРОИТЕЛЬНАЯ", 10000000);
        }
    }

    private void loadBusinesses() {
        // Загрузка из data/businesses.json
        plugin.getLogger().info("Загрузка бизнесов...");
    }

    public void saveAll() {
        // Сохранение в data/businesses.json
        plugin.getDataManager().save("businesses", businesses);
    }

    // Создание бизнеса
    public boolean createBusiness(Player owner, String name, String type, double initialCapital) {
        UUID id = UUID.randomUUID();
        Business business = new Business(id, owner.getUniqueId(), name, type, initialCapital);
        businesses.put(id, business);
        // Добавляем владельца как сотрудника
        businessEmployees.computeIfAbsent(id, k -> new ArrayList<>()).add(owner.getUniqueId());
        owner.sendMessage("§aБизнес " + name + " создан! Тип: " + type);
        return true;
    }

    // Получение бизнеса по ID
    public Business getBusiness(UUID id) { return businesses.get(id); }

    // Получение бизнеса по владельцу
    public Business getBusinessByOwner(Player player) {
        for (Business b : businesses.values()) {
            if (b.getOwner().equals(player.getUniqueId())) return b;
        }
        return null;
    }

    // === Управление казной ===
    public void depositTreasury(Business business, double amount) {
        business.setTreasury(business.getTreasury() + amount);
    }

    public boolean withdrawTreasury(Business business, double amount, Player requester) {
        if (!business.getOwner().equals(requester.getUniqueId())) {
            requester.sendMessage("§cТолько владелец может снимать деньги с казны.");
            return false;
        }
        if (business.getTreasury() < amount) {
            requester.sendMessage("§cНедостаточно средств в казне.");
            return false;
        }
        business.setTreasury(business.getTreasury() - amount);
        plugin.getEconomyManager().deposit(requester, amount);
        requester.sendMessage("§aВы сняли " + amount + " монет из казны бизнеса.");
        return true;
    }

    // === Сотрудники ===
    public void addEmployee(Business business, Player employee, Player owner) {
        if (!business.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage("§cТолько владелец может нанимать сотрудников.");
            return;
        }
        businessEmployees.computeIfAbsent(business.getId(), k -> new ArrayList<>()).add(employee.getUniqueId());
        employee.sendMessage("§aВы стали сотрудником бизнеса " + business.getName());
    }

    public void removeEmployee(Business business, Player employee, Player owner) {
        if (!business.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage("§cТолько владелец может увольнять сотрудников.");
            return;
        }
        businessEmployees.getOrDefault(business.getId(), new ArrayList<>()).remove(employee.getUniqueId());
        employee.sendMessage("§cВас уволили из бизнеса " + business.getName());
    }

    public boolean isEmployee(Business business, Player player) {
        return businessEmployees.getOrDefault(business.getId(), new ArrayList<>()).contains(player.getUniqueId());
    }

    // === Аукцион (выставление бизнеса) ===
    public void auctionBusiness(Business business) {
        if (business.isBankrupt()) {
            // Передаём бизнес новому владельцу (упрощённо - первому, кто заплатит)
            // В реальности здесь будет аукцион
            plugin.getLogger().info("Бизнес " + business.getName() + " выставлен на аукцион.");
        }
    }

    // Проверка банкротства (вызывать при изменениях казны)
    public void checkBankruptcy(Business business) {
        if (business.getTreasury() < 0 && !business.isBankrupt()) {
            business.setBankrupt(true);
            // Запускаем таймер на 72 часа
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (business.getTreasury() < 0) {
                    auctionBusiness(business);
                }
            }, 72 * 60 * 60 * 20); // 72 часа в тиках
        }
    }

    // === GUI для управления бизнесом ===
    public void openBusinessGUI(Player player) {
        Business business = getBusinessByOwner(player);
        if (business == null) {
            player.sendMessage("§cУ вас нет бизнеса.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, "§6Бизнес: " + business.getName());
        inv.setItem(0, createInfoItem(Material.DIAMOND, "Казна", "§e" + business.getTreasury() + " монет"));
        inv.setItem(1, createInfoItem(Material.PLAYER_HEAD, "Сотрудники", "§7Количество: " + businessEmployees.getOrDefault(business.getId(), new ArrayList<>()).size()));
        inv.setItem(2, createButton(Material.GOLD_INGOT, "Снять деньги", "Снять с казны (владелец)"));
        inv.setItem(3, createButton(Material.BOOK, "Управление сотрудниками", "Нанять/уволить"));
        inv.setItem(4, createButton(Material.BARRIER, "Закрыть", "Закрыть меню"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onBusinessGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6Бизнес:")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        Business business = getBusinessByOwner(player);
        if (business == null) return;

        if (slot == 2) {
            // Снятие денег (упрощённо)
            player.closeInventory();
            // Запрашиваем сумму через чат (заглушку заменяем на диалог)
            player.sendMessage("§eВведите сумму для снятия в чат (число).");
            // В реальном коде здесь будет ожидание ввода
        } else if (slot == 3) {
            // Управление сотрудниками
            openEmployeesGUI(player, business);
        } else if (slot == 4) {
            player.closeInventory();
        }
    }

    private void openEmployeesGUI(Player player, Business business) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Сотрудники");
        List<UUID> employees = businessEmployees.getOrDefault(business.getId(), new ArrayList<>());
        int slot = 0;
        for (UUID empId : employees) {
            String name = Bukkit.getOfflinePlayer(empId).getName();
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§a" + name);
            meta.setLore(Collections.singletonList("§7Кликните для увольнения"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        inv.setItem(26, createButton(Material.BARRIER, "Назад", "Вернуться в бизнес-меню"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onEmployeeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Сотрудники")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 26) { openBusinessGUI(player); return; }

        Business business = getBusinessByOwner(player);
        if (business == null) return;

        List<UUID> employees = businessEmployees.getOrDefault(business.getId(), new ArrayList<>());
        if (slot >= 0 && slot < employees.size()) {
            UUID empId = employees.get(slot);
            Player emp = Bukkit.getPlayer(empId);
            if (emp != null) {
                removeEmployee(business, emp, player);
                openEmployeesGUI(player, business);
            }
        }
    }

    private ItemStack createInfoItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // === Класс бизнеса ===
    public static class Business {
        private final UUID id;
        private final UUID owner;
        private final String name;
        private final String type;
        private double treasury;
        private boolean bankrupt;

        public Business(UUID id, UUID owner, String name, String type, double treasury) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.type = type;
            this.treasury = treasury;
            this.bankrupt = false;
        }

        public UUID getId() { return id; }
        public UUID getOwner() { return owner; }
        public String getName() { return name; }
        public String getType() { return type; }
        public double getTreasury() { return treasury; }
        public void setTreasury(double treasury) { this.treasury = treasury; }
        public boolean isBankrupt() { return bankrupt; }
        public void setBankrupt(boolean bankrupt) { this.bankrupt = bankrupt; }
    }
}