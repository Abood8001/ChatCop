package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PunishmentManager {

    private final ChatCop plugin;
    private TreeMap<Integer, Map<String, Object>> thresholds;
    private int decayPerMinute;
    private long cooldownMs;

    public PunishmentManager(ChatCop plugin) {
        this.plugin = plugin;
        loadThresholds();
    }

    private void loadThresholds() {
        thresholds = new TreeMap<>();
        decayPerMinute = plugin.getConfig().getInt("punishments.point-decay-per-minute", 2);
        cooldownMs = plugin.getConfig().getLong("punishments.punishment-cooldown-seconds", 3) * 1000L;

        var section = plugin.getConfig().getConfigurationSection("punishments.thresholds");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int pts = Integer.parseInt(key);
                var sub = section.getConfigurationSection(key);
                if (sub != null) thresholds.put(pts, sub.getValues(false));
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Awards points, runs per-filter punishment commands, then runs the highest
     * triggered threshold's commands. All gated behind a per-player cooldown so a
     * spammer can't trigger dozens of punishments in a couple of seconds.
     */
    public void applyPoints(Player player, PlayerData data, int pts, String filterName, String message, String reason) {
        data.setDecayPerMinute(decayPerMinute);
        data.addPoints(pts);
        int total = data.getPoints();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Debug] " + player.getName() + " now has " + total + " points");
        }

        // Cooldown gate — if still cooling down, skip running commands this time
        if (!data.tryPunish(cooldownMs)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[Debug] Punishment for " + player.getName() + " suppressed (cooldown)");
            }
            return;
        }

        // 1. Per-filter commands
        runFilterCommands(player, filterName, message, reason);

        // 2. Highest triggered threshold — but only when the player crosses INTO
        //    a higher threshold than the one already applied to them. Otherwise
        //    every message past the cooldown re-runs the top threshold's command,
        //    so an external `tempmute` keeps getting removed and re-applied and the
        //    mute timer never actually counts down (see repeated re-mute spam).
        int alreadyApplied = data.getLastPunishThreshold();

        // If points have decayed below what we last punished for, drop the marker
        // down to the highest threshold still met so good behaviour lets the
        // escalation start over instead of being stuck at the top forever.
        if (total < alreadyApplied) {
            int rebased = 0;
            for (Integer threshold : thresholds.keySet()) {
                if (total >= threshold) rebased = threshold;
            }
            alreadyApplied = rebased;
            data.setLastPunishThreshold(rebased);
        }

        Map.Entry<Integer, Map<String, Object>> triggered = null;
        for (Map.Entry<Integer, Map<String, Object>> entry : thresholds.entrySet()) {
            if (total >= entry.getKey()) triggered = entry;
        }
        if (triggered != null && triggered.getKey() > alreadyApplied) {
            runThresholdCommands(player, triggered.getValue(), message, reason, filterName);
            data.setLastPunishThreshold(triggered.getKey());
        }
    }

    private void runFilterCommands(Player player, String filterName, String message, String reason) {
        List<String> commands = plugin.getConfig().getStringList(
                "filters." + filterName.toLowerCase() + ".punishment.commands");
        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            runConsoleCommand(applyPlaceholders(cmd, player, "CONSOLE", reason, message, "", filterName));
        }
    }

    private void runThresholdCommands(Player player, Map<String, Object> data,
                                      String message, String reason, String filterName) {
        Object raw = data.get("commands");
        if (!(raw instanceof List<?> list)) return;
        for (Object obj : list) {
            if (!(obj instanceof String cmd) || cmd.isBlank()) continue;
            runConsoleCommand(applyPlaceholders(cmd, player, "CONSOLE", reason, message, "", filterName));
        }
    }

    public void warn(Player player, PlayerData data, String reason) {
        data.incrementWarnCount();
        data.addViolation("WARN: " + reason);
        String msg = plugin.getConfigManager().getMessage("warned",
                "{count}", String.valueOf(data.getWarnCount()),
                "{reason}", reason);
        player.sendMessage(plugin.getConfigManager().getPrefix() + msg);
    }

    public String applyPlaceholders(String input, Player player, String punisher,
                                    String reason, String message, String duration, String filter) {
        return input
                .replace("%player%",   player.getName())
                .replace("%punisher%", punisher)
                .replace("%reason%",   reason)
                .replace("%message%",  message)
                .replace("%duration%", duration)
                .replace("%filter%",   filter)
                .replace("%world%",    player.getWorld().getName());
    }

    private void runConsoleCommand(String cmd) {
        Scheduler.global(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    public void reload() {
        loadThresholds();
    }
}
