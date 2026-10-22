# WordBomb — архитектура recode

Версия: Paper 1.21.11, JDK 21. Документ фиксирует решения, принятые до начала кодирования.
Текущее поведение старого плагина описано в `CLAUDE.md`; здесь — как его переписываем.

---

## 1. Решения

| # | Тема | Решение |
|---|---|---|
| 1 | Арена | **Публичный экран** DisplayLib (`openPublicScreen`, `persistent = true`) у голограммы. Один экран на всех, кнопки «Играть» и «Выйти» видны всем рядом, кликает игрок. |
| 2 | Файлы экрана | Свои файлы DisplayLib — в папке WordBomb: `plugins/WordBomb/{screens,scripts,particles}` и `scripts.jma` (набор содержимого DisplayLib, `ContentPacks.register`). Если файла **нет** — создаём из ресурсов плагина (или переносим из `plugins/DisplayLib/...`, где он лежал раньше). Если **есть** — только читаем, сервер может менять разметку как хочет. |
| 3 | Разделение | **Логика плагина — Java.** **Логика экрана — `.jmp`**, привязан к `.jmc`. Скрипт экрана вызывает Java-методы плагина, Java обновляет экран через функции скрипта. |
| 4 | Баги | Чиним сразу в recode (список в §9). |
| 5 | Хранилище | **SQLite** вместо txt-файлов (обоснование в §6). |
| 6 | Тесты | **JUnit 5** (Jupiter 5.11.3, как в DisplayLib). Ядро тестируется без сервера. |

---

## 2. Стек и зависимости

| Что | Как подключается | Зачем |
|---|---|---|
| Paper API 1.21.11 | `compileOnly` (repo `papermc`) | Bukkit/Paper API, `AsyncChatEvent` |
| DisplayLib 3.1.0 | `compileOnly libs/DisplayLib-1.21.11+3.1.0.jar`, `plugin.yml: depend: [DisplayLib]` | Экраны, клики, частицы |
| Jumper 0.12.0 | `implementation` из `libs/jmp-0.12.0.jar`, **shade + relocate** | `config.jmc` и `messages.jmc` через records (`loadOrCreate`, `update`) |
| sqlite-jdbc | `org.xerial:sqlite-jdbc`, через `libraries:` в `plugin.yml` (Paper library loader) или shade | SQLite |
| PlaceholderAPI | `compileOnly`, `softdepend` | `%wordbomb_*%` |
| JUnit Jupiter 5.11.3 | `testImplementation` | Тесты ядра и хранилища |

Сборка — Gradle (Kotlin DSL) с wrapper. Jar — `WordBomb-1.21.11+<версия>.jar`.
Нативный NMS не используем, поэтому paperweight не нужен.

---

## 3. Структура модулей (пакеты)

Корневой пакет: `padej.wordbomb` (DisplayLib использует `padej.displayLib`, поэтому конфликта нет).

```
padej.wordbomb
├── core/                    # чистая Java, БЕЗ Bukkit — основной объект тестов
│   ├── GameSession          # состояние игры и переходы (§5)
│   ├── Turn, Round          # текущий ход, раунд, флаги ответов
│   ├── TurnTimer            # абстракция тиков: TimeSource / Scheduler
│   ├── TemplateGenerator    # шаблон 2/3 буквы, префикс/суффикс, антиповтор
│   ├── WordValidator        # раскладка → кириллица → шаблон → словарь → usedWords
│   ├── TextNormalizer       # lower, ё→е, EN→RU раскладка
│   ├── Rules                # все константы и тайминги (из config.jmc)
│   └── Events               # интерфейс GameListener (уведомления для presentation)
├── storage/                 # SQLite (§6)
│   ├── Database             # подключение, WAL, миграции схемы
│   ├── DictionaryRepo       # lookup, random word, add, remove
│   ├── SuggestionRepo       # голоса за предложения
│   ├── ReportRepo           # жалобы
│   ├── WinnersRepo          # победы по UUID
│   ├── ActionLogRepo        # журнал модерации + поиск
│   └── LegacyImporter       # перенос из txt при первом запуске
├── config/
│   ├── WordBombConfig       # record: настройки плагина, читается через Config.loadOrCreate
│   ├── Place                # record: мир + координаты + yaw/pitch
│   └── Messages             # record: тексты, messages.jmc через Config.loadOrCreate
├── bukkit/                  # всё, что трогает сервер
│   ├── WordBombPlugin       # onEnable/onDisable, сборка зависимостей
│   ├── chat/ChatListener    # перехват чата, async-проверка, возврат в main
│   ├── player/QuitListener, ZoneListener (выход за 50 блоков = выход из очереди)
│   ├── command/WbCommand, WbTabCompleter, Permissions
│   ├── presentation/        # BossBar, звуки, сообщения, GameListener для Bukkit
│   ├── effects/             # WordEffects (TextShatter), SkullEffect (Particles)
│   ├── arena/               # ArenaScreen: публичный экран, DisplayClickEvent, вызовы в скрипт
│   ├── api/ArenaApi         # объект, который видит скрипт экрана (глобал wordbomb)
│   └── papi/WordBombExpansion
└── util/                    # время, UUID, Ids, Strings (мелочь)
```

Правило: `core` не импортирует `org.bukkit` и `padej.displayLib`. Всё, что связывает с сервером, сидит в `bukkit/`.

---

## 4. Потоки

- **Чат (AsyncChatEvent)** — асинхронный поток. Нормализация и валидация делаются здесь: `WordValidator` спрашивает `DictionaryRepo` (SQLite, read-only соединение, WAL). Записей здесь нет.
- **Главный поток** — `GameSession`, таймеры, экран, эффекты, команды. Результат валидации возвращается через `runTask`.
- **DB-writer** — один выделенный поток с очередью записи (`ExecutorService`, single-thread). Все `INSERT/UPDATE/DELETE` идут сюда, главный поток не ждёт. Для команд модерации ответ игроку отправляется после подтверждения записи (через `CompletableFuture` → `runTask`).
- Таймеры хода и отсчёта — `BukkitRunnable` в главном потоке. В `core` они представлены интерфейсом `TurnTimer`, так что логику можно гонять в тестах без планировщика.

---

## 5. Игровой движок (`core/GameSession`)

### Состояния
```
WAITING ──(≥2 в очереди)──► COUNTDOWN(countdownSeconds)
   ▲                            │ (<2)              │(0)
   │◄───────────────────────────┘                   ▼
   │                                              TURN ──(ход сделан)──► TURN (следующий)
   │                                                │  └(все ответили)──► новый раунд (round++)
   │                                                │(таймаут или выход)
   │                                                ▼
   │                                           ELIMINATION
   │                                                │(≥2 живых)──► TURN
   │                                                │(1 живой)
   └──────────────(сброс)──────────── ENDED ◄───────┘
```

### Правила (после исправлений)
- Очередь ходов по кругу. Текущему игроку выдаётся шаблон.
- Ход = `max(minTurnSeconds, turnBaseSeconds − round)`. Раунд закрывается, когда **каждый** живой ответил хотя бы раз.
- **Выбывание.** Ход после выбывания текущего игрока переходит к **следующему** живому (исправлено: индекс пересчитывается от позиции выбывшего, а не назад).
- `/wb start` работает: при ≥2 в очереди запускает отсчёт немедленно (исправлено инверсия условия). Право не требуется.
- Во время игры перехватываются **только** сообщения участников. Сообщения остальных игроков в чат не трогаем (исправлено).
- Ё принимаем (регулярка `[а-яё]+`, словарь хранит нормализованное `е`, поиск по нормализованной форме). Исправлено.
- `usedWords` хранит нормализованную форму (ё→е).
- Выход игрока из зоны 50 блоков во время очереди (до старта) = выход из очереди. Во время игры выход из зоны = выбывание.
- `getTemplate()` и `currentPlayer()` безопасны при пустом списке: возвращают `Optional`.
- Таймер хода: тик на 20 тиков, отсчёт до нуля, выбывание при нуле. Чтобы не было «лишней» секунды, отсчёт идёт на `timeLeft−1` каждую секунду, а выбывание наступает при достижении 0 сразу (без доп. тика).

### Выход и восстановление
- Перезапуск сервера во время игры: игра не восстанавливается, в БД ничего не пишется кроме победителя (если уже определён). Фиксируем как явное поведение.

---

## 6. Хранилище (SQLite)

Почему SQLite: словарь 1,5 млн строк в памяти — сотни мегабайт кучи; текущая проверка перечитывает 34 МБ на каждое новое слово. В SQLite lookup по индексу быстрый и не тратит память; записи транзакционные.

Файл: `plugins/WordBomb/wordbomb.db`. Режим: WAL, `synchronous=NORMAL`, `busy_timeout=5000`, `foreign_keys=ON`.

```sql
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);           -- schema_version

CREATE TABLE dictionary (
  id   INTEGER PRIMARY KEY,
  word TEXT NOT NULL UNIQUE                -- нормализовано: lower, ё→е
);

CREATE TABLE suggestions (                 -- offers.txt
  word        TEXT NOT NULL,
  player_uuid TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (word, player_uuid)
);
CREATE INDEX suggestions_votes ON suggestions(word);

CREATE TABLE reports (                     -- reports.txt
  word        TEXT NOT NULL,
  player_uuid TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (word, player_uuid)
);

CREATE TABLE winners (                     -- winners.txt, ключ UUID (исправлено)
  player_uuid TEXT PRIMARY KEY,
  name        TEXT NOT NULL,               -- последний известный ник
  wins        INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX winners_wins ON winners(wins DESC);

CREATE TABLE action_log (                  -- actions.log
  id      INTEGER PRIMARY KEY,
  ts      INTEGER NOT NULL,                -- epoch millis
  date    TEXT NOT NULL,                   -- yyyy-MM-dd (для поиска)
  time    TEXT NOT NULL,                   -- HH:mm:ss
  player  TEXT NOT NULL,
  action  TEXT NOT NULL,                   -- accept|block|add|remove
  word    TEXT NOT NULL
);
CREATE INDEX action_log_date ON action_log(date);
```

Детали:
- **Случайное слово для шаблона:** `SELECT word FROM dictionary WHERE id >= ? ORDER BY id LIMIT 1`, где `?` — случайное число в `[1, max(id)]`. Пул берётся из **всего** словаря (исправлено: раньше только первые 10 000).
- **Голоса** — `COUNT(*)` по `suggestions` за слово. Топ-10 — `GROUP BY word ORDER BY COUNT(*) DESC LIMIT 10`.
- **Блокировка слова** (`/wb block`) — одна транзакция: удалить из `dictionary`, из `reports`, записать `action_log`.
- **Принятие** (`/wb accept`) — одна транзакция: `INSERT` в `dictionary`, удалить из `suggestions`, `action_log`.
- **Победитель** — `INSERT ... ON CONFLICT(player_uuid) DO UPDATE SET wins = wins + 1, name = excluded.name`.

### Миграция `LegacyImporter`
При первом запуске, если `wordbomb.db` ещё не создан и в папке есть старые файлы:
1. `dictionary.txt` → `dictionary` (нормализация, дубликаты пропускаются). Файл остаётся на месте как бэкап.
2. `offers.txt`, `reports.txt` → `suggestions`, `reports`.
3. `winners.txt` (ключ — ник) → `winners`. UUID берётся через `Bukkit.getOfflinePlayer(name).getUniqueId()`; на сервере с онлайн-режимом это точный UUID, в оффлайне — временный (отмечаем в логе).
4. `actions.log` → `action_log` (разбор строки `[дата][время][ник][действие][слово]`).

Импорт выполняется в одной транзакции, после успеха в `meta` пишется `legacy_imported=1`.

---

## 7. Конфиг (`config.jmc`) и тексты (`messages.jmc`)

Настройки — **record** с комментариями. Jumper сам создаёт файл из значений по умолчанию, дописывает новые переменные при обновлении плагина и не трогает то, что сервер уже правил.

```java
@Config.Comment("WordBomb settings. Edit and run /wb reload.")
public record WordBombConfig(
        @Config.Comment("Game center: the zone and the circle of players are built around it")
        Place arena,
        @Config.Comment("Public screen placement (persistent)")
        Place screen,
        @Config.Comment("Zone radius: leaving it removes the player from the queue or eliminates them")
        int zoneRadius,
        int circleRadius,
        int spectatorLeash,
        int spectatorLift,
        @Config.Comment("Seconds of countdown after 2 players join")
        int countdownSeconds,
        @Config.Comment("Turn length = max(minTurnSeconds, turnBaseSeconds - round)")
        int turnBaseSeconds,
        int minTurnSeconds,
        int lastSecondsWarning,
        @Config.Comment("Chance of a two-letter template (otherwise three)")
        double twoLetterChance,
        int templateHistory,
        int templateAttempts,
        int minPlayers,
        @Config.Comment("Click distance to the public screen")
        double screenInteractionRadius) {

    public static final WordBombConfig DEFAULTS = new WordBombConfig(
            new Place("world", -451.5, 71.0, 6459.5, 0f, 0f),
            new Place("world", -451.0, 79.0, 6489.0, -180f, 0f),
            50, 10, 20, 10, 20, 20, 5, 5, 0.9, 20, 50, 2, 8.0);
}

public record Place(String world, double x, double y, double z, float yaw, float pitch) {}
```

Загрузка и сохранение:

```java
Path file = dataFolder.resolve("config.jmc");
WordBombConfig cfg = Config.loadOrCreate(file, WordBombConfig.DEFAULTS);   // создаст файл, если его нет

// /wb config setCenter — сохраняем одно значение, комментарии сервера не трогаем
Config.update(file, "arena", Map.of("world", w, "x", x, "y", y, "z", z, "yaw", 0f, "pitch", 0f));
cfg = Config.loadOrCreate(file, WordBombConfig.DEFAULTS);                  // перечитать
```

- Ошибки значений (например, строка вместо числа) падают при старте с путём файла и именем ключа — лучше, чем тихий fallback в старом коде.
- Неизвестный мир — проверяем при старте и пишем понятную ошибку (раньше был тихий fallback к `(0.5, 0.5, 0.5)`).
- `/wb config setHologram` сохраняется тем же `Config.update`.
- Отдельного писателя и ручного разбора файла больше не нужно.

### `messages.jmc`

Тексты тоже record (`Messages`, ~60 полей). Сервер правит строки в файле, плагин дописывает новые при обновлении. Формат с плейсхолдерами: `{player}`, `{count}`, `{template}`, `{seconds}` — подстановка в коде.

```java
@Config.Comment("WordBomb messages. Placeholders: {player} {count} {template} {seconds} {word}.")
public record Messages(
        @Config.Comment("Shown when the player joins the queue")
        String joined,
        String alreadyInGame,
        String notEnoughPlayers,
        String wordNotFit,
        String notInDictionary,
        String wordAlreadyUsed,
        String turnOf,            // "Ходит игрок: {player}. Шаблон: {template}"
        String timeUp,            // "Время вышло! {player} выбывает!"
        String winner,
        String gameOver
        // ...остальные тексты из CLAUDE.md §10
) {}
```

Тексты в Java-коде не хранятся: все пользовательские строки идут через `Messages`.

## 8. Экран и скрипт (DisplayLib + Jumper)

### Файлы
| Файл | Кто создаёт | Кто правит |
|---|---|---|
| `plugins/WordBomb/screens/wordbomb_*.jmc` | WordBomb — **только если файла нет** | сервер |
| `plugins/WordBomb/scripts/wordbomb_*.jmp` | WordBomb — **только если файла нет** | сервер |
| `plugins/WordBomb/particles/wordbomb_*.jmc` | WordBomb — **только если файла нет** | сервер |
| `plugins/WordBomb/scripts.jma` | WordBomb — из ресурса `examples/wordbomb/scripts.jma` (своя политика, кладётся до регистрации набора), **только если файла нет** | сервер может расширять белый список |

Папка WordBomb — набор содержимого DisplayLib (`ContentPacks.register(plugin)` в `arena/PackFiles`): DisplayLib сам
загружает из неё экраны и частицы, следит за `screens/` (hot reload), скрипты экранов ищет только в её `scripts/`
и выполняет под её `scripts.jma`, а не под политикой DisplayLib. Файл, оставшийся в `plugins/DisplayLib/...` от
прежних версий, при запуске переносится в папку WordBomb (с правками сервера). Для редакторов Jumper в jar лежит
`META-INF/jumper/host.jmc` (хост `WordBomb`, глобалы экранов DisplayLib).

Ресурсы плагина: `examples/wordbomb/screens/wordbomb_arena.jmc` и `examples/wordbomb/scripts/wordbomb_arena.jmp`.

### Экран `wordbomb_arena.jmc` (набросок)
```java
String id = "wordbomb_arena";
String screenType = "PUBLIC";
int tickRate = 10;
double interactionRadius = 8;
String script = "wordbomb_arena.jmp";
dyn background = { color: [10, 10, 10], alpha: 200, scale: [12, 6, 1], anchor: "CENTER" };

dyn widgets = [
    { id: "title",   type: "TEXT_BUTTON", text: "WordBomb", position: [0, 2, 0], backgroundAlpha: 0 },
    { id: "state",   type: "TEXT_BUTTON", text: "Ожидание игроков", position: [0, 1, 0], backgroundAlpha: 0 },
    { id: "players", type: "TEXT_BUTTON", text: "☻ 0", position: [-3, 0, 0], backgroundAlpha: 0 },
    { id: "timer",   type: "TEXT_BUTTON", text: "⌚ -", position: [3, 0, 0], backgroundAlpha: 0 },
    { id: "join",    type: "TEXT_BUTTON", text: "Играть", position: [-1.5, -2, 0], onClick: "join" },
    { id: "leave",   type: "TEXT_BUTTON", text: "Выйти",  position: [1.5, -2, 0],  onClick: "leave" },
];
```

### Скрипт `wordbomb_arena.jmp` (набросок)
```java
void join(dyn widget, dyn player) {
    wordbomb.join(player);
}

void leave(dyn widget, dyn player) {
    wordbomb.leave(player);
}

// вызывает Java при каждом изменении состояния (раз в секунду и при событиях)
void render(String state, int players, int seconds) {
    screen.widget("state").text(state);
    screen.widget("players").text("☻ " + players);
    screen.widget("timer").text(seconds > 0 ? "⌚ " + seconds : "⌚ -");
}
```

### Связь Java ↔ скрипт
- **Скрипт → Java:** глобал `wordbomb` (объект `ArenaApi`) с методами `join(player)`, `leave(player)`, `status()`. Глобал должен попадать в скрипт экрана. Сейчас DisplayLib передаёт глобалы только сам, поэтому **нужна небольшая доработка DisplayLib**: API для регистрации глобалов плагинов (например `ScreenGlobals.register("wordbomb", arenaApi)`) и правило для него в `scripts.jma`.
- **Java → скрипт:** `GlobalScreenInstance.getScriptContext().call("render", state, players, seconds)` — вызывается из `presentation` при изменениях и раз в секунду.
- **Клики:** обрабатываются скриптом (`onClick`), Java видит их через `DisplayClickEvent` при необходимости (для логов/отмены).
- **Пока доработки нет:** временный обходной путь — `player.command("wb join")` из скрипта. Работает без изменений DisplayLib, но запускает команду от имени игрока и зависит от прав команды; использовать только как временную заглушку.

### Ограничения, которые учитываем
- Публичный экран не знает, участвует ли конкретный игрок. Поэтому обе кнопки всегда видны; `join` для участника и `leave` для не-участника отвечают сообщением «Вы уже…» / «Вы не в игре», без ошибок.
- `interactionRadius` ограничивает клики (8 блоков), а зона игры (50 блоков) — правило выхода. Это два разных параметра.
- Экран persistent: после перезапуска встаёт на место через `restorePublicScreens`. Привязка к миру `world`.

### Политика доступа: каждый плагин задаёт своим скриптам свою

- Сделано в DisplayLib через наборы содержимого: у каждой папки-набора свой `scripts.jma` в корне. Скрипты экранов
  WordBomb выполняются под `plugins/WordBomb/scripts.jma`, политика DisplayLib на них не действует.
- Политика одного набора не видит белых списков других: скрипт WordBomb не получает доступ к API, разрешённому
  другому плагину, и наоборот.

```java
// plugins/WordBomb/scripts.jma - чтобы открыть скриптам API плагина, дописать:
Policy.allowClass("padej.wordbomb.bukkit.api.ArenaApi");
```

- Сервер может дополнять `.jma` своими правилами; `/displaylib reload` перечитывает политики всех наборов.

## 9. Игровые баги, которые исправляем в recode

| Было | Станет |
|---|---|
| `/wb start` при ≥2 игроках отказывал (инверсия) | старт при ≥2, без прав |
| После выбывания текущего игрока ход шёл назад | ход к следующему живому |
| Случайные шаблоны из первых 10 000 строк | пул из всего словаря (SQLite) |
| Полный проход словаря на каждое новое слово | индексированный lookup |
| Ё не принималось в чате | принимается, нормализуется |
| Все сообщения чата отменялись во время игры | отменяются только сообщения участников |
| Победы по нику | победы по UUID, ник обновляется |
| Файлы писались без блокировок | SQLite, транзакции, один writer |
| `setCenter` тихо падал без мира | ошибка с понятным текстом |
| Тихий fallback координат | ошибка при старте |
| Пустые заглушки (`GameTimer`, `PlayerManager`, `PlayerSkinTexture`) | удалены |
| Подписка экрана на состояние игры только при создании сессии | экран подписан через `GameListener` всегда (публичный экран один) |

---

## 10. Эффекты

- **Слово в центре** — `TextShatter`: новое слово появляется, предыдущее рассыпается на буквы эффектом `letters` из DisplayLib. Старые `WordDisplay` и `CharParticleExplode` удаляются.
- **Череп при выбывании** — свой эффект `particles/wordbomb_skull.jmc` (по образцу `letters.jmc`), запуск `Particles.play("wordbomb_skull", loc)`. Цвет градиента `#FFCC45 → #FF6545` задаётся в эффекте.
- **Звуки** — таблица из `CLAUDE.md` §6.8, перенесена в `presentation/SoundTable`.
- **Боссбар** — `presentation/TurnBossBar`, формат как раньше.

---

## 11. Команды и права

Без изменений по синтаксису, плюс `leave` (появился, раньше был только кнопкой) и `reload`.

Одно право - `wordbomb.eventer` (по умолчанию op). Обычные игроки вступают в игру и выходят **кнопками публичного экрана**;
из команд им доступны только голоса и топы.

| Команда | Право |
|---|---|
| `/wb offer`, `/wb report`, `/wb offer-list`, `/wb report-list`, `/wb top` | - |
| `/wb join`, `/wb leave`, `/wb start` | `wordbomb.eventer` |
| `/wb accept`, `/wb block`, `/wb add`, `/wb remove`, `/wb search`, `/wb config ...`, `/wb mod ...`, `/wb reload` | `wordbomb.eventer` |

Старое `event_manager.event` не поддерживается: выдайте `wordbomb.eventer` той же группе.

---

## 12. Тесты (JUnit 5)

Цель: ядро и хранилище проверяются без сервера.

| Класс тестов | Что покрывает |
|---|---|
| `TemplateGeneratorTest` | размеры 2/3, вероятность 90/10 (статистически, фиксированный seed), префикс/суффикс, антиповтор 20, 50 попыток |
| `WordValidatorTest` | раскладка EN→RU, регулярка, ё, шаблон внутри слова, словарь, usedWords |
| `TextNormalizerTest` | ё→е, регистр, раскладка |
| `GameSessionTest` | старт при 2, отсчёт и отмена, порядок ходов, раунд закрывается при всех ответивших, выбывание: ход к следующему, конец при 1 живом, пустой список, выход во время игры |
| `TurnTimerTest` | формула хода `max(5, 20−round)`, отсчёт без лишней секунды |
| `DictionaryRepoTest` | lookup, random по всему словарю, add/remove, дубликаты (SQLite in-memory или temp-файл) |
| `SuggestionRepoTest`, `ReportRepoTest` | голоса уникальны на игрока, топ-10 |
| `WinnersRepoTest` | инкремент по UUID, смена ника |
| `ActionLogRepoTest` | поиск по дате, игроку, действию (`none`) |
| `LegacyImporterTest` | разбор всех четырёх txt-форматов, идемпотентность |
| `ConfigLoaderTest` | `config.jmc` → Rules, ошибка при неизвестном мире |

Bukkit-слой проверяем вручную на тестовом сервере или через MockBukkit (опционально, если понадобится). Тесты `presentation` и `arena` пишем после стабилизации DisplayLib-интеграции.

Запуск: `./gradlew test`. CI — опционально (GitHub Actions по образцу DisplayLib).

---

## 13. Порядок работ

1. Gradle-каркас, зависимости, пустой плагин, загрузка `config.jmc`.
2. `core` + тесты (без Bukkit): `TemplateGenerator`, `WordValidator`, `GameSession`.
3. `storage` (SQLite) + `LegacyImporter` + тесты.
4. Команды и чат-слушатель, игра без экрана (через команды).
5. Публичный экран: ресурсы `wordbomb_arena.jmc` / `.jmp`, `ArenaApi`, связка с DisplayLib.
6. Доработка DisplayLib: регистрация глобалов плагинов (до этого — временный `player.command`).
7. Эффекты: `letters`-шатер для слова, свой `wordbomb_skull.jmc`.
8. PAPI, права, миграция со старой версии, проверка на тестовом сервере.

---

## 14. Требования к DisplayLib (до шага 6)

1. **Глобалы плагинов для скриптов экрана.** API вида `ScreenGlobals.register(name, object)`, и чтобы глобал попадал в каждый скрипт экрана (`JumperEngine.load` получает его в `globals`).
2. **Политика на экран.** Поле `policy` в `.jmc` выбирает файл из `policies/`. Без поля - `policies/default.jma`. Каждый экран исполняет скрипт под своей политикой, белые списки не складываются (сейчас DisplayLib читает один `scripts.jma` на всё).

Остальное из DisplayLib (публичные экраны, `openPublicScreen` с `persistent`, `DisplayClickEvent`, `TextShatter`, `ScriptContext.call`) уже есть в текущей версии 3.1.0.

---

## 15. Открытые вопросы

- Обрабатывать ли ситуацию, когда DisplayLib не установлен: плагин выключается с ошибкой (рекомендуется) или работает только командами.

---

## 16. Состояние реализации

Код написан по §1-§13. Отличия и уточнения относительно плана выше:

| Тема | Как сделано |
|---|---|
| Интерфейс словаря в ядре | Называется `core/Lexicon` (а не `Dictionary`), чтобы не путаться с `java.util.Dictionary` при `import java.util.*`. `storage/DictionaryRepo` его реализует. |
| Клики по экрану | Java обрабатывает `DisplayClickEvent` и сама играет звук клика (DisplayLib 3.1.0 не играет `clickSound` на публичных экранах); по id виджета (`join`, `leave`, `start`) и **отменяет** событие, поэтому глобалы для скрипта (§14.1) пока не нужны. Функции `join`/`leave` в `wordbomb_arena.jmp` - запасной путь через `player.command("wb ...")`, срабатывает только если плагин не перехватил клик. |
| Java → скрипт | `ArenaScreen.render()` вызывает `render(state, players, seconds, round)` скрипта при каждом событии игры и раз в секунду. Если функции в скрипте нет, тексты виджетов `state`/`players`/`timer` ставятся напрямую через `WidgetAPI`. |
| Политика на экран (§14.2) | DisplayLib 3.1.0 поля `policy` не поддерживает; `wordbomb.jma` не поставляется. Скрипт экрана работает под общим `scripts.jma` - ему хватает `player.command`. |
| `/wb start` | При ≥2 игроках в очереди игра начинается **сразу** (отсчёт пропускается). Без прав. |
| Ход после выбывания | К следующему живому; выбывание текущего игрока не сдвигает очередь назад (`GameSessionTest`). |
| Папка старых данных | Импорт ищет txt-файлы сначала в `plugins/WordBomb/`, затем в `plugins/<legacyDataFolder>/` (по умолчанию `word_bomb`). После импорта в `meta` пишется `legacy_imported=1`; файлы остаются. |
| Потоки | Чат: нормализация + `DictionaryRepo.contains` в async-потоке `AsyncChatEvent`, ход - через `runTask`. Команды и запись побед - поток `WordBomb DB` (`bukkit/Async`), ответ игроку - в главном потоке. Один `Connection` SQLite под блокировкой (`storage/Database`). |
| Конфиг | `config.jmc` содержит дополнительно `wordHeight`, `wordScale`, `freezePlayers`, `legacyDataFolder`. `messages.jmc` - record `Messages` (~75 строк), `&`-коды, плейсхолдеры `{player}` и т.д. |
| Заморозка | `PlayerFreezer` сохраняет исходные значения атрибутов и возвращает их (не 0.1/0.42). Наблюдателям возвращается прежний игровой режим. |
| Экран | `plugins/WordBomb/screens/wordbomb_arena.jmc`, `scripts/wordbomb_arena.jmp`, `particles/wordbomb_skull.jmc` создаются из ресурсов `examples/wordbomb/...` только если файлов нет. Экран ставится на первом тике сервера (плагин грузится на `STARTUP`, миры позже), только если DisplayLib ещё не восстановил persistent-экран с этим id. `/wb config setHologram` без аргументов ставит экран в точку админа лицом к нему. |
| Слово в центре | `effects/WordDisplay`: `TextDisplay` с градиентом `#FFCC45 → #FF6545`, при смене - `Particles.shatter(...).effect("letters").hold(0)`. Нужен пример `letters.jmc` DisplayLib (`/displaylib examples`); без него слово просто меняется, в лог - предупреждение. |
| Экран настроек | `wordbomb_config` (приватный, `/wb config` без аргументов, `wordbomb.eventer`): значения настроек - кнопки с `id = ключ config.jmc`. Java пишет значения в виджеты (`ConfigScreen`), скрипт превращает клики в команды: значение → `player.input(...)` → `/wb config set <ключ> <значение>` (проверка до записи в файл, `ConfigLoader.setValue`). Модерация словаря - отдельный экран `wordbomb_mod` (ниже). Нужен DisplayLib с `player.input`. |
| Экран модерации | `wordbomb_mod` (приватный, `/wb mod`, `wordbomb.eventer`, morph включён): вкладки предложения/жалобы, 8 строк на страницу, ◀ ▶. Java (`ModerationScreen`) кладёт страницу в `storage` игрока DisplayLib (`wordbomb_mod.kind/page/pages/total/row.N.word/votes`) и открывает экран заново (`switchScreen` на тот же id → morph от старых слов к новым; нужен DisplayLib от 22.10.2026, где цель morph - состояние после `onOpen`). Скрипт `wordbomb_mod.jmp` в `onOpen` читает storage, клики превращает в `/wb mod tab|page` и `/wb accept|block`. После `accept`/`block` экран перерисовывается. |
| Тесты | `./gradlew test`: ядро (`core`), конфиг (`ConfigLoaderTest`, против настоящего Jumper) и хранилище (`storage`, SQLite in-memory). Слой `bukkit` - вручную на тестовом сервере. |

Что проверить на тестовом сервере в первую очередь: расстановка по кругу и заморозка, чат во время игры, клики по публичному экрану, импорт `dictionary.txt` (1,5 млн строк - несколько секунд при старте), `%wordbomb_top_1%`.
