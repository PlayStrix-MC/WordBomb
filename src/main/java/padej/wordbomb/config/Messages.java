package padej.wordbomb.config;

import me.padej.jumper.interp.Config;

/**
 * Все тексты плагина ({@code plugins/WordBomb/messages.jmc}). Цвета - {@code &}-коды, плейсхолдеры в фигурных
 * скобках подставляются в коде ({@link Text#fill}). В Java строк для игрока нет - только здесь.
 */
@Config.Comment("WordBomb messages. Colors: &-codes. Placeholders: {player} {count} {template} {seconds} {word} {round} {before} {after} {place} {wins} {votes} {date} {time} {action} {x} {y} {z}")
public record Messages(
        @Config.Comment("Prefix added to every chat message (may be empty)")
        String prefix,

        // --- очередь
        String joined,
        String alreadyInGame,
        String gameAlreadyRunning,
        String leftQueue,
        String cannotLeaveDuringGame,
        String notInQueue,
        String notEnoughPlayers,
        String startedManually,
        String leftZoneQueue,
        String tooFarToJoin,

        // --- ход
        String wordNotFit,
        String notInDictionary,
        String wordAlreadyUsed,
        String notYourTurn,
        String turnOf,
        String wordAccepted,
        String newRound,
        String timeUp,
        String quitEliminated,
        String leftZoneEliminated,
        String spectatorTooFar,

        // --- конец
        String winner,
        String gameOver,
        String wordsUsed,

        // --- боссбар
        String bossbarStarting,
        String bossbarTurn,

        // --- словарь: игроки
        String offered,
        String reported,
        String onlyRussianLetters,
        String alreadyInDictionaryOrOffered,
        String notInDictionaryOrReported,
        String offerListHeader,
        String reportListHeader,
        String voteLine,
        String emptyList,
        String topHeader,
        String topLine,

        // --- словарь: админ
        String accepted,
        String blocked,
        String added,
        String removed,
        String notInOffers,
        String notInReports,
        String alreadyInDictionary,
        String notInDictionaryAdmin,
        String searchHeader,
        String searchLine,
        String searchEmpty,

        // --- настройки
        String centerSet,
        String hologramSet,
        String badNumbers,
        String lookAtBlock,
        String reloaded,
        String reloadFailed,
        String configSet,
        String configBadValue,
        String configKeys,
        String configScreenFailed,
        String modScreenFailed,

        // --- общее
        String noPermission,
        String playersOnly,
        String unknownCommand,

        // --- публичный экран
        String screenWaiting,
        String screenCountdown,
        String screenRunning,
        String screenPlayers,
        String screenTimer,
        String screenTimerIdle,

        // --- PlaceholderAPI
        String papiTopLine,
        String papiNoData) {

    public static final Messages DEFAULTS = new Messages(
            "&6[WordBomb] &f",

            "&aВы присоединились к игре!",
            "&cВы уже участвуете в этой игре!",
            "&cИгра уже идёт!",
            "&eВы покинули очередь на игру!",
            "&cНельзя выйти во время активной игры!",
            "&cВы не в игре.",
            "&cНедостаточно игроков! Нужно минимум {count}.",
            "&aИгра запущена вручную.",
            "&eВы вышли из зоны и покинули очередь.",
            "&cПодойдите к арене, чтобы присоединиться к игре.",

            "&cСлово не подходит",
            "&cТакого слова нет в словаре.",
            "&cЭто слово уже было использовано!",
            "&cСейчас не ваш ход.",
            "&eХодит игрок: &6{player}&e. Шаблон: &c{template}",
            "&6{before}&c{template}&6{after}",
            "&bНовый раунд начинается!",
            "&cВремя вышло! &6{player} &cвыбывает!",
            "&6{player} &cвышел с сервера и выбывает!",
            "&6{player} &cпокинул зону и выбывает!",
            "&cВы слишком далеко отошли от центра!",

            "&aПобедитель: &6{player}&a!",
            "&eИгра окончена!",
            "&7Всего было использовано слов: &f{count}",

            "Игра начинается...",
            "&6{player} &f| &a{template} &f| &e{seconds}с",

            "&aСлово успешно предложено!",
            "&aЖалоба на слово отправлена!",
            "&cСлово должно содержать только русские буквы!",
            "&cЭто слово уже есть в словаре или вы уже предложили его!",
            "&cЭтого слова нет в словаре или вы уже жаловались на него!",
            "&6Топ предложений:",
            "&6Топ жалоб:",
            "&e{place}. &f{word} &7- &a{votes}",
            "&7Список пуст.",
            "&6Топ игроков по победам:",
            "&e{place}. &6{player} &7- &a{wins}",

            "&aСлово &f{word} &aпринято в словарь!",
            "&aСлово &f{word} &aзаблокировано и удалено из словаря!",
            "&aСлово &f{word} &aдобавлено в словарь!",
            "&aСлово &f{word} &aудалено из словаря!",
            "&cСлово не найдено в списке предложений!",
            "&cСлово не найдено в списке жалоб!",
            "&cЭто слово уже есть в словаре!",
            "&cЭтого слова нет в словаре!",
            "&6Журнал ({count}):",
            "&e[{date}]&f[{time}]&b[{player}]{action}&6[{word}]",
            "&7Ничего не найдено.",

            "&aЦентр ивента установлен на координаты: &f{x} {y} {z}",
            "&aПозиция голограммы установлена на: &f{x} {y} {z} ({yaw} {pitch})",
            "&cНеверный формат чисел!",
            "&cВы должны смотреть на блок!",
            "&aКонфиг и тексты перечитаны.",
            "&cОшибка перезагрузки: {word}",
            "&aНастройка &f{word} &a= &e{count}",
            "&cНеверное значение для &f{word}&c: {count}",
            "&7Ключи: &f{word}",
            "&cНе удалось открыть экран настроек (см. консоль).",
            "&cНе удалось открыть экран модерации (см. консоль).",

            "&cУ вас нет прав для этой команды!",
            "&cЭта команда только для игроков!",
            "&cНеизвестная команда. Используйте: /wb <join|leave|start|offer|report|offer-list|report-list|top|accept|block|add|remove|search|config|mod|reload>",

            "Ожидание игроков",
            "Старт через {seconds}",
            "Идёт игра · раунд {round}",
            "☻ {count}",
            "⌚ {seconds}",
            "⌚ -",

            "&6{player} &7- &a{wins}",
            "&7Нет данных");
}
