package padej.wordbomb.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateGeneratorTest {

    private static Lexicon lexicon(List<String> words, Random random) {
        return new Lexicon() {
            @Override
            public boolean contains(String w) {
                return words.contains(w);
            }

            @Override
            public String randomWord() {
                return words.isEmpty() ? null : words.get(random.nextInt(words.size()));
            }
        };
    }

    @Test
    void templateIsPrefixOrSuffixOfTwoOrThreeLetters() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 20, 50);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of("абвгде"), new Random(1)), rules, new Random(7));
        for (int i = 0; i < 200; i++) {
            String t = g.next();
            assertNotNull(t);
            assertTrue(t.length() == 2 || t.length() == 3, t);
            assertTrue("абвгде".startsWith(t) || "абвгде".endsWith(t), t);
        }
    }

    @Test
    void twoLetterChanceIsRespectedStatistically() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 0, 1);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of("абвгде"), new Random(1)), rules, new Random(42));
        int two = 0, n = 5000;
        for (int i = 0; i < n; i++) if (g.next().length() == 2) two++;
        double share = (double) two / n;
        assertTrue(share > 0.87 && share < 0.93, "two-letter share " + share);
    }

    @Test
    void shortWordsGiveThemselves() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 20, 50);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of("я"), new Random(1)), rules, new Random(3));
        assertEquals("я", g.next());
        g = new TemplateGenerator(lexicon(List.of("да"), new Random(1)), rules, new Random(3));
        assertEquals("да", g.next());
    }

    @Test
    void recentTemplatesAreAvoided() {
        // 10 слов по 2 буквы → 10 разных шаблонов; история 20 не даёт повторов, пока есть варианты
        List<String> words = List.of("аа", "бб", "вв", "гг", "дд", "ее", "жж", "зз", "ии", "кк");
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 20, 500);
        TemplateGenerator g = new TemplateGenerator(lexicon(words, new Random(5)), rules, new Random(9));
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String t = g.next();
            assertFalse(seen.contains(t), "repeated " + t + " in " + seen);
            seen.add(t);
        }
        assertEquals(10, Set.copyOf(seen).size());
        assertEquals(seen, g.recent());
    }

    @Test
    void historyIsBounded() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 3, 50);
        List<String> words = List.of("аа", "бб", "вв", "гг", "дд");
        TemplateGenerator g = new TemplateGenerator(lexicon(words, new Random(5)), rules, new Random(9));
        for (int i = 0; i < 20; i++) g.next();
        assertEquals(3, g.recent().size());
    }

    @Test
    void givesUpAfterAttemptsAndReturnsATemplateAnyway() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 1.0, 20, 5);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of("аа"), new Random(1)), rules, new Random(1));
        assertEquals("аа", g.next());
        assertEquals("аа", g.next()); // единственный вариант: после 5 попыток принимается как есть
    }

    @Test
    void emptyLexiconGivesNull() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 20, 50);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of(), new Random(1)), rules, new Random(1));
        assertNull(g.next());
    }

    @Test
    void resetClearsHistory() {
        Rules rules = new Rules(2, 20, 20, 5, 5, 0.9, 20, 50);
        TemplateGenerator g = new TemplateGenerator(lexicon(List.of("абв"), new Random(1)), rules, new Random(1));
        g.next();
        assertEquals(1, g.recent().size());
        g.reset();
        assertTrue(g.recent().isEmpty());
    }
}
