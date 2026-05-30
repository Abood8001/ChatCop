package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {

    private final ChatCop plugin;
    private final File file;

    private final AtomicLong totalMessages   = new AtomicLong(0);
    private final AtomicLong blockedMessages = new AtomicLong(0);
    private final AtomicLong censoredMessages = new AtomicLong(0);
    private final AtomicLong totalMutes      = new AtomicLong(0);
    private final AtomicLong totalWarns      = new AtomicLong(0);

    public StatsManager(ChatCop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/stats.yml");
        file.getParentFile().mkdirs();
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        totalMessages.set(yaml.getLong("total-messages", 0));
        blockedMessages.set(yaml.getLong("blocked-messages", 0));
        censoredMessages.set(yaml.getLong("censored-messages", 0));
        totalMutes.set(yaml.getLong("total-mutes", 0));
        totalWarns.set(yaml.getLong("total-warns", 0));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("total-messages", totalMessages.get());
        yaml.set("blocked-messages", blockedMessages.get());
        yaml.set("censored-messages", censoredMessages.get());
        yaml.set("total-mutes", totalMutes.get());
        yaml.set("total-warns", totalWarns.get());
        try { yaml.save(file); } catch (IOException ignored) {}
    }

    public void recordMessage()  { totalMessages.incrementAndGet(); }
    public void recordBlock()    { blockedMessages.incrementAndGet(); }
    public void recordCensor()   { censoredMessages.incrementAndGet(); }
    public void recordMute()     { totalMutes.incrementAndGet(); }
    public void recordWarn()     { totalWarns.incrementAndGet(); }

    public long getTotalMessages()    { return totalMessages.get(); }
    public long getBlockedMessages()  { return blockedMessages.get(); }
    public long getCensoredMessages() { return censoredMessages.get(); }
    public long getTotalMutes()       { return totalMutes.get(); }
    public long getTotalWarns()       { return totalWarns.get(); }

    public double getBlockRate() {
        long total = totalMessages.get();
        return total == 0 ? 0.0 : (blockedMessages.get() * 100.0) / total;
    }
}
