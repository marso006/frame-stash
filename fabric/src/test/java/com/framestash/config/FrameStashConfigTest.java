package com.framestash.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameStashConfigTest {
    @TempDir
    Path dir;

    @Test
    void missingFileIsCreatedWithDefaults() throws Exception {
        Path file = dir.resolve(FrameStashConfig.FILE_NAME);
        FrameStashConfig.LoadResult result = FrameStashConfig.load(file);
        assertTrue(Files.exists(file));
        assertTrue(result.problems().isEmpty());
        assertEquals(16, result.config().radius);

        // The written file reads back to the same values.
        FrameStashConfig.LoadResult again = FrameStashConfig.load(file);
        assertTrue(again.problems().isEmpty(), again.problems().toString());
        assertEquals("minecraft:hopper", again.config().catchAllItem);
    }

    @Test
    void valuesAreReadAndBadOnesFallBack() throws Exception {
        Path file = dir.resolve(FrameStashConfig.FILE_NAME);
        Files.writeString(file, """
                radius = 99          # too big, clamped
                maxContainers = 10
                includeHotbar = true
                catchAllItem = ""    # disables catch-all
                sound = maybe
                bogus = 1
                """, StandardCharsets.UTF_8);
        FrameStashConfig.LoadResult result = FrameStashConfig.load(file);
        FrameStashConfig c = result.config();
        assertEquals(32, c.radius);
        assertEquals(10, c.maxContainers);
        assertTrue(c.includeHotbar);
        assertEquals("", c.catchAllItem);
        assertTrue(c.sound, "invalid value keeps the default");
        assertEquals(3, result.problems().size(), result.problems().toString());
        // Missing keys were added to the file, user values kept.
        String text = Files.readString(file);
        assertTrue(text.contains("sneakGesture = false"));
        assertTrue(text.contains("maxContainers = 10"));
        assertFalse(text.contains("bogus"));
    }
}
