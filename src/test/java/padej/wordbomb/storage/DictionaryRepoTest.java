package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryRepoTest {

    private Database db;
    private DictionaryRepo repo;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        repo = new DictionaryRepo(db, new Random(1));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void addLookupRemove() {
        assertTrue(repo.add("Ёжик"));
        assertFalse(repo.add("ежик"), "duplicate after normalization");
        assertTrue(repo.contains("ёжик"));
        assertTrue(repo.contains("ЕЖИК"));
        assertFalse(repo.contains("еж"));
        assertEquals(1, repo.count());
        assertTrue(repo.remove("Ёжик"));
        assertFalse(repo.remove("ежик"));
        assertFalse(repo.contains("ежик"));
    }

    @Test
    void emptyDictionaryHasNoRandomWord() {
        assertNull(repo.randomWord());
        assertFalse(repo.contains(""));
        assertFalse(repo.contains(null));
    }

    @Test
    void randomWordCoversWholeDictionary() {
        List<String> words = List.of("один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять");
        assertEquals(10, repo.addAll(words));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) seen.add(repo.randomWord());
        assertEquals(Set.copyOf(words), seen, "every word must be reachable");
    }

    @Test
    void randomWordSurvivesHolesAfterRemoval() {
        repo.addAll(List.of("один", "два", "три"));
        repo.remove("три");
        repo.remove("два");
        for (int i = 0; i < 50; i++) assertEquals("один", repo.randomWord());
    }

    @Test
    void addAllSkipsGarbageAndDuplicates() {
        long added = repo.addAll(List.of("слово", "Слово", "", "word", "два слова", "ещё"));
        assertEquals(2, added);
        assertTrue(repo.contains("еще"));
    }

    @Test
    void randomContainingSkipsExcludedWords() {
        repo.addAll(List.of("привет", "ответ", "свет", "ветер", "дом"));
        List<String> hints = repo.randomContaining("вет", Set.of("привет", "свет"), 3);
        assertEquals(2, hints.size());
        assertTrue(hints.containsAll(List.of("ответ", "ветер")), hints.toString());
        assertEquals(1, repo.randomContaining("вет", Set.of(), 1).size());
        assertTrue(repo.randomContaining("юю", Set.of(), 3).isEmpty());
        assertTrue(repo.randomContaining("вет", Set.of(), 0).isEmpty());
    }
}
