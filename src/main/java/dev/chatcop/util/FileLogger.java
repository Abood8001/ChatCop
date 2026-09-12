package dev.chatcop.util;

import dev.chatcop.ChatCop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Buffered, rotating violation log.
 *
 * Entries are queued and flushed on a timer instead of opening and closing the
 * file for every single message on the chat thread, which turned a spam wave
 * into one file handle per message.
 */
public class FileLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ChatCop plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final Object writeLock = new Object();

    private volatile File logFile;
    private volatile boolean enabled;
    private volatile long maxBytes;

    public FileLogger(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    /** Re-reads the configured path, so /chatcop reload can move the log. */
    public final void load() {
        enabled = plugin.getConfig().getBoolean("general.log-to-file", true);
        String path = plugin.getConfig().getString("general.log-file", "logs/chatcop.log");
        maxBytes = Math.max(0L, plugin.getConfig().getLong("general.log-max-size-kb", 5120)) * 1024L;

        File resolved = new File(plugin.getDataFolder(), path);
        File parent = resolved.getParentFile();
        if (parent != null) parent.mkdirs();
        this.logFile = resolved;
    }

    public void reload() {
        flush();
        load();
    }

    public void log(String player, String filter, String message) {
        if (!enabled) return;
        queue.add("[" + LocalDateTime.now().format(FMT) + "] [" + filter + "] " + player + ": " + message);

        // Hard cap so a stalled flush can't grow without bound.
        if (queue.size() > 5000) flush();
    }

    /** Writes everything queued. Called from the async flush task and on disable. */
    public void flush() {
        if (queue.isEmpty()) return;

        List<String> batch = new ArrayList<>();
        String entry;
        while ((entry = queue.poll()) != null) batch.add(entry);
        if (batch.isEmpty()) return;

        synchronized (writeLock) {
            File target = logFile;
            try {
                rotateIfNeeded(target);
                try (Writer w = new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(target.toPath(),
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                        StandardCharsets.UTF_8))) {
                    for (String line : batch) {
                        w.write(line);
                        w.write(System.lineSeparator());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write to log file: " + e.getMessage());
            }
        }
    }

    /** Renames the log aside once it passes the configured size. 0 disables rotation. */
    private void rotateIfNeeded(File target) {
        if (maxBytes <= 0 || !target.exists() || target.length() < maxBytes) return;

        String name = target.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";

        File rotated = new File(target.getParentFile(),
                base + "-" + LocalDateTime.now().format(STAMP) + ext);
        if (!target.renameTo(rotated)) {
            plugin.getLogger().warning("Could not rotate log file " + target.getName());
        }
    }
}
