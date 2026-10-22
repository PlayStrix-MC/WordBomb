package padej.wordbomb.core;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Шаблон хода: 2 буквы (с вероятностью {@link Rules#twoLetterChance()}) или 3, префикс или суффикс
 * случайного слова словаря (50/50). Последние {@link Rules#templateHistory()} шаблонов не повторяются:
 * делается до {@link Rules#templateAttempts()} попыток, после чего шаблон берётся как есть.
 */
public final class TemplateGenerator {

    private final Lexicon dictionary;
    private final Rules rules;
    private final Random random;
    private final Deque<String> recent = new ArrayDeque<>();

    public TemplateGenerator(Lexicon dictionary, Rules rules, Random random) {
        this.dictionary = dictionary;
        this.rules = rules;
        this.random = random;
    }

    /** Следующий шаблон или {@code null}, если словарь пуст. */
    public String next() {
        String template = null;
        for (int attempt = 0; attempt < rules.templateAttempts(); attempt++) {
            String word = dictionary.randomWord();
            if (word == null) break;
            word = TextNormalizer.normalize(word);
            if (word.isEmpty()) continue;
            template = templateOf(word);
            if (!recent.contains(template)) break;
        }
        if (template != null) remember(template);
        return template;
    }

    /** Шаблон из конкретного слова (без антиповтора). */
    String templateOf(String word) {
        if (word.length() < 2) return word;
        int size = random.nextDouble() < rules.twoLetterChance() ? 2 : 3;
        size = Math.min(size, word.length());
        return random.nextBoolean()
                ? word.substring(0, size)
                : word.substring(word.length() - size);
    }

    private void remember(String template) {
        if (rules.templateHistory() <= 0) return;
        recent.addLast(template);
        while (recent.size() > rules.templateHistory()) recent.removeFirst();
    }

    /** Последние выданные шаблоны (старые первыми). */
    public List<String> recent() {
        return List.copyOf(recent);
    }

    public void reset() {
        recent.clear();
    }

    /** Для тестов и отладки: заполнить историю. */
    void seedRecent(Collection<String> templates) {
        templates.forEach(this::remember);
    }
}
