package padej.wordbomb.storage;

import padej.wordbomb.core.TextNormalizer;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Модерация словаря: каждая операция - одна транзакция (словарь + голоса + журнал).
 * Результат говорит, что пошло не так, чтобы команда показала игроку нужный текст.
 */
public final class Moderation {

    public enum Result {
        OK,
        /** Слово не из русских букв. */
        INVALID_WORD,
        /** Слова нет в списке предложений / жалоб. */
        NOT_IN_LIST,
        /** Слово уже есть в словаре. */
        ALREADY_IN_DICTIONARY,
        /** Слова нет в словаре. */
        NOT_IN_DICTIONARY
    }

    /** Вид операции - для команды, которая выбирает текст ответа. */
    public enum Kind { ACCEPT, BLOCK, ADD, REMOVE, REJECT, DISMISS }

    private final Database db;
    private final SuggestionRepo suggestions;
    private final ReportRepo reports;
    private final ActionLogRepo log;
    private final ZoneId zone;

    public Moderation(Database db, SuggestionRepo suggestions, ReportRepo reports, ActionLogRepo log) {
        this(db, suggestions, reports, log, ZoneId.systemDefault());
    }

    public Moderation(Database db, SuggestionRepo suggestions, ReportRepo reports, ActionLogRepo log, ZoneId zone) {
        this.db = db;
        this.suggestions = suggestions;
        this.reports = reports;
        this.log = log;
        this.zone = zone;
    }

    public Result apply(Kind kind, String word, String admin) {
        return switch (kind) {
            case ACCEPT -> accept(word, admin);
            case BLOCK -> block(word, admin);
            case ADD -> add(word, admin);
            case REMOVE -> remove(word, admin);
            case REJECT -> reject(word, admin);
            case DISMISS -> dismiss(word, admin);
        };
    }

    /** {@code /wb reject}: предложение отклонено - голоса сняты, в словарь слово не попадает. */
    public Result reject(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (!suggestions.deleteWord(c, w)) return Result.NOT_IN_LIST;
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.REJECT, w);
            return Result.OK;
        });
    }

    /** {@code /wb dismiss}: жалоба отклонена - жалобы сняты, слово остаётся в словаре. */
    public Result dismiss(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (!reports.deleteWord(c, w)) return Result.NOT_IN_LIST;
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.DISMISS, w);
            return Result.OK;
        });
    }

    /** {@code /wb accept}: слово из предложений попадает в словарь. */
    public Result accept(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (suggestions.countVotes(c, w) == 0) return Result.NOT_IN_LIST;
            boolean inserted = DictionaryRepo.insert(c, w);
            suggestions.deleteWord(c, w);
            if (!inserted) return Result.ALREADY_IN_DICTIONARY;
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.ACCEPT, w);
            return Result.OK;
        });
    }

    /** {@code /wb block}: слово из жалоб удаляется из словаря. */
    public Result block(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (reports.countVotes(c, w) == 0) return Result.NOT_IN_LIST;
            boolean deleted = DictionaryRepo.delete(c, w);
            reports.deleteWord(c, w);
            if (!deleted) return Result.NOT_IN_DICTIONARY;
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.BLOCK, w);
            return Result.OK;
        });
    }

    /** {@code /wb add}: прямое добавление. */
    public Result add(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (!DictionaryRepo.insert(c, w)) return Result.ALREADY_IN_DICTIONARY;
            suggestions.deleteWord(c, w);
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.ADD, w);
            return Result.OK;
        });
    }

    /** {@code /wb remove}: прямое удаление. */
    public Result remove(String word, String admin) {
        String w = normalizedOrNull(word);
        if (w == null) return Result.INVALID_WORD;
        return db.write(c -> {
            if (!DictionaryRepo.delete(c, w)) return Result.NOT_IN_DICTIONARY;
            reports.deleteWord(c, w);
            log.insert(c, LocalDateTime.now(zone), admin, ActionLogRepo.REMOVE, w);
            return Result.OK;
        });
    }

    /** Нормализованное слово из русских букв или {@code null}. */
    public static String normalizedOrNull(String word) {
        if (word == null) return null;
        String w = TextNormalizer.normalize(word);
        return TextNormalizer.isCyrillicWord(w) ? w : null;
    }
}
