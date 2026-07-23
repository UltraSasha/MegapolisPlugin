package com.megapolis.core.economy;

import com.megapolis.core.MegapolisPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class EconomyManager {

    private final MegapolisPlugin plugin;
    private Economy vaultEconomy;

    // Имя бота-сервера
    public static final String SERVER_BOT_NAME = "ServerBot";

    public EconomyManager(MegapolisPlugin plugin) {
        this.plugin = plugin;
        setupVault();
    }

    private void setupVault() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("Vault экономика подключена.");
        } else {
            plugin.getLogger().severe("Vault не найден! Экономика недоступна.");
        }
    }

    // Проверка, что ServerBot существует (создаём оффлайн-аккаунт)
    public void ensureServerBot() {
        OfflinePlayer bot = Bukkit.getOfflinePlayer(SERVER_BOT_NAME);
        if (!bot.hasPlayedBefore() && !bot.isOnline()) {
            // Создаём аккаунт через вызов каких-то методов (можно просто пополнить баланс)
            vaultEconomy.depositPlayer(bot, 0);
        }
    }

    public double getBalance(OfflinePlayer player) {
        if (vaultEconomy == null) return 0;
        return vaultEconomy.getBalance(player);
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return;
        vaultEconomy.depositPlayer(player, amount);
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return;
        vaultEconomy.withdrawPlayer(player, amount);
    }

    // Отправка денег боту
    public void payToServerBot(OfflinePlayer from, double amount) {
        OfflinePlayer bot = Bukkit.getOfflinePlayer(SERVER_BOT_NAME);
        withdraw(from, amount);
        deposit(bot, amount);
    }

    public Economy getVaultEconomy() { return vaultEconomy; }
}
