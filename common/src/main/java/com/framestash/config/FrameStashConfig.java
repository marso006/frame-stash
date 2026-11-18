package com.framestash.config;

import com.framestash.FrameStashMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Settings from {@code frame_stash.toml}. The file is flat {@code key = value} TOML, so a tiny
 * built-in reader is used instead of shipping a TOML library on Fabric.
 */
public final class FrameStashConfig {
    public static final String FILE_NAME = "frame_stash.toml";

    public int radius = 16;
    public int maxContainers = 64;
    public boolean includeHotbar = false;
    public boolean restockEnabled = true;
    public boolean overflowEnabled = true;
    public String catchAllItem = "minecraft:hopper";
    public int cooldownTicks = 20;
    public boolean particles = true;
    public boolean sound = true;
    public boolean actionBar = true;
    public int commandPermissionLevel = 0;
    public boolean skipBlockedChests = true;
    public boolean protectionCheck = true;
    public boolean sneakGesture = false;

    private Optional<Item> resolvedCatchAll;

    /** The item that marks a catch-all container, or empty when the feature is disabled. */
    public Optional<Item> catchAll() {
        if (resolvedCatchAll == null) {
            resolvedCatchAll = Optional.empty();
            if (!catchAllItem.isBlank()) {
                ResourceLocation id = ResourceLocation.tryParse(catchAllItem.trim());
                Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
                if (item.isEmpty() || item.get() == Items.AIR) {
                    FrameStashMod.LOGGER.warn("catchAllItem '{}' is not a known item; catch-all is disabled", catchAllItem);
                } else {
                    resolvedCatchAll = item;
                }
            }
        }
        return resolvedCatchAll;
    }

    /** Result of reading the file: the config plus any problems found, which were replaced by defaults. */
    public record LoadResult(FrameStashConfig config, List<String> problems) {}

    private static final List<String> KEYS = List.of(
            "radius", "maxContainers", "includeHotbar", "restockEnabled", "overflowEnabled", "catchAllItem",
            "cooldownTicks", "particles", "sound", "actionBar", "commandPermissionLevel", "skipBlockedChests",
            "protectionCheck", "sneakGesture");

    /** Reads the file, creating it with defaults if it is missing. Bad values fall back to defaults. */
    public static LoadResult load(Path file) throws IOException {
        FrameStashConfig config = new FrameStashConfig();
        List<String> problems = new ArrayList<>();
        if (!Files.exists(file)) {
            config.save(file);
            return new LoadResult(config, problems);
        }
        Map<String, String> raw = parse(Files.readAllLines(file, StandardCharsets.UTF_8), problems);
        boolean missingKeys = false;
        for (String key : KEYS) {
            String value = raw.remove(key);
            if (value == null) {
                missingKeys = true;
                continue;
            }
            try {
                config.set(key, value);
            } catch (IllegalArgumentException e) {
                problems.add(key + ": " + e.getMessage() + "; using default");
            }
        }
        raw.keySet().forEach(k -> problems.add("unknown key '" + k + "' ignored"));
        config.clamp(problems);
        if (missingKeys) {
            config.save(file); // add new keys with their comments, keep the user's values
        }
        return new LoadResult(config, problems);
    }

    private void set(String key, String v) {
        switch (key) {
            case "radius" -> radius = parseInt(v);
            case "maxContainers" -> maxContainers = parseInt(v);
            case "includeHotbar" -> includeHotbar = parseBool(v);
            case "restockEnabled" -> restockEnabled = parseBool(v);
            case "overflowEnabled" -> overflowEnabled = parseBool(v);
            case "catchAllItem" -> catchAllItem = parseString(v);
            case "cooldownTicks" -> cooldownTicks = parseInt(v);
            case "particles" -> particles = parseBool(v);
            case "sound" -> sound = parseBool(v);
            case "actionBar" -> actionBar = parseBool(v);
            case "commandPermissionLevel" -> commandPermissionLevel = parseInt(v);
            case "skipBlockedChests" -> skipBlockedChests = parseBool(v);
            case "protectionCheck" -> protectionCheck = parseBool(v);
            case "sneakGesture" -> sneakGesture = parseBool(v);
            default -> throw new IllegalArgumentException("unknown key");
        }
    }

    private void clamp(List<String> problems) {
        radius = clamp("radius", radius, 4, 32, problems);
        maxContainers = clamp("maxContainers", maxContainers, 1, 512, problems);
        cooldownTicks = clamp("cooldownTicks", cooldownTicks, 0, 1200, problems);
        commandPermissionLevel = clamp("commandPermissionLevel", commandPermissionLevel, 0, 4, problems);
    }

    private static int clamp(String key, int value, int min, int max, List<String> problems) {
        if (value < min || value > max) {
            int fixed = Math.max(min, Math.min(max, value));
            problems.add(key + " must be " + min + " to " + max + "; using " + fixed);
            return fixed;
        }
        return value;
    }

    public void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        String quotedCatchAll = "\"" + catchAllItem.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        List<String> lines = List.of(
                "# Frame Stash configuration. Run /framestash reload after editing.",
                "",
                "# Search distance in blocks around the player (4 to 32).",
                "radius = " + radius,
                "",
                "# Most containers touched by one action (1 to 512).",
                "maxContainers = " + maxContainers,
                "",
                "# Whether /stash may move items out of the hotbar.",
                "includeHotbar = " + includeHotbar,
                "",
                "# Turns /restock on or off.",
                "restockEnabled = " + restockEnabled,
                "",
                "# Glow item frames mark overflow containers, used once every normal-frame container is full.",
                "overflowEnabled = " + overflowEnabled,
                "",
                "# Item that, placed in a frame, marks a catch-all container for leftovers. Empty (\"\") disables it.",
                "catchAllItem = " + quotedCatchAll,
                "",
                "# Minimum ticks between two actions by the same player (20 ticks = 1 second, 0 to 1200).",
                "cooldownTicks = " + cooldownTicks,
                "",
                "# Feedback toggles.",
                "particles = " + particles,
                "sound = " + sound,
                "actionBar = " + actionBar,
                "",
                "# Permission level needed for /stash, /restock and /framestash info (0 = everyone, 2 = ops).",
                "commandPermissionLevel = " + commandPermissionLevel,
                "",
                "# Skip chests vanilla would refuse to open, for example with a solid block on top.",
                "skipBlockedChests = " + skipBlockedChests,
                "",
                "# Ask claim and protection mods whether the player may use a container before touching it.",
                "protectionCheck = " + protectionCheck,
                "",
                "# Sneak and right-click a labelled container with an empty hand to stash. Off by default",
                "# because it replaces opening the container while sneaking.",
                "sneakGesture = " + sneakGesture,
                "");
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    // --- minimal flat TOML reader -------------------------------------------------------------

    static Map<String, String> parse(List<String> lines, List<String> problems) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0 || line.startsWith("[")) {
                problems.add("line " + (i + 1) + " is not 'key = value'; ignored");
                continue;
            }
            out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inString) {
                i++;
            } else if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v.replace("_", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + v + "' is not a whole number");
        }
    }

    private static boolean parseBool(String v) {
        return switch (v) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("'" + v + "' is not true or false");
        };
    }

    private static String parseString(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            return v.substring(1, v.length() - 1);
        }
        throw new IllegalArgumentException(v + " must be in quotes");
    }
}
