package padej.wordbomb.core;

/**
 * Словарь, каким его видит ядро: проверка слова и случайное слово для шаблона.
 * Слова - в нормализованной форме ({@link TextNormalizer#normalize(String)}).
 */
public interface Lexicon {

    /** Есть ли нормализованное слово в словаре. Может вызываться из любого потока. */
    boolean contains(String normalizedWord);

    /** Случайное слово из всего словаря или {@code null}, если словарь пуст. */
    String randomWord();
}
