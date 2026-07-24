package com.megapolis.core.modules.cases;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.data.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CaseManager implements Listener {

    private final MegapolisPlugin plugin;
    private final DataManager dataManager;
    private final Map<String, Case> cases = new HashMap<>();

    public CaseManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        loadCases();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadCases() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection caseSection = config.getConfigurationSection("cases");
        if (caseSection == null) return;

        for (String caseName : caseSection.getKeys(false)) {
            ConfigurationSection cs = caseSection.getConfigurationSection(caseName);
            String displayName = cs.getString("display_name", caseName);
            double price = cs.getDouble("price", 0);
            List<Map<?, ?>> rewardsList = cs.getMapList("rewards");

            List<CaseReward> rewards = new ArrayList<>();
            for (Map<?, ?> rewardMap : rewardsList) {
                String type = (String) rewardMap.get("type");
                int amount = (int) rewardMap.get("amount");
                String rarity = (String) rewardMap.get("rarity");
                double chance = (double) rewardMap.get("chance");
                String materialName = (String) rewardMap.get("material");
                int customModelData = rewardMap.containsKey("custom_model_data") ? (int) rewardMap.get("custom_model_data") : 0;
                rewards.add(new CaseReward(type, amount, rarity, chance, materialName, customModelData));
            }

            Case caseObj = new Case(caseName, displayName, price, rewards);
            cases.put(caseName, caseObj);
        }
        plugin.getLogger().info("Загружено " + cases.size() + " кейсов.");
    }

    public void openCase(Player player, String caseName) {
        Case caseObj = cases.get(caseName);
        if (caseObj == null) {
            player.sendMessage("§cКейс не найден.");
            return;
        }

        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < caseObj.getPrice()) {
            player.sendMessage("§cНедостаточно денег. Нужно: " + caseObj.getPrice());
            return;
        }
        plugin.getEconomyManager().withdraw(player, caseObj.getPrice());

        CaseReward reward = selectReward(caseObj.getRewards());
        if (reward == null) {
            player.sendMessage("§cНе удалось определить награду.");
            return;
        }

        giveReward(player, reward);
        plugin.getLogger().info(player.getName() + " открыл кейс " + caseName + " и получил " + reward.getType() + " x" + reward.getAmount());
    }

    private CaseReward selectReward(List<CaseReward> rewards) {
        double total = rewards.stream().mapToDouble(CaseReward::getChance).sum();
        double rand = Math.random() * total;
        double cumulative = 0;
        for (CaseReward r : rewards) {
            cumulative += r.getChance();
            if (rand <= cumulative) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    private void giveReward(Player player, CaseReward reward) {
        String type = reward.getType();
        int amount = reward.getAmount();

        switch (type.toLowerCase()) {
            case "money":
                plugin.getEconomyManager().deposit(player, amount);
                player.sendMessage("§aВы получили " + amount + " монет!");
                break;
            case "experience":
                player.giveExp(amount);
                player.sendMessage("§aВы получили " + amount + " опыта!");
                break;
            case "item":
                if (reward.getMaterial() == null) {
                    player.sendMessage("§cОшибка: не указан материал предмета.");
                    return;
                }
                Material mat = Material.getMaterial(reward.getMaterial());
                if (mat == null) {
                    player.sendMessage("§cОшибка: неверный материал.");
                    return;
                }
                ItemStack item = new ItemStack(mat, amount);
                ItemMeta meta = item.getItemMeta();
                if (reward.getCustomModelData() > 0) {
                    meta.setCustomModelData(reward.getCustomModelData());
                }
                List<String> lore = new ArrayList<>();
                lore.add("§7Редкость: " + reward.getRarity());
                meta.setLore(lore);
                item.setItemMeta(meta);
                player.getInventory().addItem(item);
                player.sendMessage("§aВы получили " + amount + "x " + reward.getMaterial() + "!");
                break;
            case "transformer":
                player.sendMessage("§aВы получили трансформер-скин (заглушка)!");
                break;
            default:
                player.sendMessage("§cНеизвестный тип награды: " + type);
        }
    }

    public void openCasesGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§5Кейсы");
        int slot = 0;
        for (Case c : cases.values()) {
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6" + c.getDisplayName());
            meta.setLore(Arrays.asList("§7Цена: §a" + c.getPrice() + " монет", "§7Нажмите, чтобы открыть"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().equals("§5Кейсы")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= cases.size()) return;
            String caseName = (String) cases.keySet().toArray()[slot];
            openCase(player, caseName);
            player.closeInventory();
        }
    }

    public static class Case {
        private final String name;
        private final String displayName;
        private final double price;
        private final List<CaseReward> rewards;

        public Case(String name, String displayName, double price, List<CaseReward> rewards) {
            this.name = name;
            this.displayName = displayName;
            this.price = price;
            this.rewards = rewards;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public double getPrice() { return price; }
        public List<CaseReward> getRewards() { return rewards; }
    }

    public static class CaseReward {
        private final String type;
        private final int amount;
        private final String rarity;
        private final double chance;
        private final String material;
        private final int customModelData;

        public CaseReward(String type, int amount, String rarity, double chance, String material, int customModelData) {
            this.type = type;
            this.amount = amount;
            this.rarity = rarity;
            this.chance = chance;
            this.material = material;
            this.customModelData = customModelData;
        }

        public String getType() { return type; }
        public int getAmount() { return amount; }
        public String getRarity() { return rarity; }
        public double getChance() { return chance; }
        public String getMaterial() { return material; }
        public int getCustomModelData() { return customModelData; }
    }
}