package io.github.jiggy4413.prisoncore.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Turns config text into colored Minecraft text. Supported formatting, applied in this order:
 * <ol>
 *   <li>PlaceholderAPI placeholders like %vault_eco_balance% (if PlaceholderAPI is installed)</li>
 *   <li>&lt;smallcaps&gt;text&lt;/smallcaps&gt; for a ꜱᴍᴀʟʟ ᴄᴀᴘꜱ "font"</li>
 *   <li>&lt;gradient:#FF0000:#0000FF&gt;text&lt;/gradient&gt; (two or more colors)</li>
 *   <li>&amp;#RRGGBB or &lt;#RRGGBB&gt; hex colors</li>
 *   <li>classic &amp; codes (&amp;a, &amp;l, &amp;o...)</li>
 * </ol>
 */
public final class Text {

    private static final Pattern SMALLCAPS = Pattern.compile("<smallcaps>(.*?)</smallcaps>", Pattern.DOTALL);
    private static final Pattern GRADIENT = Pattern.compile(
            "<gradient:(#[0-9a-fA-F]{6}(?::#[0-9a-fA-F]{6})+)>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})|<#([0-9a-fA-F]{6})>");

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String FORMAT_CODES = "klmnoKLMNO";

    private static Method papiMethod;
    private static boolean papiBroken;

    private Text() {
    }

    /** Colors text without placeholders. */
    public static String color(String text) {
        return color(null, text);
    }

    /** Colors text, filling PlaceholderAPI placeholders for the given player first. */
    public static String color(Player player, String text) {
        if (text == null) {
            return "";
        }
        String out = papi(player, text);
        out = smallCaps(out);
        out = gradients(out);
        out = hex(out);
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    public static List<String> color(Player player, List<String> lines) {
        List<String> out = new ArrayList<String>(lines.size());
        for (String line : lines) {
            out.add(color(player, line));
        }
        return out;
    }

    // ---------------------------------------------------------------- placeholders

    private static String papi(Player player, String text) {
        if (player == null || papiBroken || text.indexOf('%') < 0) {
            return text;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            if (papiMethod == null) {
                papiMethod = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                        .getMethod("setPlaceholders", Player.class, String.class);
            }
            return (String) papiMethod.invoke(null, player, text);
        } catch (Exception e) {
            papiBroken = true;
            Bukkit.getLogger().warning("[PrisonCore] Could not use PlaceholderAPI: " + e);
            return text;
        }
    }

    // ---------------------------------------------------------------- small caps

    private static String smallCaps(String text) {
        Matcher m = SMALLCAPS.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(toSmallCaps(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Converts letters to small caps, leaving color codes and <tags> untouched. */
    public static String toSmallCaps(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                // color code: copy it whole (&#RRGGBB is 8 chars)
                int len = text.charAt(i + 1) == '#' ? Math.min(8, text.length() - i) : 2;
                out.append(text, i, i + len);
                i += len - 1;
            } else if (c == '<') {
                int end = text.indexOf('>', i);
                if (end < 0) {
                    out.append(c);
                } else {
                    out.append(text, i, end + 1);
                    i = end;
                }
            } else {
                int idx = NORMAL.indexOf(Character.toLowerCase(c));
                out.append(idx >= 0 ? SMALL.charAt(idx) : c);
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- gradients

    private static String gradients(String text) {
        Matcher m = GRADIENT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] hexes = m.group(1).split(":");
            int[][] stops = new int[hexes.length][];
            for (int i = 0; i < hexes.length; i++) {
                stops[i] = rgb(hexes[i]);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(gradient(m.group(2), stops)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String gradient(String text, int[][] stops) {
        // count visible characters (skip &l style format codes)
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isFormatCode(text, i)) {
                i++;
            } else {
                visible++;
            }
        }

        StringBuilder out = new StringBuilder();
        StringBuilder formats = new StringBuilder();
        int k = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isFormatCode(text, i)) {
                formats.append('§').append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
                continue;
            }
            double t = visible <= 1 ? 0 : (double) k / (visible - 1);
            out.append(ChatColor.of(colorAt(stops, t))).append(formats).append(text.charAt(i));
            k++;
        }
        return out.toString();
    }

    private static boolean isFormatCode(String text, int i) {
        char c = text.charAt(i);
        return (c == '&' || c == '§') && i + 1 < text.length()
                && FORMAT_CODES.indexOf(text.charAt(i + 1)) >= 0;
    }

    private static String colorAt(int[][] stops, double t) {
        double scaled = t * (stops.length - 1);
        int seg = Math.min((int) Math.floor(scaled), stops.length - 2);
        double local = scaled - seg;
        int[] a = stops[seg];
        int[] b = stops[seg + 1];
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * local);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * local);
        int bl = (int) Math.round(a[2] + (b[2] - a[2]) * local);
        return String.format("#%02X%02X%02X", r, g, bl);
    }

    private static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }

    // ---------------------------------------------------------------- hex

    private static String hex(String text) {
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1) != null ? m.group(1) : m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
