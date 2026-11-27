package io.github.jiggy4413.prisoncore.sell;

import io.github.jiggy4413.prisoncore.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.objects.SellPrice;
import me.gypopo.economyshopgui.providers.EconomyProvider;
import me.gypopo.economyshopgui.util.EcoType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Sells everything sellable in a player's inventory at the EconomyShopGUI prices.
 * Only loaded when EconomyShopGUI is on the server (see {@link SellAllCommand}).
 * Armor and the offhand are never sold, only the 36 main inventory slots.
 */
final class ShopSellAll {

    private ShopSellAll() {
    }

    static void sell(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        Map<EcoType, Double> totals = new LinkedHashMap<EcoType, Double>();
        int sold = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Optional<SellPrice> price = EconomyShopGUIHook.getSellPrice(player, item);
            if (!price.isPresent()) {
                continue; // not in the shop, not sellable, or a sell limit was reached
            }
            SellPrice p = price.get();
            for (Map.Entry<EcoType, Double> e : p.getPrices().entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    totals.merge(e.getKey(), e.getValue(), Double::sum);
                }
            }
            sold += p.getAmount();
            inv.setItem(slot, null);
            p.updateLimits(); // keeps dynamic pricing / sell stock correct
        }

        if (sold == 0) {
            player.sendMessage(Text.color("&cYou have nothing to sell."));
            return;
        }

        StringBuilder money = new StringBuilder();
        for (Map.Entry<EcoType, Double> e : totals.entrySet()) {
            EconomyProvider econ = EconomyShopGUIHook.getEcon(e.getKey());
            if (econ == null) {
                continue;
            }
            econ.depositBalance(player, e.getValue());
            if (money.length() > 0) {
                money.append("&a, &f");
            }
            money.append(econ.formatPrice(e.getValue()));
        }
        player.sendMessage(Text.color("&aSold &f" + sold + " &aitems for &f" + money + "&a."));
    }
}
