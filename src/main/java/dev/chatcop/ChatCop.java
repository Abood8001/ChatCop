package dev.chatcop;

import dev.chatcop.command.ChatCopCommand;
import dev.chatcop.command.MuteCommand;
import dev.chatcop.command.UnmuteCommand;
import dev.chatcop.command.WarnCommand;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.listener.ChatListener;
import dev.chatcop.manager.FilterManager;
import dev.chatcop.manager.MuteManager;
import dev.chatcop.manager.PunishmentManager;
import dev.chatcop.manager.StatsManager;
import dev.chatcop.util.Scheduler;
import dev.chatcop.util.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatCop extends JavaPlugin {

    private static ChatCop instance;
    private ConfigManager configManager;
    private FilterManager filterManager;
    private MuteManager muteManager;
    private PunishmentManager punishmentManager;
    private StatsManager statsManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Init managers
        configManager = new ConfigManager(this);
        statsManager = new StatsManager(this);
        muteManager = new MuteManager(this);
        filterManager = new FilterManager(this);
        punishmentManager = new PunishmentManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Register commands
        getCommand("chatcop").setExecutor(new ChatCopCommand(this));
        getCommand("ccmute").setExecutor(new MuteCommand(this));
        getCommand("ccunmute").setExecutor(new UnmuteCommand(this));
        getCommand("ccwarn").setExecutor(new WarnCommand(this));

        // Periodically flush stats so a crash doesn't lose everything since the
        // last clean shutdown. Mutes already persist on each change.
        Scheduler.asyncTimerSeconds(this, () -> statsManager.save(), 300, 300); // every 5 minutes

        // Check for updates (async, opt-out via config)
        updateChecker = new UpdateChecker(this);
        updateChecker.checkAsync();

        getLogger().info("ChatCop v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (muteManager != null) muteManager.saveMutes();
        if (statsManager != null) statsManager.save();
        getLogger().info("ChatCop disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        filterManager.reload();
        muteManager.reload();
        punishmentManager.reload();
    }

    public static ChatCop getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public FilterManager getFilterManager() { return filterManager; }
    public MuteManager getMuteManager() { return muteManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
