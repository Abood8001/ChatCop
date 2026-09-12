package dev.chatcop.hook;

import dev.chatcop.ChatCop;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Optional bStats metrics.
 *
 * The service id lives in config.yml and defaults to 0, which disables the
 * hook. Register the plugin at bstats.org, put the id it gives you into
 * metrics.service-id, and the charts start populating — shipping a hard-coded
 * id would otherwise send everyone's data to whichever project owns it.
 */
public final class MetricsHook {

    private MetricsHook() {}

    public static void register(ChatCop plugin) {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) return;

        int serviceId = plugin.getConfig().getInt("metrics.service-id", 0);
        if (serviceId <= 0) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[Metrics] No bStats service-id configured; metrics disabled.");
            }
            return;
        }

        try {
            Metrics metrics = new Metrics(plugin, serviceId);

            metrics.addCustomChart(new SimplePie("discord_alerts", () ->
                    plugin.getDiscordManager().isEnabled() ? "enabled" : "disabled"));
            metrics.addCustomChart(new SimplePie("command_filtering", () ->
                    plugin.getConfig().getBoolean("command-filter.enabled", true) ? "enabled" : "disabled"));
            metrics.addCustomChart(new SimplePie("censor_mode", () ->
                    plugin.getConfig().getBoolean("filters.profanity.censor-mode", true) ? "censor" : "block"));
        } catch (Throwable t) {
            plugin.getLogger().warning("[Metrics] Could not start bStats: " + t.getMessage());
        }
    }
}
