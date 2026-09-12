package dev.chatcop.listener;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Extends filtering beyond chat to the other places players can write text:
 * signs, written books and anvil renames. All three were previously
 * unfiltered, which made them the obvious place to put anything the chat
 * filter would have caught.
 */
public class BookSignListener implements Listener {

    private final ChatCop plugin;

    private volatile boolean filterSigns = true;
    private volatile boolean filterBooks = true;
    private volatile boolean filterAnvil = true;

    public BookSignListener(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        filterSigns = plugin.getConfig().getBoolean("extras.filter-signs", true);
        filterBooks = plugin.getConfig().getBoolean("extras.filter-books", true);
        filterAnvil = plugin.getConfig().getBoolean("extras.filter-anvil", true);
    }

    public void reload() { load(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (!filterSigns) return;
        Player player = event.getPlayer();
        if (player.hasPermission("chatcop.bypass")) return;

        StringBuilder combined = new StringBuilder();
        for (String line : event.getLines()) {
            if (line != null && !line.isBlank()) combined.append(line).append(' ');
        }
        String text = combined.toString().trim();
        if (text.isEmpty()) return;

        FilterResult result = check(player, text);
        if (result == null) return;

        event.setCancelled(true);
        deny(player, result, text, "SIGN");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        if (!filterBooks) return;
        Player player = event.getPlayer();
        if (player.hasPermission("chatcop.bypass")) return;

        BookMeta meta = event.getNewBookMeta();
        if (meta == null) return;

        StringBuilder combined = new StringBuilder();
        if (meta.hasTitle() && meta.getTitle() != null) combined.append(meta.getTitle()).append(' ');
        List<String> pages = meta.getPages();
        for (String page : pages) {
            if (page != null && !page.isBlank()) combined.append(page).append(' ');
        }
        String text = combined.toString().trim();
        if (text.isEmpty()) return;

        FilterResult result = check(player, text);
        if (result == null) return;

        event.setCancelled(true);
        deny(player, result, truncate(text), "BOOK");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!filterAnvil) return;

        ItemStack result = event.getResult();
        if (result == null) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String name = meta.getDisplayName();
        if (name.isBlank()) return;

        List<HumanEntity> viewers = event.getViewers();
        if (viewers.isEmpty()) return;
        HumanEntity viewer = viewers.get(0);
        if (!(viewer instanceof Player player)) return;
        if (player.hasPermission("chatcop.bypass")) return;

        FilterResult filtered = check(player, name);
        if (filtered == null) return;

        event.setResult(null);
        deny(player, filtered, name, "ANVIL");
    }

    /** @return the offending result, or null when the text is clean. */
    private FilterResult check(Player player, String text) {
        // Dry run: writing a book shouldn't count towards the player's chat
        // flood window or duplicate-message history.
        FilterResult result = plugin.getFilterManager().process(player, text, true);
        return result.isClean() || result.getAction() == FilterResult.Action.CENSOR ? null : result;
    }

    private void deny(Player player, FilterResult result, String text, String context) {
        player.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("blocked", "{reason}", result.getReason()));

        PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
        data.addViolation("[" + result.getFilterName() + " " + context + "] " + text);

        plugin.getPunishmentManager().applyPoints(
                player, data, result.getPoints(),
                result.getFilterName(), text, result.getReason());

        plugin.getNotificationManager().alertStaff(
                player, result.getFilterName() + " [" + context + "]", text, result.getReason());
        plugin.getDiscordManager().sendAlert(
                player, result.getFilterName(), "[" + context + "] " + text, result.getReason(), "CONSOLE");
        plugin.getFileLogger().log(player.getName(), result.getFilterName() + " [" + context + "]", text);
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 197) + "..." : text;
    }
}
