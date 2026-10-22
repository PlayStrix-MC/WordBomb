package padej.wordbomb.storage;

import padej.wordbomb.core.TextNormalizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перенос данных старого плагина (txt-файлы из {@code plugins/word_bomb/}) в SQLite при первом запуске.
 * Файлы остаются на месте как бэкап. Повторный запуск ничего не делает: в {@code meta} пишется
 * {@code legacy_imported=1}.
 * <p>
 * Форматы: {@code dictionary.txt} - слово на строку; {@code offers.txt}/{@code reports.txt} -
 * {@code слово:uuid1,uuid2}; {@code winners.txt} - {@code ник:число}; {@code actions.log} -
 * {@code [yyyy-MM-dd][HH:mm:ss][ник][действие][слово]}.
 */
public final class LegacyImporter {

    public static final String META_KEY = "legacy_imported";

    /** Что было перенесено. */
    public record Report(long dictionaryWords, int suggestions, int reports, int winners, int actions, List<String> warnings) {
        public boolean empty() {
            return dictionaryWords == 0 && suggestions == 0 && reports == 0 && winners == 0 && actions == 0;
        }
    }

    private static final Pattern LOG_LINE = Pattern.compile("\\[([^\\]]*)]\\[([^\\]]*)]\\[([^\\]]*)]\\[([^\\]]*)]\\[([^\\]]*)]");

    private final Database db;
    private final DictionaryRepo dictionary;
    private final SuggestionRepo suggestions;
    private final ReportRepo reports;
    private final WinnersRepo winners;
    private final ActionLogRepo log;
    private final Function<String, UUID> uuidByName;
    private final Consumer<String> logger;

    public LegacyImporter(Database db, DictionaryRepo dictionary, SuggestionRepo suggestions, ReportRepo reports,
                          WinnersRepo winners, ActionLogRepo log, Function<String, UUID> uuidByName, Consumer<String> logger) {
        this.db = db;
        this.dictionary = dictionary;
        this.suggestions = suggestions;
        this.reports = reports;
        this.winners = winners;
        this.log = log;
        this.uuidByName = uuidByName;
        this.logger = logger;
    }

    public boolean alreadyImported() {
        return "1".equals(db.meta(META_KEY));
    }

    /** Есть ли в папке хоть один файл старого формата. */
    public static boolean hasLegacyFiles(Path dir) {
        return dir != null && Files.isDirectory(dir) && (Files.isRegularFile(dir.resolve("dictionary.txt"))
                || Files.isRegularFile(dir.resolve("offers.txt"))
                || Files.isRegularFile(dir.resolve("reports.txt"))
                || Files.isRegularFile(dir.resolve("winners.txt"))
                || Files.isRegularFile(dir.resolve("actions.log")));
    }

    /**
     * Импорт из папки. Возвращает отчёт; {@code null}, если импорт уже выполнялся или файлов нет.
     * Словарь грузится первым и самым тяжёлым (миллионы строк) - вызывать при старте, до открытия игры.
     */
    public Report run(Path dir) throws IOException {
        if (alreadyImported() || !hasLegacyFiles(dir)) return null;
        List<String> warnings = new ArrayList<>();

        long words = 0;
        Path dict = dir.resolve("dictionary.txt");
        if (Files.isRegularFile(dict)) {
            logger.accept("Импорт словаря из " + dict + " ...");
            try (BufferedReader reader = Files.newBufferedReader(dict, StandardCharsets.UTF_8)) {
                words = dictionary.addAll(() -> reader.lines().iterator());
            }
            logger.accept("Словарь: добавлено " + words + " слов");
        }

        int offers = importVotes(dir.resolve("offers.txt"), suggestions, warnings);
        int complaints = importVotes(dir.resolve("reports.txt"), reports, warnings);
        int wins = importWinners(dir.resolve("winners.txt"), warnings);
        int actions = importLog(dir.resolve("actions.log"), warnings);

        db.setMeta(META_KEY, "1");
        warnings.forEach(logger);
        return new Report(words, offers, complaints, wins, actions, List.copyOf(warnings));
    }

    private int importVotes(Path file, VoteRepo repo, List<String> warnings) throws IOException {
        if (!Files.isRegularFile(file)) return 0;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return db.write(c -> {
            int count = 0;
            long now = System.currentTimeMillis();
            for (String line : lines) {
                String s = line.strip();
                if (s.isEmpty()) continue;
                int colon = s.indexOf(':');
                if (colon < 0) {
                    warnings.add(file.getFileName() + ": пропущена строка без ':' - " + s);
                    continue;
                }
                String word = TextNormalizer.normalize(s.substring(0, colon));
                if (!TextNormalizer.isCyrillicWord(word)) {
                    warnings.add(file.getFileName() + ": пропущено слово - " + s);
                    continue;
                }
                for (String id : s.substring(colon + 1).split(",")) {
                    String t = id.strip();
                    if (t.isEmpty()) continue;
                    try {
                        if (repo.insertVote(c, word, UUID.fromString(t), now)) count++;
                    } catch (IllegalArgumentException e) {
                        warnings.add(file.getFileName() + ": не UUID - " + t);
                    }
                }
            }
            return count;
        });
    }

    private int importWinners(Path file, List<String> warnings) throws IOException {
        if (!Files.isRegularFile(file)) return 0;
        int count = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (s.isEmpty()) continue;
            int colon = s.lastIndexOf(':');
            if (colon < 0) {
                warnings.add("winners.txt: пропущена строка - " + s);
                continue;
            }
            String name = s.substring(0, colon).strip();
            int wins;
            try {
                wins = Integer.parseInt(s.substring(colon + 1).strip());
            } catch (NumberFormatException e) {
                warnings.add("winners.txt: не число побед - " + s);
                continue;
            }
            UUID uuid = uuidByName.apply(name);
            if (uuid == null) {
                warnings.add("winners.txt: не удалось определить UUID для " + name + ", запись пропущена");
                continue;
            }
            winners.addWins(uuid, name, wins);
            count++;
        }
        return count;
    }

    private int importLog(Path file, List<String> warnings) throws IOException {
        if (!Files.isRegularFile(file)) return 0;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return db.write(c -> {
            int count = 0;
            for (String line : lines) {
                String s = line.strip();
                if (s.isEmpty()) continue;
                Matcher m = LOG_LINE.matcher(s);
                if (!m.find()) {
                    warnings.add("actions.log: пропущена строка - " + s);
                    continue;
                }
                LocalDateTime at;
                try {
                    at = ActionLogRepo.parse(m.group(1), m.group(2));
                } catch (RuntimeException e) {
                    warnings.add("actions.log: не разобраны дата/время - " + s);
                    continue;
                }
                log.insert(c, at, m.group(3), m.group(4), TextNormalizer.normalize(m.group(5)));
                count++;
            }
            return count;
        });
    }
}
