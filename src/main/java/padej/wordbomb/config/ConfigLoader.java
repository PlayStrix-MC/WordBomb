package padej.wordbomb.config;

import me.padej.jumper.interp.Config;
import padej.wordbomb.core.Rules;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        require(c.eliminationHints() >= 0 && c.eliminationHints() <= 3, "eliminationHints must be 0..3");
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

    /** Ошибка значения настройки: ключ неизвестен или значение не подходит. */
    public static final class BadValue extends IllegalArgumentException {
        public BadValue(String message) {
            super(message);
        }
    }

    /** Ключи числовых и логических настроек, которые можно менять командой и с экрана конфига. */
    public static List<String> editableKeys() {
        List<String> keys = new ArrayList<>();
        for (RecordComponent c : WordBombConfig.class.getRecordComponents()) {
            Class<?> t = c.getType();
            if (t == int.class || t == double.class || t == boolean.class) keys.add(c.getName());
        }
        return keys;
    }

    /**
     * {@code /wb config set <key> <value>}: разобрать значение по типу поля, подставить в текущий конфиг, проверить
     * ({@link Rules} и {@link #validate}) и только потом записать в файл - неверное значение в файл не попадает.
     *
     * @return новое значение (уже типизированное)
     */
    public Object setValue(WordBombConfig current, String key, String rawValue) {
        RecordComponent[] components = WordBombConfig.class.getRecordComponents();
        RecordComponent target = null;
        for (RecordComponent c : components) if (c.getName().equals(key)) target = c;
        if (target == null || !editableKeys().contains(key)) throw new BadValue("unknown key " + key);
        Object value = parse(target.getType(), rawValue);

        Object[] args = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                args[i] = components[i].getName().equals(key) ? value : components[i].getAccessor().invoke(current);
            }
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) types[i] = components[i].getType();
            WordBombConfig candidate = WordBombConfig.class.getDeclaredConstructor(types).newInstance(args);
            candidate.rules();
            validate(candidate);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build config: " + e.getMessage(), e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadValue(e.getMessage());
        }
        try {
            Config.update(configFile(), key, value);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + configFile() + ": " + e.getMessage(), e);
        }
        return value;
    }

    private static Object parse(Class<?> type, String raw) {
        String v = raw == null ? "" : raw.trim().replace(',', '.');
        try {
            if (type == int.class) return Integer.parseInt(v);
            if (type == double.class) return Double.parseDouble(v);
            if (type == boolean.class) {
                String b = v.toLowerCase(Locale.ROOT);
                if (b.equals("true") || b.equals("да") || b.equals("1") || b.equals("on")) return true;
                if (b.equals("false") || b.equals("нет") || b.equals("0") || b.equals("off")) return false;
                throw new BadValue("not a boolean: " + raw);
            }
        } catch (NumberFormatException e) {
            throw new BadValue("not a number: " + raw);
        }
        throw new BadValue("unsupported type " + type.getSimpleName());
    }

    private void update(String key, Place place) {
        try {
            Config.update(configFile(), key, place.toTable());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + configFile() + ": " + e.getMessage(), e);
        }
    }
}
