package com.megapolis.core.modules.govshop;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GovShopManager implements Listener {

    private final MegapolisPlugin plugin;
    private final Map<String, ShopItem> shopItems = new LinkedHashMap<>();

    public GovShopManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadItems();
    }

    private void loadItems() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gov_shop");
        if (section == null) {
            plugin.getLogger().warning("Секция gov_shop в config.yml не найдена!");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            String materialName = itemSection.getString("material");
            int price = itemSection.getInt("price");
            int amount = itemSection.getInt("amount", 1);
            Material mat = Material.getMaterial(materialName);
            if (mat == null) {
                plugin.getLogger().warning("Неизвестный материал: " + materialName);
                continue;
            }
            shopItems.put(key, new ShopItem(key, mat, price, amount));
        }
        plugin.getLogger().info("Загружено " + shopItems.size() + " товаров в государственный магазин.");
    }

    public void openGovShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Государственный магазин");
        int slot = 0;
        for (ShopItem item : shopItems.values()) {
            if (slot >= 53) break;
            ItemStack display = new ItemStack(item.getMaterial(), item.getAmount());
            ItemMeta meta = display.getItemMeta();
            meta.setDisplayName("§6" + item.getName());
            meta.setLore(Arrays.asList(
                    "§7Цена: §a" + item.getPrice() + " монет",
                    "§eЛКМ — купить 1 стек",
                    "§eShift+ЛКМ — купить 1 предмет"
            ));
            display.setItemMeta(meta);
            inv.setItem(slot, display);
            slot++;
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);
        player.openInventory(inv);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Государственный магазин")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= shopItems.size()) return;

        String key = (String) shopItems.keySet().toArray()[slot];
        ShopItem item = shopItems.get(key);
        if (item == null) return;

        double balance = plugin.getEconomyManager().getBalance(player);
        int buyAmount = event.isShiftClick() ? 1 : 64;
        int totalPrice = item.getPrice() * buyAmount;

        if (balance < totalPrice) {
            player.sendMessage("§cНедостаточно денег! Нужно: " + totalPrice + " монет.");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cВаш инвентарь полон!");
            return;
        }
        plugin.getEconomyManager().withdraw(player, totalPrice);
        player.getInventory().addItem(new ItemStack(item.getMaterial(), buyAmount));
        player.sendMessage("§aВы купили " +