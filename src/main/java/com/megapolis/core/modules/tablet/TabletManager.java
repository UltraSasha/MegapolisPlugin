package com.megapolis.core.modules.tablet;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class TabletManager implements Listener {

    private final MegapolisPlugin plugin;

    public TabletManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Открывает главное меню планшета для игрока.
     */
    public void openTablet(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Планшет");

        // Кнопки разделов
        inv.setItem(0, createButton(Material.COMPASS, "§aБанк", "Баланс, кредиты, вклады"));
        inv.setItem(1, createButton(Material.MINECART, "§bАвторынок", "Купить машину"));
        inv.setItem(2, createButton(Material.CHEST, "§6Гос. магазин", "Купить у государства"));
        inv.setItem(3, createButton(Material.BOOK, "§eЗадания", "Ежедневные задания"));
        inv.setItem(4, createButton(Material.ENDER_CHEST, "§5Кейсы", "Открыть кейс"));
        inv.setItem(5, createButton(Material.NAME_TAG, "§6Мессенджер", "Сообщения и чаты"));
        inv.setItem(6, createButton(Material.GOLD_INGOT, "§6Аукцион", "Купить/продать у игроков"));
        inv.setItem(7, createButton(Material.PLAYER_HEAD, "§dСкины", "Применить скин из предмета"));

        player.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Планшет")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        switch (slot) {
            case 0 -> plugin.getModuleManager().getBankManager().openBankGUI(player);
            case 1 -> plugin.getModuleManager().getVehicleManager().openAutoMarket(player);
            case 2 -> plugin.getModuleManager().getGovShopManager().openGovShop(player);
            case 3 -> plugin.getModuleManager().getTaskManager().openTasksGUI(player);
            case 4 -> plugin.getModuleManager().getCaseManager().openCasesGUI(player);
            case 5 -> plugin.getModuleManager().getMessengerManager().openMessengerGUI(player);
            case 6 -> plugin.getModuleManager().getMarketManager().openMarketGUI(player);
            case 7 -> plugin.getModuleManager().getSkinManager().openSkinGUI(player);
            default -> {}
        }
        player.closeInventory();
    }
}