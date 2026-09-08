package net.devscape.project.supremechatplus.resolver;

import net.devscape.project.supremechatplus.SupremeChatPlus;
import net.devscape.project.supremechatplus.config.ConfigManager;
import net.devscape.project.supremechatplus.config.ConfigManager.TagConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TagResolver {

    private final SupremeChatPlus plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage;
    private final boolean placeholderApiAvailable;

    public TagResolver(SupremeChatPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.placeholderApiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /**
     * Check if the message contains any enabled tag triggers.
     */
    public boolean containsAnyTag(String message) {
        String lower = message.toLowerCase();
        for (TagConfig tag : configManager.getTags().values()) {
            if (tag.isEnabled() && lower.contains(tag.getTrigger().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replace all enabled tags in the message with MiniMessage format strings.
     * Returns the modified message string with MiniMessage tags.
     */
    public String resolveTags(String message, Player player) {
        String result = message;

        for (TagConfig tag : configManager.getTags().values()) {
            if (!tag.isEnabled()) {
                continue;
            }

            String trigger = tag.getTrigger();
            if (!result.toLowerCase().contains(trigger.toLowerCase())) {
                continue;
            }

            if (!hasPermission(player, tag)) {
                continue;
            }

            String replacement = buildMiniMessageTag(tag, player);
            result = replaceCaseInsensitive(result, trigger, replacement);
        }

        return result;
    }

    /**
     * Parse a MiniMessage string to a Component.
     */
    public Component parseToComponent(String miniMessageString) {
        return miniMessage.deserialize(miniMessageString);
    }

    /**
     * Build a MiniMessage-formatted interactive tag string.
     */
    private String buildMiniMessageTag(TagConfig tag, Player player) {
        String displayText = tag.getDisplayText();
        String hoverText = tag.getHoverText();
        String clickCommand = tag.getClickCommand();

        // Replace placeholders
        displayText = applyPlaceholders(player, displayText);
        hoverText = applyPlaceholders(player, hoverText);
        clickCommand = applyPlaceholders(player, clickCommand);

        // Convert & color codes to MiniMessage format (preserve existing MiniMessage tags)
        displayText = legacyToMiniMessage(displayText);
        hoverText = legacyToMiniMessage(hoverText);

        // Escape values for MiniMessage context
        clickCommand = escapeMiniMessageQuote(clickCommand);
        hoverText = escapeMiniMessageQuote(hoverText);

        // SANITIZE click command: only allow safe characters to prevent command injection
        // Only allow alphanumeric, spaces, basic punctuation, and %player% placeholders
        clickCommand = sanitizeClickCommand(clickCommand);

        // Build MiniMessage with hover and click events
        return "<click:run_command:'" + clickCommand + "'>"
                + "<hover:show_text:'" + hoverText + "'>"
                + displayText
                + "</hover></click>";
    }

    /**
     * Sanitize click command to prevent command injection.
     * Only allows: alphanumeric, spaces, basic punctuation (.-_), and %placeholders%
     * Rejects or neutralizes anything that looks like command injection.
     */
    private String sanitizeClickCommand(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int len = command.length();
        for (int i = 0; i < len; i++) {
            char c = command.charAt(i);
            // Allow letters (both cases), digits, spaces, and basic punctuation
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '-' || c == '_' || c == '%') {
                result.append(c);
            } else {
                // Replace unsafe characters with underscore to maintain structure
                result.append('_');
            }
        }
        // Ensure result doesn't start with a space or underscore that could be exploited
        if (result.length() > 0) {
            char first = result.charAt(0);
            if (first == ' ' || first == '_') {
                result.setCharAt(0, 'c'); // default to "command" prefix
            }
        }
        return result.toString();
    }

    /**
     * Apply PlaceholderAPI placeholders and internal variables.
     */
    private String applyPlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }

        String result = text.replace("%player%", player.getName());
        result = result.replace("%display_name%", player.getDisplayName());
        result = result.replace("%world%", player.getWorld().getName());

        if (result.contains("%item_name%")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            String itemName = getItemName(item);
            itemName = tagSafe(itemName);
            result = result.replace("%item_name%", itemName);
        }

        if (placeholderApiAvailable) {
            try {
                result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
            } catch (Exception ignored) {
                // Silently ignore PlaceholderAPI errors
            }
        }

        return result;
    }

    /**
     * Get the display name of an ItemStack.
     */
    private String getItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Empty";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1))
              .append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Check if the player has permission for the tag.
     */
    private boolean hasPermission(Player player, TagConfig tag) {
        String permission = tag.getPermission();
        if (permission == null || permission.isEmpty() || permission.equalsIgnoreCase("none")) {
            return true;
        }
        return player.hasPermission(permission);
    }

    /**
     * Replace all occurrences of a trigger (case-insensitive) in a string.
     */
    private String replaceCaseInsensitive(String source, String target, String replacement) {
        if (source == null || target == null || target.isEmpty()) {
            return source;
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();
        while (true) {
            int pos = lowerSource.indexOf(lowerTarget, idx);
            if (pos < 0) {
                sb.append(source.substring(idx));
                break;
            }
            sb.append(source, idx, pos);
            sb.append(replacement);
            idx = pos + target.length();
        }
        return sb.toString();
    }

    /**
     * Convert legacy & color codes to MiniMessage format.
     * Preserves existing MiniMessage tags (escapes < > to prevent tag injection).
     * Only converts &0-&f, &k-&o, &r to their MiniMessage equivalents.
     */
    private String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < len) {
                char code = text.charAt(i + 1);
                String mmTag = legacyColorToMiniMessage(code);
                if (mmTag != null) {
                    result.append(mmTag);
                    i++;
                    continue;
                }
            }
            // Escape < and > to prevent MiniMessage tag injection from user-controlled content
            if (c == '<') {
                result.append("\\<");
                continue;
            }
            if (c == '>') {
                result.append("\\>");
                continue;
            }
            // Pass through all other characters unmodified
            result.append(c);
        }
        return result.toString();
    }

    /**
     * Map a legacy color code to a MiniMessage tag.
     */
    private String legacyColorToMiniMessage(char code) {
        switch (code) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";
            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return null;
        }
    }

    /**
     * Escape a string for use inside MiniMessage single-quoted values.
     * Escapes backslash and single quote.
     */
    private String escapeMiniMessageQuote(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("'", "\\'");
    }

    /**
     * Strip or escape characters that could break MiniMessage parsing.
     * Used for dynamic content like item names.
     * Replaces section sign (u00A7) and removes problematic control characters.
     */
    private String tagSafe(String text) {
        if (text == null) return "";
        // Replace u00A7 color codes with empty string
        String result = text.replace("\u00A7", "");
        // Escape MiniMessage special characters
        result = result.replace("\\", "\\\\")
                       .replace("'", "\\'")
                       .replace("<", "\\<")
                       .replace(">", "\\>");
        return result;
    }
}