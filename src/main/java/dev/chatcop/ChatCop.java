package dev.chatcop;

import dev.chatcop.command.ChatCopCommand;
import dev.chatcop.command.MuteCommand;
import dev.chatcop.command.UnmuteCommand;
import dev.chatcop.command.WarnCommand;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.hook.MetricsHook;
import dev.chatcop.listener.BookSignListener;
import dev.chatcop.listener.ChatListener;
import dev.chatcop.listener.CommandListener;
import dev.chatcop.manager.ChatControlManager;
import dev.chatcop.manager.DiscordWebhookManager;
import dev.chatcop.manager.FilterManager;
import dev.chatcop.manager.MuteManager;
import dev.chatcop.manager.NotificationManager;
import dev.chatcop.manager.PlayerDataStore;
import dev.chatcop.manager.PunishmentManager;
import dev.chatcop.manager.StatsManager;
import dev.chatcop.util.FileLogger;
import dev.chatcop.util.Scheduler;
import dev.chatcop.util.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatCop extends JavaPlugin {

    private static ChatCop instance;

    private ConfigManager configManager;
    private FilterManager filterManager;
    private MuteManager muteManager;
    private PunishmentManager punishmentManager;
    private StatsManager statsManager;
    private PlayerDataStore playerDataStore;
    private NotificationManager notificationManager;
    private DiscordWebhookManager discordManager;
    private ChatControlManager chatControlManager;
    private FileLogger fileLogger;
    private UpdateChecker updateChecker;

    private ChatListener chatListener;
    private CommandListener commandListener;
    private BookSignListener bookSignListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Managers. Order matters: ConfigManager first, then anything reading config.
        configManager       = new ConfigManager(this);
        statsManager        = new StatsManager(this);
        playerDataStore     = new PlayerDataStore(this);
        muteManager         = new MuteManager(this);
        filterManager       = new FilterManager(this);
        punishmentManager   = new PunishmentManager(this);
        notificationManager = new NotificationManager(this);
        discordManager      = new DiscordWebhookManager(this);
        chatControlManager  = new ChatControlManager(this);
        fileLogger          = new FileLogger(this);

        // Listeners
        chatListener     = new ChatListener(this);
        commandListener  = new CommandListener(this);
        bookSignListener = new BookSignListener(this);

        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(commandListener, this);
        getServer().getPluginManager().registerEvents(bookSignListener, this);

        // Commands
        register("chatcop", new ChatCopCommand(this));
        register("ccmute", new MuteCommand(this));
        register("ccunmute", new UnmuteCommand(this));
        register("ccwarn", new WarnCommand(this));

        startTasks();
        registerHooks();

        updateChecker = new UpdateChecker(this);
        updateChecker.checkAsync();

        getLogger().info("ChatCop v" + getDescription().getVersion() + " enabled"
                + (Scheduler.isFolia() ? " (Folia mode)." : "."));
    }

    @Override
    public void onDisable() {
        if (muteManager != null) muteManager.saveMutes();
        if (statsManager != null) statsManager.save();
        if (filterManager != null) filterManager.stageAll();
        if (playerDataStore != null) playerDataStore.flush();
        if (fileLogger != null) fileLogger.flush();
        getLogger().info("ChatCop disabled.");
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml; skipping registration.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
        applyPermissionMessage(command);
    }

    /**
     * Bukkit checks the plugin.yml permission before the executor runs and
     * sends its own generic message, which meant the configurable
     * messages.no-permission text was never actually shown. Pushing it onto the
     * command makes the configured message the one players see.
     */
    private void applyPermissionMessage(PluginCommand command) {
        String message = configManager.getMessage("no-permission");
        if (message != null && !message.isEmpty()) {
            command.setPermissionMessage(configManager.getPrefix() + message);
        }
    }

    private void startTasks() {
        // Periodic stats flush so a crash doesn't lose everything since the
        // last clean shutdown. Mutes already persist on each change.
        Scheduler.asyncTimerSeconds(this, () -> statsManager.save(), 300, 300);

        // Player points/warnings/history.
        Scheduler.asyncTimerSeconds(this, () -> {
            filterManager.stageAll();
            playerDataStore.flush();
        }, 300, 300);

        // Buffered violation log.
        Scheduler.asyncTimerSeconds(this, () -> fileLogger.flush(), 5, 5);

        // Drain one queued Discord alert at a time so bursts don't trip rate limits.
        long interval = Math.max(1L, getConfig().getLong("discord.send-interval-seconds", 2));
        Scheduler.asyncTimerSeconds(this, () -> discordManager.drainOne(), interval, interval);
    }

    private void registerHooks() {
        MetricsHook.register(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                // Referenced only inside this branch, so the class is never
                // loaded when PlaceholderAPI is absent.
                new dev.chatcop.hook.PlaceholderHook(this).register();
                getLogger().info("Hooked into PlaceholderAPI.");
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        filterManager.reload();
        muteManager.reload();
        punishmentManager.reload();
        notificationManager.reload();
        discordManager.reload();
        chatControlManager.reload();
        playerDataStore.reload();
        fileLogger.reload();

        if (chatListener != null) chatListener.reload();
        if (commandListener != null) commandListener.reload();
        if (bookSignListener != null) bookSignListener.reload();

        for (String name : new String[]{"chatcop", "ccmute", "ccunmute", "ccwarn"}) {
            PluginCommand command = getCommand(name);
            if (command != null) applyPermissionMessage(command);
        }
    }

    public static ChatCop getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public FilterManager getFilterManager() { return filterManager; }
    public MuteManager getMuteManager() { return muteManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public PlayerDataStore getPlayerDataStore() { return playerDataStore; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public DiscordWebhookManager getDiscordManager() { return discordManager; }
    public ChatControlManager getChatControlManager() { return chatControlManager; }
    public FileLogger getFileLogger() { return fileLogger; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
