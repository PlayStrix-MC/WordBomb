package padej.wordbomb.bukkit.arena;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import padej.displayLib.DisplayLib;
import padej.displayLib.script.api.StorageAPI;
import padej.displayLib.ui.ScreenInstance;
import padej.displayLib.ui.UIManager;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.storage.VoteRepo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Приватный экран модерации словаря ({@code wordbomb_mod}, команда {@code /wb mod}): вкладки «предложения» /
 * «жалобы», страницы по {@value #ROWS} строк. Данные страницы Java кладёт в {@code storage} игрока DisplayLib
 * ({@code wordbomb_mod.*}) и открывает экран заново: {@code onOpen} скрипта читает их, а morph перетекает из старых
 * слов в новые. Клики скрипт превращает в команды {@code /wb mod tab|page} и {@code /wb accept|block}.
 */
public final class ModerationScreen {

    public static final String SCREEN_ID = "wordbomb_mod";
    public static final int ROWS = 8;
    /** Сколько слов берём из базы для листания. */
    private static final int FETCH_LIMIT = 400;

    private static final String RES_SCREEN = "examples/wordbomb/screens/wordbomb_mod.jmc";
    private static final String RES_SCRIPT = "examples/wordbomb/scripts/wordbomb_mod.jmp";
    private static final String KEY = "wordbomb_mod.";

    /** Вкладка. */
    public enum Kind {
        OFFERS, REPORTS;

        public static Kind parse(String s) {
            if (s == null) return null;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "offers", "offer", "предложения" -> OFFERS;
                case "reports", "report", "жалобы" -> REPORTS;
                default -> null;
            };
        }

        String key() {
            return this == OFFERS ? "offers" : "reports";
        }
    }

    private final WordBombPlugin plugin;

    public ModerationScreen(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    private DisplayLib displayLib() {
        return (DisplayLib) DisplayLib.getInstance();
    }

    // ------------------------------------------------------------------ ресурсы

    public void installResources() {
        boolean created = PackFiles.install(plugin, RES_SCREEN, "screens", SCREEN_ID + ".jmc");
        PackFiles.install(plugin, RES_SCRIPT, "scripts", SCREEN_ID + ".jmp");
        PackFiles.reloadScreen(plugin, SCREEN_ID);
        if (created) plugin.getLogger().info("Экран " + SCREEN_ID + " создан из ресурсов плагина.");
    }

    // ------------------------------------------------------------------ открытие и листание

    /** {@code /wb mod}: открыть на сохранённой вкладке и странице. */
    public void open(Player player) {
        StorageAPI st = storage(player);
        Kind kind = Kind.parse(String.valueOf(st.get(KEY + "kind", "offers")));
        show(player, kind != null ? kind : Kind.OFFERS, asInt(st.get(KEY + "page", 0)));
    }

    /** {@code /wb mod tab <offers|reports>}: сменить вкладку, страница - первая. */
    public void tab(Player player, Kind kind) {
        show(player, kind, 0);
    }

    /** {@code /wb mod page <next|prev|N>}. */
    public void page(Player player, String arg) {
        StorageAPI st = storage(player);
        Kind kind = Kind.parse(String.valueOf(st.get(KEY + "kind", "offers")));
        int page = asInt(st.get(KEY + "page", 0));
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
        show(player, kind != null ? kind : Kind.OFFERS, page);
    }

    /** Перерисовать, если у игрока открыт этот экран (после {@code /wb accept|block}). */
    public void refreshIfOpen(Player player) {
        if (player == null || !isOpen(player)) return;
        open(player);
    }

    private boolean isOpen(Player player) {
        ScreenInstance s = UIManager.getInstance().getActiveScreen(player);
        return s != null && SCREEN_ID.equals(s.getScreenId());
    }

    /**
     * Загрузить слова (в фоне), записать страницу в storage игрока и открыть экран: если он уже открыт -
     * {@code switchScreen} на тот же id даёт morph, иначе - обычное открытие перед игроком.
     */
    private void show(Player player, Kind kind, int requestedPage) {
        UUID id = player.getUniqueId();
        VoteRepo repo = kind == Kind.OFFERS ? plugin.suggestions() : plugin.reports();
        plugin.async().supply(() -> repo.top(FETCH_LIMIT), entries -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            int pages = Math.max(1, (entries.size() + ROWS - 1) / ROWS);
            int page = Math.max(0, Math.min(requestedPage, pages - 1));
            writePage(p, kind, page, pages, entries);
            if (!displayLib().getScreenRegistry().hasScreen(SCREEN_ID)) installResources();
            boolean ok = isOpen(p)
                    ? UIManager.getInstance().switchScreen(p, SCREEN_ID)
                    : UIManager.getInstance().openScreen(p, SCREEN_ID);
            if (!ok) plugin.chat().send(p, plugin.chat().messages().modScreenFailed());
        });
    }

    private void writePage(Player player, Kind kind, int page, int pages, List<VoteRepo.Entry> entries) {
        StorageAPI st = storage(player);
        st.set(KEY + "kind", kind.key());
        st.set(KEY + "page", page);
        st.set(KEY + "pages", pages);
        st.set(KEY + "total", entries.size());
        for (int i = 1; i <= ROWS; i++) {
            int index = page * ROWS + i - 1;
            if (index < entries.size()) {
                VoteRepo.Entry e = entries.get(index);
                st.set(KEY + "row." + i + ".word", e.word());
                st.set(KEY + "row." + i + ".votes", e.votes());
            } else {
                st.remove(KEY + "row." + i + ".word");
                st.remove(KEY + "row." + i + ".votes");
            }
        }
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
}
