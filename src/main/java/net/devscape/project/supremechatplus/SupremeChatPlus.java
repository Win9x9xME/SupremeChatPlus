package net.devscape.project.supremechatplus;

import net.devscape.project.supremechatplus.config.ConfigManager;
import net.devscape.project.supremechatplus.listener.ChatTagListener;
import net.devscape.project.supremechatplus.resolver.TagResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class SupremeChatPlus extends JavaPlugin {

    private ConfigManager configManager;
    private TagResolver tagResolver;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.tagResolver = new TagResolver(this, configManager);
        getServer().getPluginManager().registerEvents(new ChatTagListener(this, tagResolver), this);
        getLogger().info("SupremeChatPlus has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SupremeChatPlus has been disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("supremechatplus")) {
            return false;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("supremechatplus.reload")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            reloadConfig();
            configManager.reload();
            sender.sendMessage(Component.text("SupremeChatPlus configuration has been reloaded.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("SupremeChatPlus v" + getDescription().getVersion(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Usage: /supremechatplus reload", NamedTextColor.GRAY));
        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TagResolver getTagResolver() {
        return tagResolver;
    }
}