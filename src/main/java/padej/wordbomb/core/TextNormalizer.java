package padej.wordbomb.core;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Нормализация текста игрока: английская раскладка → русская (QWERTY → ЙЦУКЕН), нижний регистр, ё → е.
 * Словарь хранит слова в нормализованной форме, поэтому любое сравнение идёт через {@link #normalize(String)}.
 */
public final class TextNormalizer {

    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>~";
    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбюёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁ";
    private static final Pattern CYRILLIC_WORD = Pattern.compile("[а-яё]+");

    private TextNormalizer() {
    }

    /** Символы английской раскладки заменяются на русские, остальные остаются как есть. */
    public static String convertLayout(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = EN.indexOf(c);
            sb.append(idx >= 0 ? RU.charAt(idx) : c);
        }
        return sb.toString();
    }

    /** Нижний регистр и ё → е. Так хранятся слова в словаре и в {@code usedWords}. */
    public static String normalize(String word) {
        if (word == null) return null;
        return word.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Только русские буквы (ё допускается). Проверяется строка в нижнем регистре. */
    public static boolean isCyrillicWord(String lower) {
        return lower != null && !lower.isEmpty() && CYRILLIC_WORD.matcher(lower).matches();
    }

    /**
     * Полный путь от текста из чата до слова для словаря: раскладка, обрезка пробелов, регистр,
     * проверка «только русские буквы», ё → е. Пустой результат - текст не является русским словом.
     */
    public static Optional<String> toWord(String raw) {
        if (raw == null) return Optional.empty();
        String lower = convertLayout(raw.trim()).toLowerCase(Locale.ROOT);
        if (!isCyrillicWord(lower)) return Optional.empty();
        return Optional.of(lower.replace('ё', 'е'));
    }
}
