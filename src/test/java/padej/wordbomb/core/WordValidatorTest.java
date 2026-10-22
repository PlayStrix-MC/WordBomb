package padej.wordbomb.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordValidatorTest {

    private static final Lexicon DICT = new Lexicon() {
        private final Set<String> words = Set.of("привет", "ежик", "ветер");

        @Override
        public boolean contains(String w) {
            return words.contains(w);
        }

        @Override
        public String randomWord() {
            return "привет";
        }
    };

    @Test
    void acceptsWordFromEnglishLayout() {
        WordValidator.Result r = WordValidator.validate("ghbdtn", "ив", DICT, Set.of());
        assertEquals(WordValidator.Verdict.OK, r.verdict());
        assertEquals("привет", r.word());
    }

    @Test
    void rejectsNonCyrillic() {
        assertEquals(WordValidator.Verdict.NOT_FIT, WordValidator.validate("hello1", "ив", DICT, Set.of()).verdict());
        assertEquals(WordValidator.Verdict.NOT_FIT, WordValidator.validate("при вет", "ив", DICT, Set.of()).verdict());
    }

    @Test
    void acceptsYoAndNormalizesIt() {
        WordValidator.Result r = WordValidator.validate("Ёжик", "еж", DICT, Set.of());
        assertEquals(WordValidator.Verdict.OK, r.verdict());
        assertEquals("ежик", r.word());
    }

    @Test
    void templateMustBeInsideWord() {
        assertEquals(WordValidator.Verdict.NOT_FIT, WordValidator.validate("привет", "ка", DICT, Set.of()).verdict());
        assertEquals(WordValidator.Verdict.OK, WordValidator.validate("привет", "ЕТ", DICT, Set.of()).verdict());
        assertTrue(WordValidator.fitsTemplate("ветер", "ёт".replace('ё', 'е')));
        assertFalse(WordValidator.fitsTemplate(null, "ив"));
    }

    @Test
    void unknownWordIsRejected() {
        assertEquals(WordValidator.Verdict.NOT_IN_DICTIONARY, WordValidator.validate("приветик", "ив", DICT, Set.of()).verdict());
    }

    @Test
    void usedWordIsRejected() {
        assertEquals(WordValidator.Verdict.ALREADY_USED, WordValidator.validate("привет", "ив", DICT, Set.of("привет")).verdict());
    }

    @Test
    void emptyTemplateFitsAnything() {
        assertEquals(WordValidator.Verdict.OK, WordValidator.validate("привет", "", DICT, null).verdict());
        assertEquals(WordValidator.Verdict.OK, WordValidator.validate("привет", null, DICT, null).verdict());
    }
}
