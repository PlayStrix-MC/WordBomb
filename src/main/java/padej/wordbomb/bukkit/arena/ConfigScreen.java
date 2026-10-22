package padej.wordbomb.bukkit.arena;

import org.bukkit.entity.Player;
import padej.displayLib.script.api.StorageAPI;
import padej.displayLib.ui.ScreenInstance;
import padej.displayLib.ui.UIManager;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.ConfigLoader;
import padej.wordbomb.config.WordBombConfig;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Приватный экран настроек ({@code wordbomb_config}, команда {@code /wb config}): страницы по {@value #ROWS} строк
 * «подпись - значение». Данные страницы Java кладёт в {@code storage} игрока DisplayLib ({@code wordbomb_config.*})
 * и открывает экран заново: {@code onOpen} скрипта читает их, morph перетекает между страницами. Клик по значению
 * скрипт превращает в {@code player.input(...)} → {@code /wb config set <ключ> <значение>}, стрелки - в
 * {@code /wb config page next|prev}. Подписи ключей - {@code messages.jmc → configLabels}.
 */
public final class ConfigScreen {

    public static final String SCREEN_ID = "wordbomb_config";
    public static final int ROWS = 8;

    private static final String RES_SCREEN = "examples/wordbomb/screens/wordbomb_config.jmc";
    private static final String RES_SCRIPT = "examples/wordbomb/scripts/wordbomb_config.jmp";
    private static final String KEY = "wordbomb_config.";

    private final WordBombPlugin plugin;

    public ConfigScreen(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    /** Создать недостающие файлы в {@code plugins/WordBomb/} (набор содержимого DisplayLib) и перечитать экран. */
    public void installResources() {
        boolean created = PackFiles.install(plugin, RES_SCREEN, "screens", SCREEN_ID + ".jmc");
        PackFiles.install(plugin, RES_SCRIPT, "scripts", SCREEN_ID + ".jmp");
        PackFiles.reloadScreen(plugin, SCREEN_ID);
        if (created) plugin.getLogger().info("Экран " + SCREEN_ID + " создан из ресурсов плагина.");
    }

    // ------------------------------------------------------------------ открытие и листание

    /** {@code /wb config}: открыть на сохранённой странице. */
    public boolean open(Player player) {
        return show(player, asInt(storage(player).get(KEY + "page", 0)));
    }

    /** {@code /wb config page <next|prev|N>}. */
    public void page(Player player, String arg) {
        int page = asInt(storage(player).get(KEY + "page", 0));
        if (arg == null) return;
        switch (arg.toLowerCase(Locale.ROOT)) {
            case "next" -> page++;
            case "prev" -> page--;
            default -> {
                try {
                    page = Integer.parseInt(arg) - 1;
                } catch (NumberFormatException e) {
                    return;
                }
            }
        }
        show(player, page);
    }

    /** Перерисовать, если у игрока открыт экран настроек (после {@code /wb config set}). */
    public void refreshIfOpen(Player player) {
        if (player != null && isOpen(player)) open(player);
    }

    private boolean isOpen(Player player) {
        ScreenInstance s = UIManager.getInstance().getActiveScreen(player);
        return s != null && SCREEN_ID.equals(s.getScreenId());
    }

    /**
     * Записать страницу в storage игрока и открыть экран: если он уже открыт - {@code switchScreen} на тот же id
     * даёт morph, иначе обычное открытие перед игроком.
     */
    private boolean show(Player player, int requestedPage) {
        List<String> keys = keys();
        int pages = Math.max(1, (keys.size() + ROWS - 1) / ROWS);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        writePage(player, page, pages, keys, plugin.config());
        if (!displayLibHasScreen()) installResources();
        return isOpen(player)
                ? UIManager.getInstance().switchScreen(player, SCREEN_ID)
                : UIManager.getInstance().openScreen(player, SCREEN_ID);
    }

    private boolean displayLibHasScreen() {
        return PackFiles.displayLib().getScreenRegistry().hasScreen(SCREEN_ID);
    }

    private void writePage(Player player, int page, int pages, List<String> keys, WordBombConfig config) {
        Map<String, String> labels = plugin.chat().messages().configLabels();
        Map<String, Object> values = values(config);
        StorageAPI st = storage(player);
        st.set(KEY + "page", page);
        st.set(KEY + "pages", pages);
        for (int i = 1; i <= ROWS; i++) {
            int index = page * ROWS + i - 1;
            if (index < keys.size()) {
                String key = keys.get(index);
                st.set(KEY + "row." + i + ".key", key);
                st.set(KEY + "row." + i + ".label", labels != null && labels.containsKey(key) ? labels.get(key) : key);
                st.set(KEY + "row." + i + ".value", format(values.get(key)));
            } else {
                st.remove(KEY + "row." + i + ".key");
                st.remove(KEY + "row." + i + ".label");
                st.remove(KEY + "row." + i + ".value");
            }
        }
    }

    /** Текущие значения редактируемых настроек по ключам. */
    private static Map<String, Object> values(WordBombConfig config) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (RecordComponent c : WordBombConfig.class.getRecordComponents()) {
            try {
                out.put(c.getName(), c.getAccessor().invoke(config));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return out;
    }

    private static String format(Object value) {
        if (value instanceof Double d) return String.format(Locale.ROOT, "%s", d);
        if (value instanceof Boolean b) return b ? "да" : "нет";
        return String.valueOf(value);
    }

    private static StorageAPI storage(Player player) {
        return StorageAPI.forPlayer(player.getUniqueId());
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Ключи в порядке экрана: сначала как в {@code configLabels} (порядок задаёт сервер), затем остальные редактируемые.
     */
    public List<String> keys() {
        List<String> editable = ConfigLoader.editableKeys();
        List<String> out = new ArrayList<>();
        Map<String, String> labels = plugin.chat().messages().configLabels();
        if (labels != null) {
            for (String k : labels.keySet()) if (editable.contains(k)) out.add(k);
        }
        for (String k : editable) if (!out.contains(k)) out.add(k);
        return out;
    }

    /** Ключи, доступные в {@code /wb config set}. */
    public static List<String> editableKeys() {
        return ConfigLoader.editableKeys();
    }
}
