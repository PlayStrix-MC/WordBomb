package padej.wordbomb.core;

import java.util.Optional;
import java.util.Set;

/**
 * Проверка слова игрока по шагам: раскладка → только кириллица → содержит шаблон → есть в словаре →
 * не использовано в этой игре. Шаги без словаря ({@link #prepare(String)}, {@link #fitsTemplate})
 * дешёвые и используются отдельно: в асинхронном потоке чата до обращения к словарю.
 */
public final class WordValidator {

    public enum Verdict {
        /** Слово подходит. */
        OK,
        /** Не русское слово или не содержит шаблон. */
        NOT_FIT,
        /** Нет в словаре. */
        NOT_IN_DICTIONARY,
        /** Уже было в этой игре. */
        ALREADY_USED
    }

    /** Результат: вердикт и нормализованное слово (пустое, если текст не является словом). */
    public record Result(Verdict verdict, String word) {
        public boolean ok() {
            return verdict == Verdict.OK;
        }
    }

    private WordValidator() {
    }

    /** Текст из чата → нормализованное слово (раскладка, регистр, ё → е) или пусто. */
    public static Optional<String> prepare(String raw) {
        return TextNormalizer.toWord(raw);
    }

    /** Содержит ли нормализованное слово шаблон (без учёта регистра и ё). */
    public static boolean fitsTemplate(String word, String template) {
        if (word == null) return false;
        if (template == null || template.isEmpty()) return true;
        return word.contains(TextNormalizer.normalize(template));
    }

    /** Полная проверка. {@code used} может быть {@code null}. */
    public static Result validate(String raw, String template, Lexicon dictionary, Set<String> used) {
        Optional<String> prepared = prepare(raw);
        if (prepared.isEmpty()) return new Result(Verdict.NOT_FIT, "");
        String word = prepared.get();
        if (!fitsTemplate(word, template)) return new Result(Verdict.NOT_FIT, word);
        if (dictionary != null && !dictionary.contains(word)) return new Result(Verdict.NOT_IN_DICTIONARY, word);
        if (used != null && used.contains(word)) return new Result(Verdict.ALREADY_USED, word);
        return new Result(Verdict.OK, word);
    }
}
