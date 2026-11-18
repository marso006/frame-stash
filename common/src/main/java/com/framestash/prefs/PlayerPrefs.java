package com.framestash.prefs;

import com.framestash.FrameStashMod;
import com.framestash.config.FrameStashConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Per-player menu toggles, saved per world in {@code frame_stash_players.json}. A missing value means
 * "use the server default". Players can only turn sound and particles off, never force them on
 * against the server config; the hotbar choice is free either way.
 */
public final class PlayerPrefs {
    private static final String FILE_NAME = "frame_stash_players.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Stored values; {@code null} means unset. */
    public static final class Prefs {
        public Boolean stashHotbar;
        public Boolean sound;
        public Boolean particles;
    }

    private static Path loadedFile;
    private static Map<String, Prefs> prefs = new HashMap<>();

    private PlayerPrefs() {}

    public static boolean stashHotbar(ServerPlayer player, FrameStashConfig config) {
        Boolean value = get(player).stashHotbar;
        return value != null ? value : config.includeHotbar;
    }

    public static boolean sound(ServerPlayer player, FrameStashConfig config) {
        return config.sound && !Boolean.FALSE.equals(get(player).sound);
    }

    public static boolean particles(ServerPlayer player, FrameStashConfig config) {
        return config.particles && !Boolean.FALSE.equals(get(player).particles);
    }

    public static void toggleStashHotbar(ServerPlayer player, FrameStashConfig config) {
        update(player, p -> p.stashHotbar = !stashHotbar(player, config));
    }

    public static void toggleSound(ServerPlayer player, FrameStashConfig config) {
        update(player, p -> p.sound = !sound(player, config));
    }

    public static void toggleParticles(ServerPlayer player, FrameStashConfig config) {
        update(player, p -> p.particles = !particles(player, config));
    }

    private static synchronized Prefs get(ServerPlayer player) {
        ensureLoaded(player.level().getServer());
        Prefs p = prefs.get(player.getUUID().toString());
        return p != null ? p : new Prefs();
    }

    private static synchronized void update(ServerPlayer player, java.util.function.Consumer<Prefs> change) {
        MinecraftServer server = player.level().getServer();
        ensureLoaded(server);
        Prefs p = prefs.computeIfAbsent(player.getUUID().toString(), k -> new Prefs());
        change.accept(p);
        save();
    }

    private static void ensureLoaded(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME).normalize();
        if (file.equals(loadedFile)) return;
        loadedFile = file;
        prefs = new HashMap<>();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Prefs> read = GSON.fromJson(reader, new TypeToken<Map<String, Prefs>>() {}.getType());
            if (read != null) {
                read.keySet().removeIf(k -> !isUuid(k));
                prefs.putAll(read);
            }
        } catch (IOException | JsonParseException e) {
            FrameStashMod.LOGGER.warn("Could not read {}; player menu settings reset", file, e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(loadedFile.getParent());
            try (Writer writer = Files.newBufferedWriter(loadedFile, StandardCharsets.UTF_8)) {
                GSON.toJson(prefs, writer);
            }
        } catch (IOException e) {
            FrameStashMod.LOGGER.warn("Could not save {}", loadedFile, e);
        }
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
