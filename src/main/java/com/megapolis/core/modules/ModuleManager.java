package com.megapolis.core.modules;

import com.megapolis.core.MegapolisPlugin;
import com.megapolis.core.modules.admin.AdminPanel;
import com.megapolis.core.modules.bank.BankManager;
import com.megapolis.core.modules.business.BusinessManager;
import com.megapolis.core.modules.cases.CaseManager;
import com.megapolis.core.modules.events.EventManager;
import com.megapolis.core.modules.govshop.GovShopManager;
import com.megapolis.core.modules.locations.LocationManager;
import com.megapolis.core.modules.market.MarketManager;
import com.megapolis.core.modules.messenger.MessengerManager;
import com.megapolis.core.modules.skin.SkinManager;
import com.megapolis.core.modules.tablet.TabletManager;
import com.megapolis.core.modules.tasks.TaskManager;
import com.megapolis.core.modules.transformers.TransformerListener;
import com.megapolis.core.modules.transformers.TransformerManager;
import com.megapolis.core.modules.transport.VehicleListener;
import com.megapolis.core.modules.transport.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

public class ModuleManager {

    private final MegapolisPlugin plugin;

    private VehicleManager vehicleManager;
    private CaseManager caseManager;
    private TransformerManager transformerManager;
    private EventManager eventManager;
    private BankManager bankManager;
    private TaskManager taskManager;
    private BusinessManager businessManager;
    private TabletManager tabletManager;
    private GovShopManager govShopManager;
    private MarketManager marketManager;
    private MessengerManager messengerManager;
    private SkinManager skinManager;
    private LocationManager locationManager;
    private AdminPanel adminPanel;

    public ModuleManager(MegapolisPlugin plugin) {
        this.plugin = plugin;

        this.vehicleManager = new VehicleManager(plugin);
        this.caseManager = new CaseManager(plugin);
        this.transformerManager = new TransformerManager(plugin);
        this.eventManager = new EventManager(plugin);
        this.bankManager = new BankManager(plugin);
        this.taskManager = new TaskManager(plugin);
        this.businessManager = new BusinessManager(plugin);
        this.govShopManager = new GovShopManager(plugin);
        this.marketManager = new MarketManager(plugin);
        this.messengerManager = new MessengerManager(plugin);
        this.skinManager = new SkinManager(plugin);
        this.locationManager = new LocationManager(plugin);
        this.adminPanel = new AdminPanel(plugin);
        this.tabletManager = new TabletManager(plugin);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new VehicleListener(vehicleManager), plugin);
        pm.registerEvents(new TransformerListener(transformerManager), plugin);

        plugin.getLogger().info("Все модули успешно загружены.");
    }

    public VehicleManager getVehicleManager() { return vehicleManager; }
    public CaseManager getCaseManager() { return caseManager; }
    public TransformerManager getTransformerManager() { return transformerManager; }
    public EventManager getEventManager() { return eventManager; }
    public BankManager getBankManager() { return bankManager; }
    public TaskManager getTaskManager() { return taskManager; }
    public BusinessManager getBusinessManager() { return businessManager; }
    public TabletManager getTabletManager() { return tabletManager; }
    public GovShopManager getGovShopManager() { return govShopManager; }
    public MarketManager getMarketManager() { return marketManager; }
    public MessengerManager getMessengerManager() { return messengerManager; }
    public SkinManager getSkinManager() { return skinManager; }
    public LocationManager getLocationManager() { return locationManager; }
    public AdminPanel getAdminPanel() { return adminPanel; }

    public void disable() {
        vehicleManager.saveAll();
        bankManager.saveBalances();
        businessManager.saveAll();
        marketManager.saveAuctions();
        adminPanel.saveChatHistory();
        plugin.getLogger().info("Все данные сохранены.");
    }
}
