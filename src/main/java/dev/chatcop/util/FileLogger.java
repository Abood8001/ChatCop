package dev.chatcop.util;

import dev.chatcop.ChatCop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogger {

    private final ChatCop plugin;
    private final File logFile;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileLogger(ChatCop plugin) {
        this.plugin = plugin;
        String path = plugin.getConfig().getString("general.log-file", "logs/chatcop.log");
        this.logFile = new File(plugin.getDataFolder(), path);
        logFile.getParentFile().mkdirs();
    }

    public void log(String player, String filter, String message) {
        if (!plugin.getConfigManager().isLogToFile()) return;
        String entry = "[" + LocalDateTime.now().format(FMT) + "] [" + filter + "] " + player + ": " + message;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile, true))) {
            w.write(entry);
            w.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write to log file: " + e.getMessage());
        }
    }
}
