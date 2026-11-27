package me.gypopo.economyshopgui.providers;

import org.bukkit.OfflinePlayer;

/** Compile-only stub. */
public interface EconomyProvider {
    void depositBalance(OfflinePlayer p, double amount);

    String formatPrice(double amount);
}
