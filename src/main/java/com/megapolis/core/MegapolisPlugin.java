package com.megapolis.core;

import com.megapolis.core.commands.*;
import com.megapolis.core.data.DataManager;
import com.megapolis.core.economy.EconomyManager;
import com.megapolis.core.modules.ModuleManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MegapolisPlugin extends JavaPlugin {

    private static MegapolisPlugin instance;
    private DataManager dataManager;
    private EconomyManager economyManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.economyManager = new EconomyManager(this);
        this.moduleManager = new ModuleManager(this);

        // Регистрация команд
        getCommand("pts").setExecutor(new PTSCommand());
        getCommand("trunk").setExecutor(new TrunkCommand());
        getCommand("engine").setExecutor(new EngineCommand());
        getCommand("tf").setExecutor(new TFCommand());
        getCommand("tablet").setExecutor(new TabletCommand());
        getCommand("skin").setExecutor(new SkinCommand());
        getCommand("newskin").setExecutor(new NewSkinCommand());
        getCommand("vehicle").setExecutor(new VehicleSpawnCommand());
        getCommand("megapolis").setExecutor(new MegapolisCommand());

        getLogger().info("MegapolisCore успешно загружен!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) dataManager.saveAll();
        if (moduleManager != null) moduleManager.disable();
        getLogger().info("MegapolisCore выгружен.");
    }

    public static MegapolisPlugin getInstance() { return instance; }
    public DataManager getDataManager() { return dataManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
}
