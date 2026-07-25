package com.megapolis.core.modules.bank;

import com.megapolis.core.MegapolisPlugin;
import org.bukkit.Bukkit; import org.bukkit.Material; import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory; import org.bukkit.inventory.ItemStack; import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class BankManager implements Listener {
    private final MegapolisPlugin plugin;
    private final Map<UUID, Map<String, Double>> playerBalances = new HashMap<>();
    private final Map<String, Double> exchangeRates = new HashMap<>();
    private final Map<UUID, List<BankTransaction>> transactions = new HashMap<>();
    private final Map<UUID, List<Loan>> activeLoans = new HashMap<>();

    public BankManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadExchangeRates(); loadBalances(); startExchangeUpdater();
    }

    private void loadExchangeRates() {
        exchangeRates.put("USD", 1.0); exchangeRates.put("EUR", 1.2); exchangeRates.put("RUB", 0.01); exchangeRates.put("BTC", 10000.0);
    }

    private void startExchangeUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String key : exchangeRates.keySet()) {
                double change = (Math.random() - 0.5) * 0.02;
                double newRate = exchangeRates.get(key) * (1 + change);
                if (newRate < 0.001) newRate = 0.001;
                exchangeRates.put(key, newRate);
            }
        }, 0, 20 * 60 * 60);
    }

    private void loadBalances() { /* заглушка */ }
    public void saveBalances() { plugin.getDataManager().save("balances", playerBalances); }

    public void openBankGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§aБанк");
        UUID uuid = player.getUniqueId();
        Map<String, Double> balances = playerBalances.getOrDefault(uuid, new HashMap<>());
        inv.setItem(0, createCurrencyItem(Material.GOLD_INGOT, "Рубли", balances.getOrDefault("RUB", 0.0)));
        inv.setItem(1, createCurrencyItem(Material.DIAMOND, "Доллары", balances.getOrDefault("USD", 0.0)));
        inv.setItem(2, createCurrencyItem(Material.EMERALD, "Евро", balances.getOrDefault("EUR", 0.0)));
        inv.setItem(3, createCurrencyItem(Material.NETHERITE_INGOT, "Биткоины", balances.getOrDefault("BTC", 0.0)));
        inv.setItem(9, createButton(Material.WRITABLE_BOOK, "Кредиты", "Взять/погасить кредит"));
        inv.setItem(10, createButton(Material.CHEST, "Вклады", "Открыть вклад"));
        inv.setItem(11, createButton(Material.ARROW, "Обмен валют", "Конвертировать"));
        inv.setItem(12, createButton(Material.BOOK, "История", "Посмотреть транзакции"));
        inv.setItem(13, createButton(Material.BARRIER, "Закрыть", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onBankClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§aБанк")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        switch (slot) {
            case 9 -> openCreditGUI(player);
            case 10 -> openDepositGUI(player);
            case 11 -> openExchangeGUI(player);
            case 12 -> showHistory(player);
            case 13 -> player.closeInventory();
        }
    }

    private void openCreditGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Кредиты");
        inv.setItem(0, createButton(Material.GOLD_INGOT, "5000 RUB", "Процент 5%, срок 30 дней"));
        inv.setItem(1, createButton(Material.GOLD_INGOT, "10000 RUB", "Процент 4%, срок 60 дней"));
        inv.setItem(2, createButton(Material.GOLD_INGOT, "25000 RUB", "Процент 3%, срок 90 дней"));
        inv.setItem(3, createButton(Material.EMERALD, "Погасить кредит", "Погасить досрочно"));
        inv.setItem(26, createButton(Material.BARRIER, "Назад", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onCreditClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Кредиты")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) { openBankGUI(player); return; }
        if (slot == 3) { repayLoan(player); return; }
        double[] amounts = {5000, 10000, 25000};
        double[] rates = {0.05, 0.04, 0.03};
        if (slot >= 0 && slot < 3) {
            if (hasActiveLoan(player)) { player.sendMessage("§cУ вас уже есть активный кредит."); return; }
            double amount = amounts[slot]; double rate = rates[slot];
            deposit(player, "RUB", amount);
            addTransaction(player, "Кредит", "RUB", amount, "Выдан кредит на сумму " + amount);
            activeLoans.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(new Loan(amount, rate, System.currentTimeMillis()));
            player.sendMessage("§aВам выдан кредит на " + amount + " RUB. Процентная ставка: " + (rate*100) + "%");
            player.closeInventory();
        }
    }

    private boolean hasActiveLoan(Player player) {
        List<Loan> loans = activeLoans.get(player.getUniqueId());
        if (loans == null) return false;
        for (Loan loan : loans) if (!loan.isRepaid()) return true;
        return false;
    }

    private void repayLoan(Player player) {
        List<Loan> loans = activeLoans.get(player.getUniqueId());
        if (loans == null || loans.isEmpty()) { player.sendMessage("§eУ вас нет активных кредитов."); return; }
        Loan loan = null;
        for (Loan l : loans) if (!l.isRepaid()) { loan = l; break; }
        if (loan == null) { player.sendMessage("§eУ вас нет активных кредитов."); return; }
        double total = loan.getAmount() * (1 + loan.getRate());
        if (getBalance(player, "RUB") < total) { player.sendMessage("§cНедостаточно средств. Нужно: " + total + " RUB."); return; }
        withdraw(player, "RUB", total);
        loan.setRepaid(true);
        addTransaction(player, "Погашение кредита", "RUB", -total, "Кредит погашен");
        player.sendMessage("§aКредит погашен! Сумма: " + total + " RUB.");
        player.closeInventory();
    }

    private void openDepositGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Вклады");
        inv.setItem(0, createButton(Material.CHEST, "10000 RUB", "Доход 2% в месяц, срок 30 дней"));
        inv.setItem(1, createButton(Material.CHEST, "50000 RUB", "Доход 3% в месяц, срок 60 дней"));
        inv.setItem(2, createButton(Material.CHEST, "100000 RUB", "Доход 4% в месяц, срок 90 дней"));
        inv.setItem(26, createButton(Material.BARRIER, "Назад", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onDepositClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Вклады")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 26) { openBankGUI(player); return; }
        double[] amounts = {10000, 50000, 100000};
        double[] rates = {0.02, 0.03, 0.04};
        if (slot >= 0 && slot < 3) {
            double amount = amounts[slot]; double rate = rates[slot];
            if (getBalance(player, "RUB") < amount) { player.sendMessage("§cНедостаточно средств."); return; }
            withdraw(player, "RUB", amount);
            addTransaction(player, "Вклад", "RUB", amount, "Открыт вклад на сумму " + amount + " под " + (rate*100) + "%");
            player.sendMessage("§aВклад открыт! Сумма: " + amount + " RUB, доходность: " + (rate*100) + "% в месяц.");
            player.closeInventory();
        }
    }

    private void openExchangeGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§6Обмен валют");
        String[] currencies = {"RUB", "USD", "EUR", "BTC"};
        int slot = 0;
        for (String from : currencies) for (String to : currencies) {
            if (from.equals(to)) continue;
            double rate = getExchangeRate(from, to);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + from + " → " + to);
            meta.setLore(Arrays.asList("§7Курс: 1 " + from + " = " + String.format("%.4f", rate) + " " + to, "§7Кликните для обмена 1000 " + from));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        inv.setItem(35, createButton(Material.BARRIER, "Назад", ""));
        player.openInventory(inv);
    }

    @EventHandler
    public void onExchangeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6Обмен валют")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 35) { openBankGUI(player); return; }
        String[] currencies = {"RUB", "USD", "EUR", "BTC"};
        int pairIndex = slot; int fromIdx = pairIndex / 3; int toIdx = pairIndex % 3;
        if (fromIdx == toIdx || fromIdx >= currencies.length || toIdx >= currencies.length) return;
        String from = currencies[fromIdx]; String to = currencies[toIdx];
        double amount = 1000;
        if (getBalance(player, from) < amount) { player.sendMessage("§cНедостаточно " + from + " на счету."); return; }
        double rate = getExchangeRate(from, to);
        double received = amount * rate;
        withdraw(player, from, amount); deposit(player, to, received);
        addTransaction(player, "Обмен", from, -amount, "Обмен " + amount + " " + from + " → " + to + " (" + received + ")");
        addTransaction(player, "Обмен", to, received, "Получено " + received + " " + to);
        player.sendMessage("§aВы обменяли " + amount + " " + from + " на " + String.format("%.2f", received) + " " + to);
        player.closeInventory();
    }

    private void showHistory(Player player) {
        List<BankTransaction> history = transactions.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (history.isEmpty()) { player.sendMessage("§eИстория пуста."); return; }
        Inventory inv = Bukkit.createInventory(null, 54, "§6История");
        int slot = 0;
        for (BankTransaction t : history) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + t.getType() + " (" + t.getCurrency() + ")");
            meta.setLore(Arrays.asList("§7Сумма: " + t.getAmount(), "§7Описание: " + t.getDescription(), "§7Дата: " + t.getDate()));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
            if (slot >= 54) break;
        }
        player.openInventory(inv);
    }

    private void addTransaction(Player player, String type, String currency, double amount, String desc) {
        BankTransaction t = new BankTransaction(type, currency, amount, desc, new Date());
        transactions.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(t);
    }

    public double getBalance(Player player, String currency) {
        return playerBalances.getOrDefault(player.getUniqueId(), new HashMap<>()).getOrDefault(currency, 0.0);
    }

    public void deposit(Player player, String currency, double amount) {
        playerBalances.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).merge(currency, amount, Double::sum);
    }

    public boolean withdraw(Player player, String currency, double amount) {
        double balance = getBalance(player, currency);
        if (balance < amount) return false;
        playerBalances.get(player.getUniqueId()).put(currency, balance - amount);
        return true;
    }

    public double getExchangeRate(String from, String to) {
        return exchangeRates.getOrDefault(from, 0.0) * (1.0 / exchangeRates.getOrDefault(to, 1.0));
    }

    private ItemStack createCurrencyItem(Material mat, String name, double amount) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + name + ": §e" + String.format("%.2f", amount));
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

    private static class BankTransaction {
        private final String type; private final String currency; private final double amount; private final String description; private final Date date; private boolean closed;
        public BankTransaction(String type, String currency, double amount, String description, Date date) {
            this.type = type; this.currency = currency; this.amount = amount; this.description = description; this.date = date; this.closed = false;
        }
        public String getType() { return type; }
        public String getCurrency() { return currency; }
        public double getAmount() { return amount; }
        public String getDescription() { return description; }
        public Date getDate() { return date; }
        public boolean isClosed() { return closed; }
        public void setClosed(boolean closed) { this.closed = closed; }
    }

    private static class Loan {
        private final double amount; private final double rate; private final long startTime; private boolean repaid;
        public Loan(double amount, double rate, long startTime) { this.amount = amount; this.rate = rate; this.startTime = startTime; this.repaid = false; }
        public double getAmount() { return amount; }
        public double getRate() { return rate; }
        public long getStartTime() { return startTime; }
        public boolean isRepaid() { return repaid; }
        public void setRepaid(boolean repaid) { this.repaid = repaid; }
    }
}
