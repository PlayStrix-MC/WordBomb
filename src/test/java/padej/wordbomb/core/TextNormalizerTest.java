package padej.wordbomb.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNormalizerTest {

    @Test
    void convertsEnglishLayoutToRussian() {
        assertEquals("привет", TextNormalizer.convertLayout("ghbdtn"));
        assertEquals("ПРИВЕТ", TextNormalizer.convertLayout("GHBDTN"));
        assertEquals("ёж", TextNormalizer.convertLayout("`;"));
        assertEquals("уже русское", TextNormalizer.convertLayout("уже русское"));
    }

    @Test
    void normalizeLowersAndReplacesYo() {
        assertEquals("ежик", TextNormalizer.normalize("Ёжик"));
        assertEquals("елка", TextNormalizer.normalize("  ЁЛКА "));
    }

    @Test
    void cyrillicCheckAcceptsYoAndRejectsOthers() {
        assertTrue(TextNormalizer.isCyrillicWord("ёжик"));
        assertTrue(TextNormalizer.isCyrillicWord("слово"));
        assertFalse(TextNormalizer.isCyrillicWord("слово1"));
        assertFalse(TextNormalizer.isCyrillicWord("hello"));
        assertFalse(TextNormalizer.isCyrillicWord("два слова"));
        assertFalse(TextNormalizer.isCyrillicWord(""));
        assertFalse(TextNormalizer.isCyrillicWord(null));
    }

    @Test
    void toWordRunsTheWholePipeline() {
        assertEquals(Optional.of("привет"), TextNormalizer.toWord("GHBDTN"));
        assertEquals(Optional.of("ежик"), TextNormalizer.toWord(" Ёжик "));
        assertEquals(Optional.empty(), TextNormalizer.toWord("hi there"));
        assertEquals(Optional.empty(), TextNormalizer.toWord("слово!"));
        assertEquals(Optional.empty(), TextNormalizer.toWord(null));
    }
}
