package io.github.jiggy4413.prisoncore.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads one of PrisonCore's config files, creating it from the built-in copy the first time.
 * Mistakes (tabs, bad indentation) are described in plain words instead of a stack trace.
 */
public final class YamlFile {

    /** The loaded file (missing keys fall back to the built-in copy), or null if it had a mistake. */
    public final YamlConfiguration config;
    /** What's wrong with the file, or null when it loaded fine. */
    public final String problem;

    private YamlFile(YamlConfiguration config, String problem) {
        this.config = config;
        this.problem = problem;
    }

    public static YamlFile load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration builtIn = new YamlConfiguration();
        InputStream in = plugin.getResource(name);
        if (in != null) {
            builtIn = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        String problem;
        YamlConfiguration config = new YamlConfiguration();
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            problem = findTab(text);
            if (problem == null) {
                config.loadFromString(text);
            }
        } catch (InvalidConfigurationException e) {
            problem = describeYamlError(e.getMessage());
        } catch (IOException e) {
            problem = "couldn't read the file: " + e.getMessage();
        }
        if (problem != null) {
            return new YamlFile(null, problem);
        }
        config.setDefaults(builtIn);
        return new YamlFile(config, null);
    }

    /** The built-in copy alone, for a first start where the file on disk is broken. */
    public static YamlConfiguration builtIn(JavaPlugin plugin, String name) {
        InputStream in = plugin.getResource(name);
        return in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** YAML doesn't allow tab characters for indentation, a very common mistake. */
    public static String findTab(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            String beforeComment = hash >= 0 ? line.substring(0, hash) : line;
            if (beforeComment.indexOf('\t') >= 0) {
                return "line " + (i + 1) + " has a Tab character. Use spaces to indent instead.";
            }
        }
        return null;
    }

    /** Keeps the useful part of SnakeYAML's long error, e.g. "line 12, column 5". */
    public static String describeYamlError(String message) {
        if (message == null) {
            return "invalid YAML";
        }
        Matcher m = Pattern.compile("line (\\d+), column (\\d+)").matcher(message);
        String where = m.find() ? " near line " + m.group(1) + ", column " + m.group(2) : "";
        String first = message.trim().split("\n")[0].trim();
        return "invalid YAML" + where + " (" + first + "). Check indentation and quotes.";
    }
}
