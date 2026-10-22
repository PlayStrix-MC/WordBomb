package padej.wordbomb.bukkit.arena;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import padej.displayLib.DisplayLib;
import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.particles.Particles;
import padej.wordbomb.bukkit.effects.EffectPlayer;
import padej.displayLib.script.ScriptContext;
import padej.displayLib.script.api.WidgetAPI;
import padej.displayLib.ui.GlobalScreenInstance;
import padej.displayLib.ui.UIManager;
import padej.displayLib.ui.widgets.Widget;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.Messages;
import padej.wordbomb.config.Text;
import padej.wordbomb.core.GameSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Публичный экран арены (DisplayLib, {@code wordbomb_arena}): один на всех, стоит у голограммы, persistent.
 * <ul>
 *   <li>Файлы экрана, скрипта и эффекта черепа создаются из ресурсов плагина, только если их ещё нет
 *       (сервер правит их как хочет).</li>
 *   <li>Клики по кнопкам {@code join}/{@code leave}/{@code start} обрабатывает Java через
 *       {@link DisplayClickEvent} (событие отменяется, чтобы скрипт не выполнил действие второй раз).</li>
 *   <li>Состояние отдаётся скрипту функцией {@code render(state, players, seconds, round)}; если в скрипте
 *       её нет - тексты виджетов {@code state}, {@code players}, {@code timer} ставятся напрямую.</li>
 * </ul>
 */
public final class ArenaScreen implements Listener {

    public static final String SCREEN_ID = "wordbomb_arena";
    public static final String WIDGET_JOIN = "join";
    public static final String WIDGET_LEAVE = "leave";
    public static final String WIDGET_START = "start";
    public static final String RENDER_FUNCTION = "render";

    private static final String RES_SCREEN = "examples/wordbomb/screens/wordbomb_arena.jmc";
    private static final String RES_SCRIPT = "examples/wordbomb/scripts/wordbomb_arena.jmp";
    private static final String RES_PARTICLES = "examples/wordbomb/particles/";

    private final WordBombPlugin plugin;
    private boolean renderWarned;

    public ArenaScreen(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    private DisplayLib displayLib() {
        return (DisplayLib) DisplayLib.getInstance();
    }

    // ------------------------------------------------------------------ ресурсы

    /** Создать недостающие файлы в {@code plugins/DisplayLib/} и зарегистрировать их в DisplayLib. */
    public void installResources() {
        Path displayLibDir = displayLib().getDataFolder().toPath();
        boolean screenCreated = copyIfMissing(RES_SCREEN, displayLibDir.resolve("screens").resolve(SCREEN_ID + ".jmc"));
        copyIfMissing(RES_SCRIPT, displayLibDir.resolve("scripts").resolve(SCREEN_ID + ".jmp"));
        boolean particlesChanged = false;
        boolean particlesMissing = false;
        for (String effect : EffectPlayer.ALL) {
            particlesChanged |= copyIfMissing(RES_PARTICLES + effect + ".jmc", displayLibDir.resolve("particles").resolve(effect + ".jmc"));
            particlesMissing |= Particles.effect(effect) == null;
        }

        // всегда перечитываем свой файл: если в screens/ есть другой файл с тем же id, иначе победит он
        displayLib().getScreenRegistry().reloadScreen(SCREEN_ID);
        if (screenCreated) plugin.getLogger().info("Экран " + SCREEN_ID + " создан из ресурсов плагина.");
        if (particlesChanged || particlesMissing) {
            displayLib().getParticleRegistry().loadAll();
        }
        for (String effect : EffectPlayer.ALL) {
            if (Particles.effect(effect) == null) {
                plugin.getLogger().warning("Эффект " + effect + " не загружен DisplayLib (plugins/DisplayLib/particles/" + effect + ".jmc).");
            }
        }
        if (!displayLib().getScreenRegistry().hasScreen(SCREEN_ID)) {
            plugin.getLogger().warning("Экран " + SCREEN_ID + " не загружен DisplayLib (см. plugins/DisplayLib/screens/"
                    + SCREEN_ID + ".jmc): публичный экран не появится.");
        }
    }

    private boolean copyIfMissing(String resource, Path target) {
        if (Files.exists(target)) return false;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("Ресурс " + resource + " отсутствует в jar плагина");
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Создан " + target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось создать " + target + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ размещение

    /**
     * Поставить экран в точке из конфига. Экран, восстановленный DisplayLib после перезапуска, пересоздаётся:
     * при восстановлении теряется направление раскладки (см. {@link #open()}), и виджеты уходят за фон.
     */
    public void place() {
        replace();
    }

    /** Переставить экран в точку из конфига ({@code /wb config setHologram}, {@code /wb reload}). */
    public void replace() {
        GlobalScreenInstance existing = find();
        if (existing != null) UIManager.getInstance().closePublicScreenById(SCREEN_ID);
        open();
    }

    /**
     * {@code screen.yaw} в конфиге - куда смотрит экран (на зрителей), как у приватных экранов DisplayLib.
     * У публичного экрана DisplayLib поворот сущностей берёт из аргументов {@code yaw/pitch}, а раскладку виджетов
     * (право и глубина {@code z}) - из направления {@code Location}; чтобы виджеты с отрицательным {@code z} выходили
     * к зрителю, направление {@code Location} должно быть противоположно повороту экрана.
     */
    private void open() {
        Location at = plugin.screenLocation();
        if (at == null) {
            plugin.getLogger().warning("Мир экрана не загружен: публичный экран не поставлен.");
            return;
        }
        Location layout = at.clone();
        layout.setYaw(at.getYaw() + 180f);
        layout.setPitch(-at.getPitch());
        boolean ok = UIManager.getInstance().openPublicScreen(SCREEN_ID, layout, at.getYaw(), at.getPitch(), true);
        if (ok) {
            render();
        } else {
            plugin.getLogger().warning("DisplayLib не открыл публичный экран " + SCREEN_ID + ".");
        }
    }

    public GlobalScreenInstance find() {
        try {
            return UIManager.getInstance().findPublicScreenById(SCREEN_ID);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ состояние → экран

    /** Передать состояние игры на экран (вызывается при каждом изменении и раз в секунду). */
    public void render() {
        GlobalScreenInstance screen = find();
        if (screen == null || screen.isRemoved()) return;
        GameSession session = plugin.session();
        Messages m = plugin.chat().messages();
        int players = session.queue().size();
        int seconds = switch (session.state()) {
            case COUNTDOWN -> session.countdownLeft();
            case RUNNING -> session.turnSecondsLeft();
            case WAITING -> -1;
        };
        int round = session.round();
        String state = switch (session.state()) {
            case WAITING -> m.screenWaiting();
            case COUNTDOWN -> Text.fill(m.screenCountdown(), "seconds", seconds);
            case RUNNING -> Text.fill(m.screenRunning(), "round", round + 1, "count", players);
        };

        ScriptContext ctx = screen.getScriptContext();
        boolean hasRender = ctx != null && ctx.hasScript() && ctx.getScript() != null && ctx.getScript().hasFunction(RENDER_FUNCTION);
        if (hasRender && ctx.call(RENDER_FUNCTION, state, players, seconds, round)) return;
        if (!renderWarned && ctx != null && ctx.hasScript() && !hasRender) {
            renderWarned = true;
            plugin.getLogger().info("В скрипте экрана " + SCREEN_ID + " нет функции " + RENDER_FUNCTION
                    + "(state, players, seconds, round): тексты виджетов ставятся напрямую.");
        }
        setText(screen, "state", state);
        setText(screen, "players", Text.fill(m.screenPlayers(), "count", players));
        setText(screen, "timer", seconds >= 0 ? Text.fill(m.screenTimer(), "seconds", seconds) : m.screenTimerIdle());
    }

    private void setText(GlobalScreenInstance screen, String widgetId, String text) {
        Widget widget = screen.getWidget(widgetId);
        if (widget == null) return;
        try {
            new WidgetAPI(widgetId, widget).text(text);
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Виджет " + widgetId + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ клики

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(DisplayClickEvent event) {
        if (!event.isPublicScreen() || !SCREEN_ID.equals(event.getScreenId())) return;
        String id = event.getWidgetId();
        if (id == null) return;
        Player player = event.getPlayer();
        boolean ok;
        switch (id) {
            case WIDGET_JOIN -> ok = plugin.api().join(player);
            case WIDGET_LEAVE -> ok = plugin.api().leave(player);
            case WIDGET_START -> ok = plugin.api().start(player);
            default -> {
                return;
            }
        }
        // DisplayLib не играет clickSound на публичных экранах (клик не доходит до виджета), поэтому звук - здесь
        clickSound(player, ok);
        // Java уже обработала клик: скрипту экрана делать нечего
        event.setCancelled(true);
    }

    /** Щелчок кнопки; при отказе (уже в игре, не в очереди, игра идёт) - низкий «бас». */
    private static void clickSound(Player player, boolean ok) {
        if (ok) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        }
    }
}
