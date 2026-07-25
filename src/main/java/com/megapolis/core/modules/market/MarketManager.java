package com.megapolis.core.modules.market;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager implements Listener {

    private final MegapolisPlugin plugin;
    // Хранилище активных лотов: auctionId -> AuctionLot
    private final Map<String, AuctionLot> activeAuctions = new ConcurrentHashMap<>();
    // Временные данные для создания лота: игрок -> выбранный предмет
    private final Map<UUID, ItemStack> sellItemBuffer = new HashMap<>();

    public MarketManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadAuctions();
    }

    // === СОЗДАНИЕ ЛОТА (игрок выставляет предмет) ===
    public void startSellProcess(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cДержите в руке предмет, который хотите продать.");
            return;
        }
        // Сохраняем предмет во временный буфер
        sellItemBuffer.put(player.getUniqueId(), item.clone());
        // Открываем GUI для ввода цены (упрощённо через чат)
        player.closeInventory();
        player.sendMessage("§eВведите цену в чат (только число) или напишите 'отмена' для отмены.");
        // Ожидаем ввод цены в чате (обработчик ниже)
    }

    // Обработка ввода цены из чата
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
            // Оповещение всем игрокам
            Bukkit.broadcastMessage("§6[Аукцион] §e" + player.getName() + " §fвыставил §e" + item.getType().name() + " §fза §a" + price + " §fмонет.");
        } catch (NumberFormatException e) {
            player.sendMessage("§cВведите корректное число.");
        }
    }

    // === ОТКРЫТИЕ ГЛАВНОГО МЕНЮ АУКЦИОНА ===
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
            // Сохраняем ID лота в NBT (временно в lore, т.к. проще)
            // Используем отдельную карту для сопоставления слота и ID
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

    // === ОБРАБОТЧИК КЛИКОВ В АУКЦИОНЕ ===
    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§dАукцион")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        // Кнопка "Выставить предмет"
        if (slot == 0) {
            startSellProcess(player);
            player.closeInventory();
            return;
        }
        // Кнопка "Мои лоты"
        if (slot == 1) {
            openMyLots(player);
            return;
        }
        // Кнопка закрытия
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        // Клик по лоту (слоты 9-53)
        if (slot >= 9 && slot < 53) {
            // Определяем, какой лот соответствует слоту
            int index = slot - 9;
            if (index >= activeAuctions.size()) return;
            String auctionId = (String) activeAuctions.keySet().toArray()[index];
            AuctionLot lot = activeAuctions.get(auctionId);
            if (lot == null) return;

            // Проверка: нельзя купить свой собственный лот
            if (lot.getSeller().equals(player.getUniqueId())) {
                player.sendMessage("§cВы не можете купить свой собственный лот.");
                return;
            }

            double balance = plugin.getEconomyManager().getBalance(player);
            if (event.isShiftClick()) {
                // Shift+ЛКМ — торг (делаем ставку)
                // Упрощённо: просто покупаем по цене + 10%
                double bidPrice = lot.getPrice() * 1.1;
                if (balance < bidPrice) {
                    player.sendMessage("§cНедостаточно средств для ставки. Нужно: " + bidPrice);
                    return;
                }
                // Списываем деньги продавцу (упрощённо, без возврата)
                plugin.getEconomyManager().withdraw(player, bidPrice);
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lot.getSeller()), bidPrice);
                // Забираем предмет
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
                // Проверка свободного места
                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage("§cВаш инвентарь полон!");
                    return;
                }
                // Списываем деньги продавцу
                plugin.getEconomyManager().withdraw(player, lot.getPrice());
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lot.getSeller()), lot.getPrice());
                // Выдаём предмет
                player.getInventory().addItem(lot.getItem());
                activeAuctions.remove(auctionId);
                saveAuctions();
                player.sendMessage("§aВы купили предмет за " + lot.getPrice() + " монет!");
                player.closeInventory();
            }
        }
    }

    // === МОИ ЛОТЫ ===
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
                // Сохраняем ID лота в отдельную карту для этого GUI
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
        // Находим лот по порядку
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
            // Возвращаем предмет игроку
            player.getInventory().addItem(lot.getItem());
            saveAuctions();
            player.sendMessage("§aВы сняли лот с продажи.");
            player.closeInventory();
        }
    }

    // === СОХРАНЕНИЕ И ЗАГРУЗКА ===
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

    // === ВНУТРЕННИЙ КЛАСС ЛОТА ===
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
}
