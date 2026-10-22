package padej.wordbomb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    @Test
    void createsFilesFromDefaultsAndReadsThemBack() {
        ConfigLoader loader = new ConfigLoader(dir.resolve("WordBomb"));
        ConfigLoader.Loaded loaded = loader.load();
        assertEquals(WordBombConfig.DEFAULTS, loaded.config());
        assertEquals(Messages.DEFAULTS, loaded.messages());
        assertEquals(20, loaded.rules().countdownSeconds());
        assertEquals(5, loaded.rules().turnSeconds(15));
        assertTrue(Files.exists(loader.configFile()));
        assertTrue(Files.exists(loader.messagesFile()));
    }

    @Test
    void serverEditsAreKeptAndMissingKeysAppended() throws IOException {
        ConfigLoader loader = new ConfigLoader(dir);
        loader.load();
        String text = Files.readString(loader.configFile(), StandardCharsets.UTF_8);
        text = text.replace("int zoneRadius = 50;", "int zoneRadius = 70; // wider");
        Files.writeString(loader.configFile(), text, StandardCharsets.UTF_8);
        Files.writeString(loader.messagesFile(), "String joined = \"&aПривет!\";\n", StandardCharsets.UTF_8);

        ConfigLoader.Loaded loaded = loader.load();
        assertEquals(70, loaded.config().zoneRadius());
        assertEquals("&aПривет!", loaded.messages().joined());
        assertEquals(Messages.DEFAULTS.winner(), loaded.messages().winner(), "missing keys come from defaults");
        assertTrue(Files.readString(loader.messagesFile(), StandardCharsets.UTF_8).contains("String winner ="), "and are appended to the file");
        assertTrue(Files.readString(loader.configFile(), StandardCharsets.UTF_8).contains("// wider"));
    }

    @Test
    void savePlaceUpdatesOnlyThatKey() throws IOException {
        ConfigLoader loader = new ConfigLoader(dir);
        loader.load();
        loader.saveArena(new Place("arena_world", 1.5, 64.0, -2.5, 90f, 0f));
        loader.saveScreen(new Place("arena_world", 3, 70, 4, -180f, 10f));
        ConfigLoader.Loaded loaded = loader.load();
        assertEquals(new Place("arena_world", 1.5, 64.0, -2.5, 90f, 0f), loaded.config().arena());
        assertEquals(new Place("arena_world", 3, 70, 4, -180f, 10f), loaded.config().screen());
        assertEquals(WordBombConfig.DEFAULTS.zoneRadius(), loaded.config().zoneRadius());
        assertTrue(Files.readString(loader.configFile(), StandardCharsets.UTF_8).contains("// WordBomb settings"));
    }

    @Test
    void invalidValuesFailWithFileName() throws IOException {
        ConfigLoader loader = new ConfigLoader(dir);
        loader.load();
        String text = Files.readString(loader.configFile(), StandardCharsets.UTF_8).replace("int minTurnSeconds = 5;", "int minTurnSeconds = 0;");
        Files.writeString(loader.configFile(), text, StandardCharsets.UTF_8);
        IllegalStateException e = assertThrows(IllegalStateException.class, loader::load);
        assertTrue(e.getMessage().contains("config.jmc"), e.getMessage());

        text = Files.readString(loader.configFile(), StandardCharsets.UTF_8).replace("int minTurnSeconds = 0;", "int minTurnSeconds = \"five\";");
        Files.writeString(loader.configFile(), text, StandardCharsets.UTF_8);
        e = assertThrows(IllegalStateException.class, loader::load);
        assertTrue(e.getMessage().contains("config.jmc"), e.getMessage());
    }

    @Test
    void emptyWorldIsRejected() throws IOException {
        ConfigLoader loader = new ConfigLoader(dir);
        loader.load();
        loader.saveArena(new Place("", 0, 0, 0, 0, 0));
        IllegalStateException e = assertThrows(IllegalStateException.class, loader::load);
        assertTrue(e.getMessage().contains("arena.world"));
    }

    @Test
    void setValueValidatesBeforeWriting() throws IOException {
        ConfigLoader loader = new ConfigLoader(dir);
        WordBombConfig cfg = loader.load().config();
        assertEquals(15, loader.setValue(cfg, "countdownSeconds", "15"));
        assertEquals(0.5, loader.setValue(cfg, "twoLetterChance", "0,5"));
        assertEquals(false, loader.setValue(cfg, "freezePlayers", "нет"));
        WordBombConfig reloaded = loader.load().config();
        assertEquals(15, reloaded.countdownSeconds());
        assertEquals(0.5, reloaded.twoLetterChance());
        assertEquals(false, reloaded.freezePlayers());

        assertThrows(ConfigLoader.BadValue.class, () -> loader.setValue(reloaded, "minTurnSeconds", "0"));
        assertThrows(ConfigLoader.BadValue.class, () -> loader.setValue(reloaded, "minTurnSeconds", "abc"));
        assertThrows(ConfigLoader.BadValue.class, () -> loader.setValue(reloaded, "arena", "1"));
        assertThrows(ConfigLoader.BadValue.class, () -> loader.setValue(reloaded, "nope", "1"));
        assertEquals(5, loader.load().config().minTurnSeconds(), "bad value must not reach the file");
        assertTrue(ConfigLoader.editableKeys().contains("countdownSeconds"));
        assertFalse(ConfigLoader.editableKeys().contains("legacyDataFolder"));
    }
}
