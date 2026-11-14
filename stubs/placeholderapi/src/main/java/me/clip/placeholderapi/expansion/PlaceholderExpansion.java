package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;

/** Compile-only stub of PlaceholderAPI 2.10.x. See stubs/placeholderapi/pom.xml. */
public abstract class PlaceholderExpansion {

    public abstract String getIdentifier();

    public abstract String getAuthor();

    public abstract String getVersion();

    public boolean persist() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean canRegister() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean register() {
        throw new UnsupportedOperationException("stub");
    }

    public String onRequest(OfflinePlayer player, String params) {
        throw new UnsupportedOperationException("stub");
    }
}
