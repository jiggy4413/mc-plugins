package io.github.jiggy4413.prisoncore;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Starter plugin. Its only job right now is to prove the build pipeline works:
 * build the jar, drop it in /plugins, restart, run /prisoncore.
 */
public final class PrisonCore extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("PrisonCore " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PrisonCore disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Hex colors (1.16+) as a preview of what the custom /ranks menu can do
        sender.sendMessage(ChatColor.of("#FFB347") + "" + ChatColor.BOLD + "PrisonCore "
                + ChatColor.of("#7FDBFF") + "v" + getDescription().getVersion()
                + ChatColor.GRAY + " is running.");
        return true;
    }
}
