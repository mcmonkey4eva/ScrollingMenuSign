package me.desht.scrollingmenusign;

import com.comphenix.protocol.ProtocolLibrary;
import me.desht.dhutils.*;
import me.desht.dhutils.MetaFaker.MetadataFilter;
import me.desht.dhutils.commands.CommandManager;
import me.desht.dhutils.cost.EconomyCost;
import me.desht.dhutils.responsehandler.ResponseHandler;
import me.desht.scrollingmenusign.commandlets.*;
import me.desht.scrollingmenusign.commands.*;
import me.desht.scrollingmenusign.listeners.*;
import me.desht.scrollingmenusign.parser.CommandParser;
import me.desht.scrollingmenusign.spout.SpoutUtils;
import me.desht.scrollingmenusign.util.SMSUtil;
import me.desht.scrollingmenusign.util.UUIDMigration;
import me.desht.scrollingmenusign.variables.VariablesManager;
import me.desht.scrollingmenusign.views.*;
import me.desht.scrollingmenusign.views.action.RepaintAction;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;
import org.mcstats.Metrics.Plotter;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * ScrollingMenuSign
 *
 * @author desht
 */
public class ScrollingMenuSign extends JavaPlugin implements ConfigurationListener {

    public static final int BLOCK_TARGET_DIST = 4;
    public static final String CONSOLE_OWNER = "[console]";
    public static final UUID CONSOLE_UUID = new UUID(0, 0);

    private static ScrollingMenuSign instance = null;

    public static Economy economy = null;
    public static Permission permission = null;

    private final CommandManager cmds = new CommandManager(this);
    private final CommandletManager cmdlets = new CommandletManager(this);
    private final ViewManager viewManager = new ViewManager(this);
    private final LocationManager locationManager = new LocationManager();
    private final VariablesManager variablesManager = new VariablesManager(this);
    private final MenuManager menuManager = new MenuManager(this);
    private final SMSHandlerImpl handler = new SMSHandlerImpl(this);
    private final ConfigCache configCache = new ConfigCache();

    private boolean spoutEnabled = false;

    private ConfigurationManager configManager;

    public final ResponseHandler responseHandler = new ResponseHandler(this);
    private boolean protocolLibEnabled = false;
    private MetaFaker faker;
    private boolean vaultLegacyMode = false;
    private boolean holoAPIEnabled;

    @Override
    public void onLoad() {
        ConfigurationSerialization.registerClass(PersistableLocation.class);
    }

    @Override
    public void onEnable() {
        setInstance(this);

        LogUtils.init(this);

        DirectoryStructure.setupDirectoryStructure();

        configManager = new ConfigurationManager(this, this);
        configManager.setPrefix("sms");

        configCleanup();

        configCache.processConfig(getConfig());

        MiscUtil.init(this);
        MiscUtil.setColouredConsole(getConfig().getBoolean("sms.coloured_console"));

        Debugger.getInstance().setPrefix("[SMS] ");
        Debugger.getInstance().setLevel(getConfig().getInt("sms.debug_level"));
        Debugger.getInstance().setTarget(getServer().getConsoleSender());

        PluginManager pm = getServer().getPluginManager();
        setupSpout(pm);
        setupVault(pm);
        setupProtocolLib(pm);
        setupHoloAPI(pm);
        if (protocolLibEnabled) {
            ItemGlow.init(this);
            setupItemMetaFaker();
        }

        setupCustomFonts();

        new SMSPlayerListener(this);
        new SMSBlockListener(this);
        new SMSEntityListener(this);
        new SMSWorldListener(this);
        if (spoutEnabled) {
            new SMSSpoutKeyListener(this);
        }

        registerCommands();
        registerCommandlets();

        MessagePager.setPageCmd("/sms page [#|n|p]");
        MessagePager.setDefaultPageSize(getConfig().getInt("sms.pager.lines", 0));

        SMSScrollableView.setDefaultScrollType(SMSScrollableView.ScrollType.valueOf(getConfig().getString("sms.scroll_type").toUpperCase()));

        loadPersistedData();
        variablesManager.checkForUUIDMigration();

        if (spoutEnabled) {
            SpoutUtils.precacheTextures();
        }

        setupMetrics();

        Debugger.getInstance().debug(getDescription().getName() + " version " + getDescription().getVersion() + " is enabled!");

        UUIDMigration.migrateToUUID(this);
    }

    @Override
    public void onDisable() {
        SMSPersistence.saveMenusAndViews();
        SMSPersistence.saveMacros();
        SMSPersistence.saveVariables();
        for (SMSMenu menu : getMenuManager().listMenus()) {
            // this also deletes all the menu's views...
            menu.deleteTemporary();
        }
        for (SMSMacro macro : SMSMacro.listMacros()) {
            macro.deleteTemporary();
        }

        if (faker != null) {
            faker.shutdown();
        }

        economy = null;
        permission = null;
        setInstance(null);

        Debugger.getInstance().debug(getDescription().getName() + " version " + getDescription().getVersion() + " is disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return cmds.dispatch(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return cmds.onTabComplete(sender, command, label, args);
    }

    public SMSHandler getHandler() {
        return handler;
    }

    public boolean isSpoutEnabled() {
        return spoutEnabled;
    }

    public boolean isProtocolLibEnabled() {
        return protocolLibEnabled;
    }

    public boolean isHoloAPIEnabled() {
        return holoAPIEnabled;
    }

    public static ScrollingMenuSign getInstance() {
        return instance;
    }

    public CommandletManager getCommandletManager() {
        return cmdlets;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }

    /**
     * @return the viewManager
     */
    public ViewManager getViewManager() {
        return viewManager;
    }

    /**
     * @return the locationManager
     */
    public LocationManager getLocationManager() {
        return locationManager;
    }

    private void setupMetrics() {
        if (!getConfig().getBoolean("sms.mcstats")) {
            return;
        }

        try {
            Metrics metrics = new Metrics(this);

            Graph graphM = metrics.createGraph("Menu/View/Macro count");
            graphM.addPlotter(new Plotter("Menus") {
                @Override
                public int getValue() {
                    return getMenuManager().listMenus().size();
                }
            });
            graphM.addPlotter(new Plotter("Views") {
                @Override
                public int getValue() {
                    return viewManager.listViews().size();
                }
            });
            graphM.addPlotter(new Plotter("Macros") {
                @Override
                public int getValue() {
                    return SMSMacro.listMacros().size();
                }
            });

            Graph graphV = metrics.createGraph("View Types");
            for (final Entry<String, Integer> e : viewManager.getViewCounts().entrySet()) {
                graphV.addPlotter(new Plotter(e.getKey()) {
                    @Override
                    public int getValue() {
                        return e.getValue();
                    }
                });
            }
            metrics.start();
        } catch (IOException e) {
            LogUtils.warning("Can't submit metrics data: " + e.getMessage());
        }
    }

    private static void setInstance(ScrollingMenuSign plugin) {
        instance = plugin;
    }

    private void setupHoloAPI(PluginManager pm) {
        Plugin holoAPI = pm.getPlugin("HoloAPI");
        if (holoAPI != null && holoAPI.isEnabled()) {
            holoAPIEnabled = true;
            Debugger.getInstance().debug("Hooked HoloAPI v" + holoAPI.getDescription().getVersion());
        }
    }

    private void setupSpout(PluginManager pm) {
        Plugin spout = pm.getPlugin("Spout");
        if (spout != null && spout.isEnabled()) {
            spoutEnabled = true;
            Debugger.getInstance().debug("Hooked Spout v" + spout.getDescription().getVersion());
        }
    }

    private void setupVault(PluginManager pm) {
        Plugin vault = pm.getPlugin("Vault");
        if (vault != null && vault.isEnabled()) {
            int ver = PluginVersionChecker.getRelease(vault.getDescription().getVersion());
            Debugger.getInstance().debug("Hooked Vault v" + vault.getDescription().getVersion());
            vaultLegacyMode = ver < 1003000;  // Vault 1.3.0
            if (vaultLegacyMode) {
                LogUtils.warning("Detected an older version of Vault.  Proper UUID functionality requires Vault 1.4.1 or later.");
            }
            setupEconomy();
            setupPermission();
        } else {
            LogUtils.warning("Vault not loaded: no economy command costs & no permission group support");
        }
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        economy = economyProvider.getProvider();
        if (economyProvider == null) {
            LogUtils.warning("No economy plugin detected - economy command costs not available");
        }
    }

    private void setupPermission() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(Permission.class);
        permission = permissionProvider.getProvider();
        if (permission == null) {
            LogUtils.warning("No permissions plugin detected - no permission group support");
        }
    }

    public boolean isVaultLegacyMode() {
        return vaultLegacyMode;
    }

    private void setupProtocolLib(PluginManager pm) {
        Plugin pLib = pm.getPlugin("ProtocolLib");
        if (pLib != null && pLib instanceof ProtocolLibrary && pLib.isEnabled()) {
            protocolLibEnabled = true;
            Debugger.getInstance().debug("Hooked ProtocolLib v" + pLib.getDescription().getVersion());
        }
    }

    private void setupItemMetaFaker() {
        faker = new MetaFaker(this, new MetadataFilter() {
            @Override
            public ItemMeta filter(ItemMeta itemMeta, Player player) {
                if (player.getGameMode() == GameMode.CREATIVE) {
                    // messing with item meta in creative mode can have unwanted consequences
                    return null;
                }
                if (!ActiveItem.isActiveItem(itemMeta)) {
                    String[] f = PopupItem.getPopupItemFields(itemMeta);
                    if (f == null) {
                        return null;
                    }
                }
                // strip the last line from the lore for active items & popup items
                List<String> newLore = new ArrayList<String>(itemMeta.getLore());
                newLore.remove(newLore.size() - 1);
                ItemMeta newMeta = itemMeta.clone();
                newMeta.setLore(newLore);
                return newMeta;
            }
        });
    }

    private void registerCommands() {
        cmds.registerCommand(new AddItemCommand());
        cmds.registerCommand(new AddMacroCommand());
        cmds.registerCommand(new AddViewCommand());
        cmds.registerCommand(new CreateMenuCommand());
        cmds.registerCommand(new DeleteMenuCommand());
        cmds.registerCommand(new EditMenuCommand());
        cmds.registerCommand(new FontCommand());
        cmds.registerCommand(new GetConfigCommand());
        cmds.registerCommand(new GiveCommand());
        cmds.registerCommand(new ItemUseCommand());
        cmds.registerCommand(new ListMacroCommand());
        cmds.registerCommand(new ListMenusCommand());
        cmds.registerCommand(new MenuCommand());
        cmds.registerCommand(new PageCommand());
        cmds.registerCommand(new ReloadCommand());
        cmds.registerCommand(new RemoveItemCommand());
        cmds.registerCommand(new RemoveMacroCommand());
        cmds.registerCommand(new RemoveViewCommand());
        cmds.registerCommand(new RepaintCommand());
        cmds.registerCommand(new SaveCommand());
        cmds.registerCommand(new SetConfigCommand());
        cmds.registerCommand(new UndeleteMenuCommand());
        cmds.registerCommand(new VarCommand());
        cmds.registerCommand(new ViewCommand());
    }

    private void registerCommandlets() {
        cmdlets.registerCommandlet(new AfterCommandlet());
        cmdlets.registerCommandlet(new CooldownCommandlet());
        cmdlets.registerCommandlet(new PopupCommandlet());
        cmdlets.registerCommandlet(new SubmenuCommandlet());
        cmdlets.registerCommandlet(new CloseSubmenuCommandlet());
        cmdlets.registerCommandlet(new ScriptCommandlet());
        cmdlets.registerCommandlet(new QuickMessageCommandlet());
    }

    private void loadPersistedData() {
        SMSPersistence.loadMacros();
        SMSPersistence.loadVariables();
        SMSPersistence.loadMenus();
        SMSPersistence.loadViews();
    }

    public static URL makeImageURL(String path) throws MalformedURLException {
        if (path == null || path.isEmpty()) {
            throw new MalformedURLException("file must be non-null and not an empty string");
        }

        return makeImageURL(ScrollingMenuSign.getInstance().getConfig().getString("sms.resource_base_url"), path);
    }

    public static URL makeImageURL(String base, String path) throws MalformedURLException {
        if (path == null || path.isEmpty()) {
            throw new MalformedURLException("file must be non-null and not an empty string");
        }
        if ((base == null || base.isEmpty()) && !path.startsWith("http:")) {
            throw new MalformedURLException("base URL must be set (use /sms setcfg resource_base_url ...");
        }
        if (path.startsWith("http:") || base == null) {
            return new URL(path);
        } else {
            return new URL(new URL(base), path);
        }
    }

    @Override
    public Object onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
        if (key.equals("scroll_type")) {
            try {
                SMSScrollableView.ScrollType t = SMSGlobalScrollableView.ScrollType.valueOf(newVal.toString().toUpperCase());
                DHValidate.isTrue(t != SMSGlobalScrollableView.ScrollType.DEFAULT, "Scroll type must be one of SCROLL/PAGE");
            } catch (IllegalArgumentException e) {
                throw new DHUtilsException("Scroll type must be one of SCROLL/PAGE");
            }
        } else if (key.equals("debug_level")) {
            DHValidate.isTrue((Integer) newVal >= 0, "Debug level must be >= 0");
        } else if (key.equals("submenus.back_item.material") || key.equals("inv_view.default_icon")) {
            try {
                SMSUtil.parseMaterialSpec(newVal.toString());
            } catch (IllegalArgumentException e) {
                throw new DHUtilsException("Invalid material specification: " + newVal.toString());
            }
        }
        return newVal;
    }

    @Override
    public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
        if (key.startsWith("actions.spout") && isSpoutEnabled()) {
            // reload & re-cache spout key definitions
            SpoutUtils.loadKeyDefinitions();
        } else if (key.startsWith("spout.") && isSpoutEnabled()) {
            // settings which affects how spout views are drawn
            repaintViews("spout");
        } else if (key.equalsIgnoreCase("command_log_file")) {
            CommandParser.setLogFile(newVal.toString());
        } else if (key.equalsIgnoreCase("debug_level")) {
            Debugger.getInstance().setLevel((Integer) newVal);
        } else if (key.startsWith("item_prefix.") || key.endsWith("_justify") || key.equals("max_title_lines") || key.startsWith("submenus.")) {
            // settings which affect how all views are drawn
            if (key.equals("item_prefix.selected")) {
                configCache.setPrefixSelected(newVal.toString());
            } else if (key.equals("item_prefix.not_selected")) {
                configCache.setPrefixNotSelected(newVal.toString());
            } else if (key.equals("submenus.back_item.label")) {
                configCache.setSubmenuBackLabel(newVal.toString());
            } else if (key.equals("submenus.back_item.material")) {
                configCache.setSubmenuBackIcon(newVal.toString());
            } else if (key.equals("submenus.title_prefix")) {
                configCache.setSubmenuTitlePrefix(newVal.toString());
            }
            repaintViews(null);
        } else if (key.equals("coloured_console")) {
            MiscUtil.setColouredConsole((Boolean) newVal);
        } else if (key.equals("scroll_type")) {
            SMSScrollableView.setDefaultScrollType(SMSGlobalScrollableView.ScrollType.valueOf(newVal.toString().toUpperCase()));
            repaintViews(null);
        } else if (key.equals("no_physics")) {
            configCache.setPhysicsProtected((Boolean) newVal);
        } else if (key.equals("no_break_signs")) {
            configCache.setBreakProtected((Boolean) newVal);
        } else if (key.equals("inv_view.default_icon")) {
            configCache.setDefaultInventoryViewIcon(newVal.toString());
        } else if (key.equals("user_variables.fallback_sub")) {
            configCache.setFallbackUserVarSub(newVal.toString());
        }
    }

    public ConfigCache getConfigCache() {
        return configCache;
    }

    private void repaintViews(String type) {
        for (SMSView v : viewManager.listViews()) {
            if (type == null || v.getType().equals(type)) {
                v.update(null, new RepaintAction());
            }
        }
    }

    public void setupCustomFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        //noinspection ConstantConditions
        for (File f : DirectoryStructure.getFontsFolder().listFiles()) {
            String n = f.getName().toLowerCase();
            int type;
            if (n.endsWith(".ttf")) {
                type = Font.TRUETYPE_FONT;
            } else if (n.endsWith(".pfa") || n.endsWith(".pfb") || n.endsWith(".pfm") || n.endsWith(".afm")) {
                type = Font.TYPE1_FONT;
            } else {
                continue;
            }
            try {
                ge.registerFont(Font.createFont(type, f));
                Debugger.getInstance().debug("registered font: " + f.getName());
            } catch (Exception e) {
                LogUtils.warning("can't load custom font " + f + ": " + e.getMessage());
            }
        }
    }

    private void configCleanup() {
        String[] obsolete = new String[]{
                "sms.maps.break_block_id", "sms.autosave", "sms.menuitem_separator",
                "sms.persistent_user_vars", "uservar",
        };

        boolean changed = false;
        Configuration config = getConfig();
        for (String k : obsolete) {
            if (config.contains(k)) {
                config.set(k, null);
                LogUtils.info("removed obsolete config item: " + k);
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
        }
    }

    public VariablesManager getVariablesManager() {
        return variablesManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }
}
