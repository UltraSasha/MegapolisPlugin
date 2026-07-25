package com.megapolis.core.modules.skin;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit; import org.bukkit.Material; import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent; import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory; import org.bukkit.inventory.ItemStack; import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta; import org.bukkit.persistence.PersistentDataType; import org.bukkit.NamespacedKey;

import java.util.Arrays; import java.util.HashMap; import java.util.Map; import java.util.UUID;

public class SkinManager implements Listener {
    private final MegapolisPlugin plugin; private final NamespacedKey skinKey; private final Map<UUID, String> activeSkins = new HashMap<>();

    public SkinManager(MegapolisPlugin plugin) {
        this.plugin = plugin; this.skinKey = new NamespacedKey(plugin, "skin_name");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (Bukkit.getPluginManager().getPlugin("SkinRestorer") == null) plugin.getLogger().warning("SkinRestorer не найден!");
    }

    public boolean applySkin(Player player, String skinName) {
        if (Bukkit.getPluginManager().getPlugin("SkinRestorer") == null) { player.sendMessage("§cSkinRestorer не установлен."); return false; }
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin set " + player.getName() + " " + skinName);
        if (success) { activeSkins.put(player.getUniqueId(), skinName); player.sendMessage("§aСкин '" + skinName + "' применён!"); }
        else player.sendMessage("§cНе удалось применить скин.");
        return success;
    }

    public boolean applySkinFromItem(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String skinName = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        if (skinName == null || skinName.isEmpty()) return false;
        item.setAmount(item.getAmount() - 1);
        return applySkin(player, skinName);
    }

    public ItemStack createSkinItem(String skinName, String displayName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName("§6" + displayName);
        meta.setLore(Arrays.asList("§7Нажмите ПКМ, чтобы применить скин", "§7Скин: §f" + skinName));
        meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skinName);
        item.setItemMeta(meta);
        return item;
    }

    public void createSkinItemFromCurrent(Player player, String skinName) {
        ItemStack skinItem = createSkinItem(skinName, "§6Скин: " + skinName);
        player.getInventory().addItem(skinItem);
        player.sendMessage("§aПредмет-скин '" + skinName + "' создан!");
    }

    public void openSkinGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§dСкины");
        inv.setItem(0, createButton(Material.GOLD_INGOT, "§aПрименить из предмета", "Держите голову в руке"));
        inv.setItem(1, createButton(Material.BARRIER, "§cСбросить скин", "Вернуть стандартный"));
        inv.setItem(2, createButton(Material.BOOK, "§eТекущий скин", activeSkins.getOrDefault(player.getUniqueId(), "Не установлен")));
        inv.setItem(26, createButton(Material.BARRIER, "§cЗакрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onSkinGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§dСкины")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 0) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (applySkinFromItem(player, item)) player.sendMessage("§aСкин применён!");
            else player.sendMessage("§cДержите в руке валидный предмет-скин.");
            player.closeInventory();
        } else if (slot == 1) {
            if (Bukkit.getPluginManager().getPlugin("SkinRestorer") != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin remove " + player.getName());
                activeSkins.remove(player.getUniqueId());
                player.sendMessage("§aСкин сброшен.");
            }
            player.closeInventory();
        } else if (slot == 26) player.closeInventory();
    }

    @EventHandler
    public void onSkinItemUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return;
        String skinName = item.getItemMeta().getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        if (skinName != null && !skinName.isEmpty()) {
            applySkinFromItem(player, item);
            event.setCancelled(true);
        }
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
