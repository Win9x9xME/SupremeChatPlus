package net.devscape.project.supremechatplus.listener;

import net.devscape.project.supremechatplus.SupremeChatPlus;
import net.devscape.project.supremechatplus.resolver.TagResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Collection;

public class ChatTagListener implements Listener {

    private final SupremeChatPlus plugin;
    private final TagResolver tagResolver;

    public ChatTagListener(SupremeChatPlus plugin, TagResolver tagResolver) {
        this.plugin = plugin;
        this.tagResolver = tagResolver;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        if (!tagResolver.containsAnyTag(message)) {
            return;
        }

        String resolvedMessage = tagResolver.resolveTags(message, player);

        if (resolvedMessage.equals(message)) {
            return;
        }

        // Cancel the original event - we"ll send the component ourselves
        event.setCancelled(true);

        Component chatComponent = buildChatComponent(event, player, resolvedMessage);

        // Broadcast to all online players
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player online : onlinePlayers) {
            online.sendMessage(chatComponent);
        }

        // Send plain text version to console (hover/click are UI-only)
        String plainText = PlainTextComponentSerializer.plainText().serialize(chatComponent);
        Bukkit.getConsoleSender().sendMessage(plainText);
    }

    /**
     * Build the chat component from the event"s format string.
     * Handles multiple format patterns used by SupremeChat and other plugins:
     * - "&7%1$s &8➟ &7%2$s" (positional Java format)
     * - "&7%s &8➟ &7%s" (non-positional Java format)
     * - "&7PlayerName &8➟ &7%2$s" (player already injected)
     * - "&7PlayerName &8➟ &7message" (fully formatted - fallback)
     */
    private Component buildChatComponent(AsyncPlayerChatEvent event, Player player, String resolvedMessage) {
        String format = validateAndGetFormat(event.getFormat());
        String displayName = player.getDisplayName();

        String prefix;
        String suffix;

        // Strategy 1: Try %2$s (positional)
        int msgIndex = format.indexOf("%2$s");
        if (msgIndex >= 0) {
            String formatStr = format.replace("%1$s", displayName);
            prefix = formatStr.substring(0, msgIndex);
            suffix = formatStr.substring(msgIndex + 4);
        } else {
            // Strategy 2: Try %s (non-positional, second occurrence is the message)
            int firstIdx = format.indexOf("%s");
            if (firstIdx >= 0) {
                // Replace first %s with display name
                String before = format.substring(0, firstIdx);
                String after = format.substring(firstIdx + 2);
                String formatStr = before + displayName + after;
                // Find the next %s in the result
                int secondIdx = formatStr.indexOf("%s", before.length() + displayName.length());
                if (secondIdx >= 0) {
                    prefix = formatStr.substring(0, secondIdx);
                    suffix = formatStr.substring(secondIdx + 2);
                } else {
                    // Only one %s found - it was for the player, message is appended
                    prefix = formatStr;
                    suffix = "";
                }
            } else {
                // Strategy 3: No %s found - format may already contain the full text.
                // Try to locate the original message within the format.
                String originalMessage = event.getMessage();
                int msgPos = format.indexOf(originalMessage);
                if (msgPos >= 0) {
                    prefix = format.substring(0, msgPos);
                    suffix = format.substring(msgPos + originalMessage.length());
                } else {
                    // Fallback: prepend player name + separator
                    prefix = "\u00A77" + displayName + "\u00A78\u279E\u00A77 ";
                    suffix = "";
                }
            }
        }

        // Convert & to section sign for legacy component parsing
        prefix = prefix.replace("&", "\u00A7");
        suffix = suffix.replace("&", "\u00A7");

        // Parse legacy prefix and suffix
        Component prefixComponent = LegacyComponentSerializer.legacySection().deserialize(prefix);
        Component suffixComponent = LegacyComponentSerializer.legacySection().deserialize(suffix);

        // Parse the resolved message with MiniMessage (handles hover/click tags)
        Component messageComponent = tagResolver.parseToComponent(resolvedMessage);

        // Combine all parts
        return prefixComponent.append(messageComponent).append(suffixComponent);
    }

    /**
     * Validate and sanitize the chat format string to prevent abuse.
     * Limits length and removes potentially dangerous patterns.
     */
    private String validateAndGetFormat(String format) {
        if (format == null || format.isEmpty()) {
            return "&7%player% &8➟ &7%message%";
        }
        // Limit length to prevent abuse
        if (format.length() > 200) {
            format = format.substring(0, 200);
        }
        // Remove control characters
        // Control characters are in range 0x00-0x1F and 0x7F-0x9F
        StringBuilder safe = new StringBuilder();
        for (char c : format.toCharArray()) {
            if (c > 0x1F && (c < 0x7F || c > 0x9F) && c != '\0') {
                safe.append(c);
            }
        }
        return safe.toString();
    }
}