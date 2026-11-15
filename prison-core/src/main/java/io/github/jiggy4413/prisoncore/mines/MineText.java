package io.github.jiggy4413.prisoncore.mines;

import io.github.jiggy4413.prisoncore.util.Text;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Text about a mine's live state (percent mined, status, timer, progress bar), worded with the
 * settings in mines.yml. Used by the PlaceholderAPI placeholders and the warps menu.
 * Has no PlaceholderAPI classes, so it works without PlaceholderAPI installed.
 */
public final class MineText {

    /** Every field value() understands. */
    public static final String[] FIELDS = {"mined", "remaining", "until", "resetat", "status", "timer", "bar", "name"};

    private MineText() {
    }

    public static String value(MineManager manager, Mine m, String field) {
        ConfigurationSection t = manager.text();
        double mined = m.minedPercent();
        if (field.equals("mined")) {
            return m.minedText();
        }
        if (field.equals("remaining")) {
            return String.valueOf((int) Math.ceil(100 - mined));
        }
        if (field.equals("until")) {
            return String.valueOf((int) Math.max(0, Math.ceil(m.resetAtPercent() - mined)));
        }
        if (field.equals("resetat")) {
            return String.valueOf((int) m.resetAtPercent());
        }
        if (field.equals("status")) {
            if (m.isResetting()) {
                return Text.color(t.getString("placeholders.status-resetting", "&eResetting"));
            }
            if (m.isWarning()) {
                return Text.color(t.getString("placeholders.status-warning", "&6Resetting soon"));
            }
            return Text.color(t.getString("placeholders.status-ready", "&aReady"));
        }
        if (field.equals("timer")) {
            return manager.formatTimer(m);
        }
        if (field.equals("name")) {
            return Text.color(m.display());
        }
        // bar
        int length = Math.max(1, t.getInt("placeholders.bar-length", 10));
        String symbol = t.getString("placeholders.bar-symbol", "■");
        int filled = (int) Math.round(Math.min(1, mined / m.resetAtPercent()) * length);
        StringBuilder sb = new StringBuilder(t.getString("placeholders.bar-filled", "&c"));
        for (int i = 0; i < filled; i++) {
            sb.append(symbol);
        }
        sb.append(t.getString("placeholders.bar-empty", "&a"));
        for (int i = filled; i < length; i++) {
            sb.append(symbol);
        }
        return Text.color(sb.toString());
    }
}
