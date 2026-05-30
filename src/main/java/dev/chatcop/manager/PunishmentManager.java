package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PunishmentManager {

    private final ChatCop plugin;
    private TreeMap<Integer, Map<String, Object>> thresholds;

    public PunishmentManager(ChatCop plugin) {
        this.plugin = plugin;
        loadThresholds();
    }

    private void loadThresholds() {
        thresholds = new TreeMap<>();
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

    public void applyPoints(Player player, PlayerData data, int pts, String filterName, String message, String reason) {
        runFilterCommands(player, filterName, message, reason);
    }

    private void runFilterCommands(Player player, String filterName, String message, String reason) {
        List<String> commands = plugin.getConfig().getStringList(
                "filters." + filterName.toLowerCase() + ".punishment.commands");
        for (String cmd : commands) {
            runConsoleCommand(applyPlaceholders(cmd, player, "CONSOLE", reason, message, "", filterName));
        }
    }

    private void runThresholdCommands(Player player, Map<String, Object> data,
                                      String message, String reason, String filterName) {
        Object raw = data.get("commands");
        if (!(raw instanceof List<?> list)) return;
        for (Object obj : list) {
            if (!(obj instanceof String cmd)) continue;
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
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    public void reload() {
        loadThresholds();
    }
}