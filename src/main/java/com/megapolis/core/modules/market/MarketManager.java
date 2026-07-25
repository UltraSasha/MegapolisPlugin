package com.megapolis.core.modules.market;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager implements Listener {

    private final MegapolisPlugin plugin;

    // Аукцион предметов
    private final Map<String, AuctionLot> activeAuctions = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> sellItemBuffer = new HashMap<>(); // временный буфер для выставления

    // Аукцион машин (от игроков)
    private final Map<UUID, VehicleAuctionLot> vehicleAuctions = new HashMap<>(); // vehicleId → лот

    public MarketManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadAuctions();
        loadVehicleAuctions();
    }

    // ============================================================
    // АУКЦИОН ПРЕДМЕТОВ (обычный)
    // ============================================================

    public void openMarketGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§dАукцион");

        // Кнопка "Выставить предмет"
        ItemStack sellBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sellBtn.getItemMeta();
        sellMeta.setDisplayName("§aВыставить предмет");
        sellMeta.setLore(Arrays.asList("§7Держите предмет в руке и нажмите"));
        sellBtn.setItemMeta(sellMeta);
        inv.setItem(0, sellBtn);

        // Кнопка "Мои лоты"
        ItemStack myBtn = new ItemStack(Material.BOOK);
        ItemMeta myMeta = myBtn.getItemMeta();
        myMeta.setDisplayName("§6Мои лоты");
        myMeta.setLore(Arrays.asList("§7Посмотреть свои активные лоты"));
        myBtn.setItemMeta(myMeta);
        inv.setItem(1, myBtn);

        // Заполняем остальные слоты активными лотами
        int slot = 9;
        for (AuctionLot lot : activeAuctions.values()) {
            if (slot >= 53) break;
            ItemStack display = lot.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add("§7Продавец: §f" + Bukkit.getOfflinePlayer(lot.getSeller()).getName());
            lore.add("§7Цена: §a" + lot.getPrice() + " монет");
            lore.add("§eЛКМ — купить мгновенно");
            lore.add("§eShift+ЛКМ — сделать ставку (торг)");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
            slot++;
        }

        // Кнопка закрытия
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    // --- Выставление предмета ---
    public void startSellProcess(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cДержите в руке предмет, который хотите продать.");
            return;
        }
        sellItemBuffer.put(player.getUniqueId(), item.clone());
        player.closeInventory();
        player.sendMessage("§eВведите цену в чат (только число) или напишите 'отмена' для отмены.");
        // Ожидаем ввод цены в чате (обработчик ниже)
    }

    public void handlePriceInput(Player player, String message) {
        if (message.equalsIgnoreCase("отмена")) {
            sellItemBuffer.remove(player.getUniqueId());
            player.sendMessage("§cВы отменили выставление предмета.");
            return;
        }
        try {
            double price = Double.parseDouble(message);
            if (price <= 0) {
                player.sendMessage("§cЦена должна быть больше 0.");
                return;
            }
            ItemStack item = sellItemBuffer.remove(player.getUniqueId());
            if (item == null) {
                player.sendMessage("§cОшибка: предмет не найден. Попробуйте снова.");
                return;
            }
            // Убираем предмет из рук игрока
            player.getInventory().setItemInMainHand(null);
            // Создаём лот
            String auctionId = UUID.randomUUID().toString();
            AuctionLot lot = new AuctionLot(auctionId, player.getUniqueId(), item, price, System.currentTimeMillis());
            activeAuctions.put(auctionId, lot);
            saveAuctions();
            player.sendMessage("§aПредмет выставлен на аукцион за " + price + " монет!");
            Bukkit.broadcastMessage("§6[Аукцион] §e" + player.getName() + " §fвыставил §e" + item.getType().name() + " §fза §a" + price + " §fмонет.");
        } catch (NumberFormatException e) {
            player.sendMessage("§cВведите корректное число.");
        }
    }

    // --- Обработка кликов в аукционе ---
    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§dАукцион")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 0) {
            startSellProcess(player);
            player.closeInventory();
            return;
        }
        if (slot == 1) {
            openMyLots(player);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot >= 9 && slot < 53) {
            int index = slot - 9;
            if (index >= activeAuctions.size()) return;
            String auctionId = (String) activeAuctions.keySet().toArray()[index];
            AuctionLot lot = activeAuctions.get(auctionId);
            if (lot == null) return;

            if (lot.getSeller().equals(player.getUniqueId())) {
                player.sendMessage("§cВы не можете купить свой собственный лот.");
                return;
            }

            double balance = plugin.getEconomyManager().getBalance(player);
            if (event.isShiftClick()) {
                // Shift+ЛКМ — ставка (торг) — покупаем по цене + 10%
                double bidPrice = lot.getPrice() * 1.1;
                if (balance < bidPrice) {
                    player.sendMessage("§cНедостаточно средств для ставки. Нужно: " + bidPrice);
                    return;
                }
                plugin.getEconomyManager().withdraw(player, bidPrice);
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lot.getSeller()), bidPrice);
                player.getInventory().addItem(lot.getItem());
                activeAuctions.remove(auctionId);
                saveAuctions();
                player.sendMessage("§aВы выиграли торг и купили предмет за " + bidPrice + " монет!");
                player.closeInventory();
            } else {
                // ЛКМ — мгновенная покупка
                if (balance < lot.getPrice()) {
                    player.sendMessage("§cНедостаточно средств. Нужно: " + lot.getPrice());
                    return;
                }
                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage("§cВаш инвентарь полон!");
                    return;
                }
                plugin.getEconomyManager().withdraw(player, lot.getPrice());
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lot.getSeller()), lot.getPrice());
                player.getInventory().addItem(lot.getItem());
                activeAuctions.remove(auctionId);
                saveAuctions();
                player.sendMessage("§aВы купили предмет за " + lot.getPrice() + " монет!");
                player.closeInventory();
            }
        }
    }

    // --- Мои лоты ---
    private void openMyLots(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Мои лоты");
        int slot = 0;
        for (AuctionLot lot : activeAuctions.values()) {
            if (lot.getSeller().equals(player.getUniqueId())) {
                ItemStack display = lot.getItem().clone();
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                lore.add("§7Цена: §a" + lot.getPrice() + " монет");
                lore.add("§cНажмите, чтобы снять с продажи");
                meta.setLore(lore);
                display.setItemMeta(meta);
                inv.setItem(slot, display);
                slot++;
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onMyLotsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Мои лоты")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= activeAuctions.size()) return;
        int index = 0;
        String targetId = null;
        for (AuctionLot lot : activeAuctions.values()) {
            if (lot.getSeller().equals(player.getUniqueId())) {
                if (index == slot) {
                    targetId = lot.getAuctionId();
                    break;
                }
                index++;
            }
        }
        if (targetId == null) return;
        AuctionLot lot = activeAuctions.remove(targetId);
        if (lot != null) {
            player.getInventory().addItem(lot.getItem());
            saveAuctions();
            player.sendMessage("§aВы сняли лот с продажи.");
            player.closeInventory();
        }
    }

    // ============================================================
    // АУКЦИОН МАШИН (от игроков)
    // ============================================================

    public void openVehicleAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Авторынок (игроки)");
        int slot = 0;
        for (VehicleAuctionLot lot : vehicleAuctions.values()) {
            if (slot >= 53) break;
            ItemStack display = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = display.getItemMeta();
            meta.setCustomModelData(lot.getModelId());
            meta.setDisplayName("§6" + lot.getVehicleName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Продавец: §f" + Bukkit.getOfflinePlayer(lot.getSeller()).getName());
            lore.add("§7Цена: §a" + lot.getPrice() + " монет");
            lore.add("§7Здоровье: §f" + lot.getHealth() + "%");
            lore.add("§7Топливо: §f" + lot.getFuel() + "%");
            lore.add("§eНажмите для покупки");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
            slot++;
        }
        inv.setItem(53, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    // Выставление машины на аукцион (из стоянки)
    public void listVehicleForAuction(Player player, UUID vehicleId, double price) {
        Vehicle vehicle = plugin.getModuleManager().getVehicleManager().getVehicleById(vehicleId);
        if (vehicle == null) {
            player.sendMessage("§cМашина не найдена.");
            return;
        }
        if (!vehicle.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cВы не владелец этой машины.");
            return;
        }
        VehicleAuctionLot lot = new VehicleAuctionLot(
                vehicleId,
                player.getUniqueId(),
                vehicle.getType().getDisplayName(),
                vehicle.getType().getModelId(),
                price,
                vehicle.getHealth(),
                vehicle.getFuel()
        );
        vehicleAuctions.put(vehicleId, lot);
        // Удаляем машину из мира (если она заспавнена)
        Entity entity = Bukkit.getEntity(vehicleId);
        if (entity != null) entity.remove();
        // Удаляем из VehicleManager
        plugin.getModuleManager().getVehicleManager().unregisterVehicle(vehicleId);
        saveVehicleAuctions();
        player.sendMessage("§aМашина выставлена на аукцион за " + price + " монет.");
        Bukkit.broadcastMessage("§6[Авторынок] §e" + player.getName() + " §fвыставил машину §e" + lot.getVehicleName() + " §fза §a" + price + " §fмонет.");
    }

    // Покупка машины на аукционе
    @EventHandler
    public void onVehicleAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Авторынок (игроки)")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 53) { player.closeInventory(); return; }
        if (slot < 0 || slot >= vehicleAuctions.size()) return;

        UUID vehicleId = (UUID) vehicleAuctions.keySet().toArray()[slot];
        VehicleAuctionLot lot = vehicleAuctions.get(vehicleId);
        if (lot == null) return;

        if (lot.getSeller().equals(player.getUniqueId())) {
            player.sendMessage("§cВы не можете купить свою машину.");
            return;
        }

        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < lot.getPrice()) {
            player.sendMessage("§cНедостаточно денег! Нужно: " + lot.getPrice());
            return;
        }

        // Создаём машину для покупателя
        VehicleType type;
        try {
            type = VehicleType.valueOf(lot.getVehicleName().toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            type = VehicleType.CAR;
        }
        Vehicle newVehicle = plugin.getModuleManager().getVehicleManager().spawnVehicle(player, type, player.getLocation());
        if (newVehicle == null) {
            player.sendMessage("§cНе удалось создать машину.");
            return;
        }
        // Передаём деньги продавцу
        plugin.getEconomyManager().withdraw(player, lot.getPrice());
        plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lot.getSeller()), lot.getPrice());

        vehicleAuctions.remove(vehicleId);
        saveVehicleAuctions();
        player.sendMessage("§aВы купили машину " + lot.getVehicleName() + " за " + lot.getPrice() + " монет!");
        player.closeInventory();
    }

    // ============================================================
    // СОХРАНЕНИЕ/ЗАГРУЗКА
    // ============================================================

    public void saveAuctions() {
        plugin.getDataManager().save("auctions", activeAuctions);
    }

    private void loadAuctions() {
        Map<String, AuctionLot> loaded = plugin.getDataManager().load("auctions", Map.class);
        if (loaded != null) {
            activeAuctions.putAll(loaded);
        }
        plugin.getLogger().info("Загружено " + activeAuctions.size() + " лотов.");
    }

    private void saveVehicleAuctions() {
        plugin.getDataManager().save("vehicle_auctions", vehicleAuctions);
    }

    private void loadVehicleAuctions() {
        Map<UUID, VehicleAuctionLot> loaded = plugin.getDataManager().load("vehicle_auctions", Map.class);
        if (loaded != null) {
            vehicleAuctions.putAll(loaded);
        }
        plugin.getLogger().info("Загружено " + vehicleAuctions.size() + " лотов машин.");
    }

    // ============================================================
    // ВНУТРЕННИЕ КЛАССЫ
    // ============================================================

    public static class AuctionLot {
        private final String auctionId;
        private final UUID seller;
        private final ItemStack item;
        private final double price;
        private final long timestamp;

        public AuctionLot(String auctionId, UUID seller, ItemStack item, double price, long timestamp) {
            this.auctionId = auctionId;
            this.seller = seller;
            this.item = item;
            this.price = price;
            this.timestamp = timestamp;
        }

        public String getAuctionId() { return auctionId; }
        public UUID getSeller() { return seller; }
        public ItemStack getItem() { return item; }
        public double getPrice() { return price; }
        public long getTimestamp() { return timestamp; }
    }

    public static class VehicleAuctionLot {
        private final UUID vehicleId;
        private final UUID seller;
        private final String vehicleName;
        private final int modelId;
        private final double price;
        private final int health;
        private final int fuel;

        public VehicleAuctionLot(UUID vehicleId, UUID seller, String vehicleName, int modelId, double price, int health, int fuel) {
            this.vehicleId = vehicleId;
            this.seller = seller;
            this.vehicleName = vehicleName;
            this.modelId = modelId;
            this.price = price;
            this.health = health;
            this.fuel = fuel;
        }

        public UUID getVehicleId() { return vehicleId; }
        public UUID getSeller() { return seller; }
        public String getVehicleName() { return vehicleName; }
        public int getModelId() { return modelId; }
        public double getPrice() { return price; }
        public int getHealth() { return health; }
        public int getFuel() { return fuel; }
    }

    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
                           }
