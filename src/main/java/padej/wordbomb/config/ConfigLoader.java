package padej.wordbomb.config;

import me.padej.jumper.interp.Config;
import padej.wordbomb.core.Rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Чтение и запись {@code config.jmc} и {@code messages.jmc} через Jumper: файл создаётся из значений по умолчанию,
 * недостающие переменные дописываются, правки сервера сохраняются. Ошибки значений - исключение с путём файла.
 */
public final class ConfigLoader {

    public static final String CONFIG_FILE = "config.jmc";
    public static final String MESSAGES_FILE = "messages.jmc";

    /** Загруженные конфиг, тексты и правила ядра. */
    public record Loaded(WordBombConfig config, Messages messages, Rules rules) {}

    private final Path dataFolder;

    public ConfigLoader(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public Path configFile() {
        return dataFolder.resolve(CONFIG_FILE);
    }

    public Path messagesFile() {
        return dataFolder.resolve(MESSAGES_FILE);
    }

    public Loaded load() {
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + dataFolder + ": " + e.getMessage(), e);
        }
        WordBombConfig config = read(configFile(), WordBombConfig.DEFAULTS);
        Messages messages = read(messagesFile(), Messages.DEFAULTS);
        Rules rules;
        try {
            rules = config.rules();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(configFile() + ": " + e.getMessage(), e);
        }
        validate(config);
        return new Loaded(config, messages, rules);
    }

    private static <T> T read(Path file, T defaults) {
        try {
            return Config.loadOrCreate(file, defaults);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(file + ": " + e.getMessage(), e);
        }
    }

    private void validate(WordBombConfig c) {
        require(c.arena() != null && c.arena().world() != null && !c.arena().world().isBlank(), "arena.world is empty");
        require(c.screen() != null && c.screen().world() != null && !c.screen().world().isBlank(), "screen.world is empty");
        require(c.zoneRadius() > 0, "zoneRadius must be > 0");
        require(c.circleRadius() > 0, "circleRadius must be > 0");
        require(c.spectatorLeash() > 0, "spectatorLeash must be > 0");
        require(c.wordScale() > 0, "wordScale must be > 0");
    }

    private void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(configFile() + ": " + message);
    }

    /** {@code /wb config setCenter}: записать только {@code arena}, не трогая остальное. */
    public void saveArena(Place place) {
        update("arena", place);
    }

    /** {@code /wb config setHologram}: записать только {@code screen}. */
    public void saveScreen(Place place) {
        update("screen", place);
    }

    private void update(String key, Place place) {
        try {
            Config.update(configFile(), key, place.toTable());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + configFile() + ": " + e.getMessage(), e);
        }
    }
}
