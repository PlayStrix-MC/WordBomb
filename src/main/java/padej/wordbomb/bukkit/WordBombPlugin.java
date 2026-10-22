package padej.wordbomb.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import padej.wordbomb.bukkit.api.ArenaApi;
import padej.wordbomb.bukkit.arena.ArenaScreen;
import padej.wordbomb.bukkit.arena.ConfigScreen;
import padej.wordbomb.bukkit.chat.ChatListener;
import padej.wordbomb.bukkit.command.WbCommand;
import padej.wordbomb.bukkit.command.WbTabCompleter;
import padej.wordbomb.bukkit.papi.WordBombExpansion;
import padej.wordbomb.bukkit.player.PlayerListener;
import padej.wordbomb.bukkit.player.ZoneTask;
import padej.wordbomb.bukkit.presentation.GamePresenter;
import padej.wordbomb.config.ConfigLoader;
import padej.wordbomb.config.Messages;
import padej.wordbomb.config.Place;
import padej.wordbomb.config.WordBombConfig;
import padej.wordbomb.core.GameSession;
import padej.wordbomb.core.Rules;
import padej.wordbomb.core.TemplateGenerator;
import padej.wordbomb.storage.ActionLogRepo;
import padej.wordbomb.storage.Database;
import padej.wordbomb.storage.DictionaryRepo;
import padej.wordbomb.storage.LegacyImporter;
import padej.wordbomb.storage.Moderation;
import padej.wordbomb.storage.ReportRepo;
import padej.wordbomb.storage.SuggestionRepo;
import padej.wordbomb.storage.WinnersRepo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Точка входа WordBomb: собирает конфиг, хранилище, ядро игры, экран и слушатели.
 * Порядок: конфиг → SQLite (+ импорт старых txt) → ядро → экран DisplayLib → команды и слушатели → PAPI.
 */
public final class WordBombPlugin extends JavaPlugin {

    public static final String DB_FILE = "wordbomb.db";

    private ConfigLoader configLoader;
    private WordBombConfig config;
    private Messages messages;
    private Rules rules;
    private Chat chat;
    private Async async;

    private Database db;
    private DictionaryRepo dictionary;
    private SuggestionRepo suggestions;
    private ReportRepo reports;
    private WinnersRepo winners;
    private ActionLogRepo actionLog;
    private Moderation moderation;

    private GamePresenter presenter;
    private GameSession session;
    private ArenaApi api;
    private ArenaScreen arena;
    private ConfigScreen configScreen;
    private WordBombExpansion papi;

    private BukkitTask tickTask;
    private BukkitTask zoneTask;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("DisplayLib") == null) {
            getLogger().severe("DisplayLib не найден: WordBomb не может работать без экранов.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1. конфиг и тексты
        configLoader = new ConfigLoader(getDataFolder().toPath());
        try {
            applyLoaded(configLoader.load());
        } catch (RuntimeException e) {
            getLogger().severe("Ошибка конфига: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        chat = new Chat(() -> messages);
        async = new Async(this);

        // 2. хранилище
        try {
            db = Database.open(getDataFolder().toPath().resolve(DB_FILE));
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Не удалось открыть базу " + DB_FILE + ": " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dictionary = new DictionaryRepo(db);
        suggestions = new SuggestionRepo(db);
        reports = new ReportRepo(db);
        winners = new WinnersRepo(db);
        actionLog = new ActionLogRepo(db);
        moderation = new Moderation(db, suggestions, reports, actionLog);
        importLegacy();
        long words = dictionary.count();
        if (words == 0) {
            getLogger().warning("Словарь пуст: положите dictionary.txt старого плагина в plugins/" + config.legacyDataFolder()
                    + "/ (или в plugins/WordBomb/) и перезапустите сервер, либо добавляйте слова через /wb add.");
        } else {
            getLogger().info("Словарь: " + words + " слов.");
        }

        // 3. ядро и представление
        presenter = new GamePresenter(this);
        session = new GameSession(rules, dictionary, new TemplateGenerator(dictionary, rules, new Random()), presenter);
        api = new ArenaApi(this);

        // 4. экран, команды, слушатели
        arena = new ArenaScreen(this);
        arena.installResources();
        configScreen = new ConfigScreen(this);
        configScreen.installResources();
        getServer().getPluginManager().registerEvents(arena, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        PluginCommand command = getCommand("wb");
        if (command != null) {
            command.setExecutor(new WbCommand(this));
            command.setTabCompleter(new WbTabCompleter(this));
        } else {
            getLogger().severe("Команда /wb не объявлена в plugin.yml");
        }
        tickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            session.tick();
            arena.render();
        }, 20L, 20L);
        zoneTask = getServer().getScheduler().runTaskTimer(this, new ZoneTask(this), 20L, 20L);
        // миры загружаются после STARTUP-плагинов: ставим экран на первом тике сервера
        getServer().getScheduler().runTask(this, () -> {
            checkWorlds();
            arena.place();
        });

        // 5. PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papi = new WordBombExpansion(this);
            if (papi.register()) {
                getLogger().info("PlaceholderAPI: плейсхолдеры %wordbomb_*% зарегистрированы.");
            } else {
                papi = null;
            }
        }
        getLogger().info("WordBomb " + getPluginMeta().getVersion() + " запущен.");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (zoneTask != null) zoneTask.cancel();
        if (session != null) {
            try {
                session.abort();
            } catch (RuntimeException e) {
                getLogger().warning("Ошибка при завершении игры: " + e.getMessage());
            }
        }
        if (presenter != null) presenter.shutdown();
        if (papi != null) papi.unregister();
        if (async != null) async.shutdown();
        if (db != null) db.close();
        getLogger().info("WordBomb выключен.");
    }

    // ------------------------------------------------------------------ конфиг

    private void applyLoaded(ConfigLoader.Loaded loaded) {
        config = loaded.config();
        messages = loaded.messages();
        rules = loaded.rules();
    }

    /** Перечитать {@code config.jmc} и {@code messages.jmc}; правила применяются к следующей игре. */
    public void reload() {
        ConfigLoader.Loaded loaded = configLoader.load();
        applyLoaded(loaded);
        checkWorlds();
        if (session != null && !session.isRunning()) {
            session.abort();
            session = new GameSession(rules, dictionary, new TemplateGenerator(dictionary, rules, new Random()), presenter);
        }
        if (arena != null) arena.replace();
    }

    private void checkWorlds() {
        if (Bukkit.getWorld(config.arena().world()) == null) {
            getLogger().severe("Мир арены '" + config.arena().world() + "' не найден (config.jmc → arena.world): игра не сможет начаться.");
        }
        if (Bukkit.getWorld(config.screen().world()) == null) {
            getLogger().severe("Мир экрана '" + config.screen().world() + "' не найден (config.jmc → screen.world): экран не появится.");
        }
    }

    private void importLegacy() {
        Path own = getDataFolder().toPath();
        Path legacy = own.resolveSibling(config.legacyDataFolder());
        Path source = LegacyImporter.hasLegacyFiles(own) ? own : LegacyImporter.hasLegacyFiles(legacy) ? legacy : null;
        if (source == null) return;
        LegacyImporter importer = new LegacyImporter(db, dictionary, suggestions, reports, winners, actionLog,
                this::uuidByName, getLogger()::info);
        if (importer.alreadyImported()) return;
        getLogger().info("Найдены данные старого плагина в " + source + ": переносим в " + DB_FILE + " (файлы остаются как бэкап).");
        long started = System.currentTimeMillis();
        try {
            LegacyImporter.Report report = importer.run(source);
            if (report != null) {
                getLogger().info(String.format("Импорт завершён за %.1f с: слов %d, предложений %d, жалоб %d, победителей %d, записей журнала %d",
                        (System.currentTimeMillis() - started) / 1000.0, report.dictionaryWords(), report.suggestions(),
                        report.reports(), report.winners(), report.actions()));
            }
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Импорт старых данных не удался: " + e.getMessage(), e);
        }
    }

    private boolean offlineModeWarned;

    @SuppressWarnings("deprecation")
    private UUID uuidByName(String name) {
        if (name == null || name.isBlank()) return null;
        if (!getServer().getOnlineMode() && !offlineModeWarned) {
            offlineModeWarned = true;
            getLogger().warning("Сервер в offline-режиме: UUID победителей из winners.txt вычисляются локально (OfflinePlayer) - "
                    + "совпадут с UUID игроков этого сервера.");
        }
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    // ------------------------------------------------------------------ доступ для остальных классов

    public WordBombConfig config() {
        return config;
    }

    public Rules rules() {
        return rules;
    }

    public ConfigLoader configLoader() {
        return configLoader;
    }

    public Chat chat() {
        return chat;
    }

    public Async async() {
        return async;
    }

    public DictionaryRepo dictionary() {
        return dictionary;
    }

    public SuggestionRepo suggestions() {
        return suggestions;
    }

    public ReportRepo reports() {
        return reports;
    }

    public WinnersRepo winners() {
        return winners;
    }

    public ActionLogRepo actionLog() {
        return actionLog;
    }

    public Moderation moderation() {
        return moderation;
    }

    public GameSession session() {
        return session;
    }

    public GamePresenter presenter() {
        return presenter;
    }

    public ArenaApi api() {
        return api;
    }

    public ArenaScreen arena() {
        return arena;
    }

    public ConfigScreen configScreen() {
        return configScreen;
    }

    public void papiCacheInvalidate() {
        if (papi != null) papi.invalidate();
    }

    /** Центр арены или {@code null}, если мир не загружен. */
    public Location arenaCenter() {
        return toLocation(config.arena());
    }

    /** Точка публичного экрана или {@code null}, если мир не загружен. */
    public Location screenLocation() {
        return toLocation(config.screen());
    }

    private static Location toLocation(Place p) {
        World world = Bukkit.getWorld(p.world());
        if (world == null) return null;
        return new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }
}
