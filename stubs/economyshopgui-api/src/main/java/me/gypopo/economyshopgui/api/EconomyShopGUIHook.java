package me.gypopo.economyshopgui.api;

import java.util.Optional;
import me.gypopo.economyshopgui.api.objects.SellPrice;
import me.gypopo.economyshopgui.providers.EconomyProvider;
import me.gypopo.economyshopgui.util.EcoType;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

/** Compile-only stub. The real class comes from EconomyShopGUI at runtime. */
public class EconomyShopGUIHook {
    public static Optional<SellPrice> getSellPrice(OfflinePlayer player, ItemStack item) {
        throw new UnsupportedOperationException("stub");
    }

    public static EconomyProvider getEcon(EcoType ecoType) {
        throw new UnsupportedOperationException("stub");
    }
}
