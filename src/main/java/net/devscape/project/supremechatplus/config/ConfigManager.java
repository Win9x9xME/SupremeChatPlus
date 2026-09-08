package net.devscape.project.supremechatplus.config;

import net.devscape.project.supremechatplus.SupremeChatPlus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final SupremeChatPlus plugin;
    private Map<String, TagConfig> tags;

    public ConfigManager(SupremeChatPlus plugin) {
        this.plugin = plugin;
        this.tags = new LinkedHashMap<>();
        loadTags();
    }

    public void reload() {
        plugin.reloadConfig();
        loadTags();
    }

    private void loadTags() {
        tags.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("tags");
        if (section == null) {
            return;
        }
        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            ConfigurationSection tagSection = section.getConfigurationSection(key);
            if (tagSection == null) continue;

            // Validate and sanitize config values with length limits and character checks
            boolean enabled = tagSection.getBoolean("enabled", true);
            String trigger = sanitizeTagTrigger(key, tagSection.getString("trigger", "[" + key + "]"));
            String displayText = sanitizeConfigValue(tagSection.getString("display_text", "&a[" + key + "]"), 100);
            String hoverText = sanitizeConfigValue(tagSection.getString("hover_text", "&7Click to view"), 200);
            String clickCommand = sanitizeClickCommand(tagSection.getString("click_command", "/say " + key));
            String permission = sanitizePermissionNode(key, tagSection.getString("permission", "supremechatplus.tag." + key));

            tags.put(key, new TagConfig(
                    key, enabled, trigger, displayText,
                    hoverText, clickCommand, permission
            ));
        }
    }

    /**
     * Sanitize tag trigger: only allow alphanumeric, brackets, and basic chars.
     * Prevents overly long or malicious triggers.
     */
    private String sanitizeTagTrigger(String key, String trigger) {
        if (trigger == null || trigger.isEmpty()) {
            return "[" + key + "]";
        }
        // Limit length
        if (trigger.length() > 30) {
            trigger = trigger.substring(0, 30);
        }
        // Only allow safe characters: alphanumeric, spaces, brackets, underscores
        StringBuilder safe = new StringBuilder();
        for (char c : trigger.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '[' || c == ']' || c == '_' || c == ' ') {
                safe.append(c);
            }
        }
        return safe.toString().isEmpty() ? "[" + key + "]" : safe.toString();
    }

    /**
     * Sanitize config value: limit length and allow only safe characters.
     */
    private String sanitizeConfigValue(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // Trim to max length
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        // Remove control characters and null bytes
        // Control characters are in range 0x00-0x1F and 0x7F-0x9F
        StringBuilder safe = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c > 0x1F && (c < 0x7F || c > 0x9F) && c != '\0') {
                safe.append(c);
            }
        }
        return safe.toString();
    }

    /**
     * Sanitize permission node: only allow alphanumeric, dots, and underscores.
     */
    private String sanitizePermissionNode(String key, String permission) {
        if (permission == null || permission.isEmpty()) {
            return "supremechatplus.tag." + key;
        }
        // Limit length
        if (permission.length() > 50) {
            permission = permission.substring(0, 50);
        }
        // Only allow alphanumeric, dots, and underscores
        StringBuilder safe = new StringBuilder();
        for (char c : permission.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                safe.append(c);
            }
        }
        String result = safe.toString();
        return result.isEmpty() ? "supremechatplus.tag." + key : result;
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

    public Map<String, TagConfig> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public TagConfig getTag(String key) {
        return tags.get(key);
    }

    public static class TagConfig {
        private final String key;
        private final boolean enabled;
        private final String trigger;
        private final String displayText;
        private final String hoverText;
        private final String clickCommand;
        private final String permission;

        public TagConfig(String key, boolean enabled, String trigger,
                         String displayText, String hoverText,
                         String clickCommand, String permission) {
            this.key = key;
            this.enabled = enabled;
            this.trigger = trigger;
            this.displayText = displayText;
            this.hoverText = hoverText;
            this.clickCommand = clickCommand;
            this.permission = permission;
        }

        public String getKey() { return key; }
        public boolean isEnabled() { return enabled; }
        public String getTrigger() { return trigger; }
        public String getDisplayText() { return displayText; }
        public String getHoverText() { return hoverText; }
        public String getClickCommand() { return clickCommand; }
        public String getPermission() { return permission; }
    }
}