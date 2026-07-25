package com.megapolis.core.modules.skin;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkinManager implements Listener {

    private final MegapolisPlugin plugin;
    private final NamespacedKey skinKey;

    // Кэш: UUID игрока → имя скина, которое он использует
    private final Map<UUID, String> activeSkins = new HashMap<>();

    public SkinManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.skinKey = new NamespacedKey(plugin, "skin_name");
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Проверяем, установлен ли SkinRestorer
        if (Bukkit.getPluginManager().getPlugin("SkinRestorer") == null) {
            plugin.getLogger().warning("⚠️ SkinRestorer не найден! Скины не будут работать.");
        } else {
            plugin.getLogger().info("✅ SkinRestorer найден, скины будут работать через команды.");
        }
    }

    /**
     * Применяет скин к игроку через консольную команду.
     */
    public boolean applySkin(Player player, String skinName) {
        if (Bukkit.getPluginManager().getPlugin("SkinRestorer") == null) {
            player.sendMessage("§cSkinRestorer не установлен на сервере!");
            return false;
        }

        // Выполняем команду от консоли
        boolean success = Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "skin set " + player.getName() + " " + skinName
        );

        if (success) {
            activeSkins.put(player.getUniqueId(), skinName);
            player.sendMessage("§aСкин '" + skinName + "' успешно применён!");
        } else {
            player.sendMessage("§cНе удалось применить скин. Проверьте имя скина.");
        }

        return success;
    }

    /**
     * Применяет скин из предмета (ПКМ по голове).
     */
    public boolean applySkinFromItem(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String skinName = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        if (skinName == null || skinName.isEmpty()) {
            return false;
        }

        // Убираем 1 предмет из стека (одноразовый скин)
        item.setAmount(item.getAmount() - 1);

        return applySkin(player, skinName);
    }

    /**
     * Создаёт предмет-скин (голову) для выдачи игроку.
     */
    public ItemStack createSkinItem(String skinName, String displayName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName("§6" + displayName);
        meta.setLore(Arrays.asList(
                "§7Нажмите ПКМ, чтобы применить скин",
                "§7Скин: §f" + skinName
        ));
        // Сохраняем имя скина в PersistentDataContainer
        meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skinName);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Открывает GUI управления скинами (из планшета).
     */
    public void openSkinGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§dСкины");

        // Кнопка: применить скин из предмета в руке
        ItemStack applyBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta applyMeta = applyBtn.getItemMeta();
        applyMeta.setDisplayName("§aПрименить скин из предмета");
        applyMeta.setLore(Arrays.asList(
                "§7Держите в руке предмет-скин (голову)",
                "§7и нажмите сюда"
        ));
        applyBtn.setItemMeta(applyMeta);
        inv.setItem(0, applyBtn);

        // Кнопка: сбросить скин (на стандартный)
        ItemStack resetBtn = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = resetBtn.getItemMeta();
        resetMeta.setDisplayName("§cСбросить скин");
        resetMeta.setLore(Arrays.asList("§7Вернуть стандартный скин"));
        resetBtn.setItemMeta(resetMeta);
        inv.setItem(1, resetBtn);

        // Информация о текущем скине
        String currentSkin = activeSkins.getOrDefault(player.getUniqueId(), "Не установлен");
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoBtn.getItemMeta();
        infoMeta.setDisplayName("§eТекущий скин");
        infoMeta.setLore(Arrays.asList("§7" + currentSkin));
        infoBtn.setItemMeta(infoMeta);
        inv.setItem(2, infoBtn);

        // Команда /skin (пояснение)
        ItemStack cmdBtn = new ItemStack(Material.PAPER);
        ItemMeta cmdMeta = cmdBtn.getItemMeta();
        cmdMeta.setDisplayName("§6Команда /skin");
        cmdMeta.setLore(Arrays.asList(
                "§7Используйте: §f/skin <имя_скина>",
                "§7Пример: §f/skin Bumblebee"
        ));
        cmdBtn.setItemMeta(cmdMeta);
        inv.setItem(3, cmdBtn);

        // Закрыть
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        closeBtn.setItemMeta(closeMeta);
        inv.setItem(26, closeBtn);

        player.openInventory(inv);
    }

    @EventHandler
    public void onSkinGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§dСкины")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        switch (slot) {
            case 0 -> {
                // Применить скин из предмета в руке
                ItemStack item = player.getInventory().getItemInMainHand();
                if (applySkinFromItem(player, item)) {
                    player.sendMessage("§aСкин применён!");
                } else {
                    player.sendMessage("§cДержите в руке валидный предмет-скин (PLAYER_HEAD).");
                }
                player.closeInventory();
            }
            case 1 -> {
                // Сброс скина
                if (Bukkit.getPluginManager().getPlugin("SkinRestorer") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin remove " + player.getName());
                    activeSkins.remove(player.getUniqueId());
                    player.sendMessage("§aСкин сброшен до стандартного.");
                } else {
                    player.sendMessage("§cSkinRestorer не установлен!");
                }
                player.closeInventory();
            }
            case 26 -> player.closeInventory();
        }
    }

    /**
     * Обработчик ПКМ по предмету-скину (вне GUI).
     */
    @EventHandler
    public void onSkinItemUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.PLAYER_HEAD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String skinName = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
            if (skinName != null && !skinName.isEmpty()) {
                applySkinFromItem(player, item);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Получить имя текущего скина игрока.
     */
    public String getActiveSkin(Player player) {
        return activeSkins.getOrDefault(player.getUniqueId(), "Не установлен");
    }

    /**
     * Команда /newskin <название> — сохраняет текущий скин игрока как предмет.
     */
    public void saveCurrentSkinAsItem(Player player, String skinName) {
        // Получаем текущий скин игрока через команду (если есть)
        // Упрощённо: просто создаём предмет с указанным именем
        ItemStack skinItem = createSkinItem(skinName, "§6Скин: " + skinName);
        player.getInventory().addItem(skinItem);
        player.sendMessage("§aПредмет-скин '" + skinName + "' создан!");
    }
}
