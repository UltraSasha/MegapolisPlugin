package com.megapolis.core.modules.skin;

import com.megapolis.core.MegapolisPlugin;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.exception.SkinRequestException;
import net.skinsrestorer.api.property.SkinProperty;
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
    private SkinsRestorer skinsRestorer;
    private final NamespacedKey skinKey;
    private final NamespacedKey skinSourceKey; // откуда взят скин (имя или UUID)

    // Кэш: UUID игрока → имя скина, который он сейчас использует
    private final Map<UUID, String> activeSkins = new HashMap<>();

    public SkinManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.skinKey = new NamespacedKey(plugin, "skin_name");
        this.skinSourceKey = new NamespacedKey(plugin, "skin_source");

        // Инициализация API SkinRestorer
        try {
            this.skinsRestorer = SkinsRestorerProvider.get();
            plugin.getLogger().info("SkinRestorer API успешно подключен.");
        } catch (Exception e) {
            plugin.getLogger().warning("SkinRestorer не найден! Скины не будут работать.");
            this.skinsRestorer = null;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Проверяет, доступен ли SkinRestorer.
     */
    public boolean isSkinRestorerAvailable() {
        return skinsRestorer != null;
    }

    /**
     * Применяет скин к игроку по имени скина.
     */
    public boolean applySkin(Player player, String skinName) {
        if (!isSkinRestorerAvailable()) {
            player.sendMessage("§cSkinRestorer не установлен!");
            return false;
        }

        try {
            SkinProperty skinData = skinsRestorer.getSkinStorage().getSkinData(skinName);
            if (skinData == null) {
                skinData = skinsRestorer.getMojangAPI().getSkinData(skinName);
                if (skinData == null) {
                    player.sendMessage("§cСкин с именем '" + skinName + "' не найден!");
                    return false;
                }
            }

            skinsRestorer.getSkinApplier(Player.class).applySkin(player, skinData);
            activeSkins.put(player.getUniqueId(), skinName);
            player.sendMessage("§aСкин '" + skinName + "' успешно применён!");
            return true;

        } catch (SkinRequestException e) {
            player.sendMessage("§cОшибка при применении скина: " + e.getMessage());
            return false;
        } catch (Exception e) {
            player.sendMessage("§cНеизвестная ошибка: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Применяет скин из предмета (игрок кликает по предмету-скину).
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

        // Предмет одноразовый (убираем один)
        item.setAmount(item.getAmount() - 1);

        return applySkin(player, skinName);
    }

    /**
     * Создаёт предмет-скин, который запоминает ТЕКУЩИЙ скин игрока.
     * Используется в команде /newskin.
     */
    public ItemStack createSkinItemFromCurrent(Player player, String skinName) {
        // Получаем текущий скин игрока
        String currentSkin = activeSkins.getOrDefault(player.getUniqueId(), null);
        if (currentSkin == null) {
            // Если у игрока нет активного скина (стандартный), пытаемся получить через API
            try {
                if (isSkinRestorerAvailable()) {
                    SkinProperty skinData = skinsRestorer.getSkinStorage().getSkinData(player.getName());
                    if (skinData != null) {
                        // Пытаемся выяснить имя, но API не даёт прямой метод, поэтому используем имя игрока
                        // В реальности нужно сохранять имя при применении, но для простоты будем использовать ник игрока
                        currentSkin = player.getName();
                    }
                }
            } catch (Exception e) {
                // Игнорируем
            }
        }

        if (currentSkin == null) {
            // Если всё равно null, используем ник игрока (как имя скина)
            currentSkin = player.getName();
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName("§6Скин: " + skinName);
        meta.setLore(Arrays.asList(
                "§7Запомненный скин: §f" + currentSkin,
                "§7Нажмите ПКМ, чтобы применить",
                "§7(Одноразовый)"
        ));
        // Сохраняем имя скина в PDC
        meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, currentSkin);
        // Сохраняем название, данное игроком (для информации)
        meta.getPersistentDataContainer().set(skinSourceKey, PersistentDataType.STRING, skinName);
        // Устанавливаем скин на голову (для отображения)
        // Можно попробовать установить владельца, если это валидный ник
        // meta.setOwningPlayer(Bukkit.getOfflinePlayer(currentSkin));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Открывает GUI со скинами (для планшета).
     */
    public void openSkinGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§dСкины");

        ItemStack applyBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta applyMeta = applyBtn.getItemMeta();
        applyMeta.setDisplayName("§aПрименить скин из предмета");
        applyMeta.setLore(Arrays.asList("§7Держите в руке предмет-скин", "§7и нажмите сюда"));
        applyBtn.setItemMeta(applyMeta);
        inv.setItem(0, applyBtn);

        ItemStack resetBtn = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = resetBtn.getItemMeta();
        resetMeta.setDisplayName("§cСбросить скин");
        resetMeta.setLore(Arrays.asList("§7Вернуть стандартный скин"));
        resetBtn.setItemMeta(resetMeta);
        inv.setItem(1, resetBtn);

        String currentSkin = activeSkins.getOrDefault(player.getUniqueId(), "Не установлен");
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoBtn.getItemMeta();
        infoMeta.setDisplayName("§eТекущий скин");
        infoMeta.setLore(Arrays.asList("§7" + currentSkin));
        infoBtn.setItemMeta(infoMeta);
        inv.setItem(2, infoBtn);

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
                ItemStack item = player.getInventory().getItemInMainHand();
                if (applySkinFromItem(player, item)) {
                    player.sendMessage("§aСкин успешно применён!");
                } else {
                    player.sendMessage("§cДержите в руке валидный предмет-скин (PLAYER_HEAD).");
                }
                player.closeInventory();
            }
            case 1 -> {
                if (isSkinRestorerAvailable()) {
                    try {
                        skinsRestorer.getSkinApplier(Player.class).removeSkin(player);
                        activeSkins.remove(player.getUniqueId());
                        player.sendMessage("§aСкин сброшен до стандартного.");
                    } catch (Exception e) {
                        player.sendMessage("§cОшибка при сбросе скина.");
                    }
                } else {
                    player.sendMessage("§cSkinRestorer не доступен.");
                }
                player.closeInventory();
            }
            case 26 -> player.closeInventory();
        }
    }

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
                if (applySkinFromItem(player, item)) {
                    player.sendMessage("§aСкин '" + skinName + "' применён!");
                }
                event.setCancelled(true);
            }
        }
    }

    public String getActiveSkin(Player player) {
        return activeSkins.getOrDefault(player.getUniqueId(), "Не установлен");
    }
}