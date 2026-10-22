package padej.wordbomb.bukkit.arena;

import org.bukkit.entity.Player;
import padej.displayLib.DisplayLib;
import padej.displayLib.script.api.WidgetAPI;
import padej.displayLib.ui.ScreenInstance;
import padej.displayLib.ui.UIManager;
import padej.displayLib.ui.widgets.Widget;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.ConfigLoader;
import padej.wordbomb.config.WordBombConfig;
import padej.wordbomb.storage.VoteRepo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Приватный экран настроек ({@code wordbomb_config}, команда {@code /wb config}): значения настроек - кнопки с
 * {@code id = ключ config.jmc}, справа топ предложений и жалоб. Java пишет значения и списки в виджеты,
 * скрипт экрана ({@code wordbomb_config.jmp}) превращает клики и ввод из чата в команды {@code /wb}.
 */
public final class ConfigScreen {

    public static final String SCREEN_ID = "wordbomb_config";
    public static final int LIST_SIZE = 5;

    private static final String RES_SCREEN = "examples/wordbomb/screens/wordbomb_config.jmc";
    private static final String RES_SCRIPT = "examples/wordbomb/scripts/wordbomb_config.jmp";

    private final WordBombPlugin plugin;

    public ConfigScreen(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    private DisplayLib displayLib() {
        return (DisplayLib) DisplayLib.getInstance();
    }

    /** Создать файлы экрана и скрипта в {@code plugins/WordBomb/}, если их нет, и перечитать экран. */
    public void installResources() {
        boolean created = PackFiles.install(plugin, RES_SCREEN, "screens", SCREEN_ID + ".jmc");
        PackFiles.install(plugin, RES_SCRIPT, "scripts", SCREEN_ID + ".jmp");
        // всегда перечитываем свой файл: если в screens/ есть другой файл с тем же id, иначе победит он
        PackFiles.reloadScreen(plugin, SCREEN_ID);
        if (created) plugin.getLogger().info("Экран " + SCREEN_ID + " создан из ресурсов плагина.");
    }

    /** Открыть экран перед игроком (в 2 блоках по взгляду, как {@code /displaylib open}) и заполнить. */
    public boolean open(Player player) {
        if (!displayLib().getScreenRegistry().hasScreen(SCREEN_ID)) installResources();
        if (!UIManager.getInstance().openScreen(player, SCREEN_ID)) return false;
        refresh(player);
        return true;
    }

    /** Экран настроек этого игрока или {@code null}, если открыт другой экран / ничего. */
    private ScreenInstance screenOf(Player player) {
        ScreenInstance s = UIManager.getInstance().getActiveScreen(player);
        return s != null && SCREEN_ID.equals(s.getScreenId()) ? s : null;
    }

    /** Перерисовать, если у игрока открыт экран настроек (после {@code /wb config set}, {@code accept}, {@code block}). */
    public void refreshIfOpen(Player player) {
        if (player != null && screenOf(player) != null) refresh(player);
    }

    private void refresh(Player player) {
        ScreenInstance screen = screenOf(player);
        if (screen == null) return;
        renderSettings(screen, plugin.config());
        UUID id = player.getUniqueId();
        plugin.async().supply(() -> new Lists(plugin.suggestions().top(LIST_SIZE), plugin.reports().top(LIST_SIZE)), lists -> {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            ScreenInstance s = p != null ? screenOf(p) : null;
            if (s == null) return;
            renderList(s, "offer_", lists.offers());
            renderList(s, "report_", lists.reports());
        });
    }

    private record Lists(List<VoteRepo.Entry> offers, List<VoteRepo.Entry> reports) {}

    private void renderSettings(ScreenInstance screen, WordBombConfig config) {
        for (RecordComponent c : WordBombConfig.class.getRecordComponents()) {
            Widget widget = screen.getWidget(c.getName());
            if (widget == null) continue;
            try {
                Object value = c.getAccessor().invoke(config);
                setText(widget, c.getName(), format(value));
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().fine("config widget " + c.getName() + ": " + e.getMessage());
            }
        }
    }

    private void renderList(ScreenInstance screen, String prefix, List<VoteRepo.Entry> entries) {
        for (int i = 1; i <= LIST_SIZE; i++) {
            Widget widget = screen.getWidget(prefix + i);
            if (widget == null) continue;
            WidgetAPI api = new WidgetAPI(prefix + i, widget);
            if (i <= entries.size()) {
                VoteRepo.Entry e = entries.get(i - 1);
                safe(() -> {
                    api.text(e.word() + " ·" + e.votes());
                    api.tooltip(e.word());
                });
            } else {
                safe(() -> {
                    api.text("-");
                    api.tooltip("");
                });
            }
        }
    }

    private void setText(Widget widget, String id, String text) {
        safe(() -> new WidgetAPI(id, widget).text(text));
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            plugin.getLogger().fine("config screen: " + e.getMessage());
        }
    }

    private static String format(Object value) {
        if (value instanceof Double d) return String.format(Locale.ROOT, "%s", d);
        if (value instanceof Boolean b) return b ? "да" : "нет";
        return String.valueOf(value);
    }

    /** Ключи, доступные на экране и в {@code /wb config set}. */
    public static List<String> keys() {
        return ConfigLoader.editableKeys();
    }
}
