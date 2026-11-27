package io.github.jiggy4413.prisoncore.sell;

import io.github.jiggy4413.prisoncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /prisoncore sellall <player>: sells the player's whole inventory at the shop prices.
 * Made for sell NPCs: Citizens runs it from the console, so players never need /sellall themselves.
 */
public final class SellAllCommand {

    private SellAllCommand() {
    }

    public static void run(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&cUsage: /prisoncore sellall <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.color("&c" + args[1] + " isn't online."));
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI")
                && !Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI-Premium")) {
            sender.sendMessage(Text.color("&cEconomyShopGUI isn't running, so nothing can be sold."));
            return;
        }
        try {
            ShopSellAll.sell(target);
        } catch (Throwable t) {
            sender.sendMessage(Text.color("&cSelling failed: " + t));
            Bukkit.getLogger().severe("[PrisonCore] sellall for " + target.getName() + " failed: " + t);
            t.printStackTrace();
        }
    }
}
