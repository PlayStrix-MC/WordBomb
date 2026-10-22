package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationTest {

    private Database db;
    private DictionaryRepo dictionary;
    private SuggestionRepo suggestions;
    private ReportRepo reports;
    private ActionLogRepo log;
    private Moderation moderation;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        dictionary = new DictionaryRepo(db);
        suggestions = new SuggestionRepo(db);
        reports = new ReportRepo(db);
        log = new ActionLogRepo(db);
        moderation = new Moderation(db, suggestions, reports, log);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void acceptMovesSuggestionIntoDictionaryAndLogs() {
        assertEquals(Moderation.Result.NOT_IN_LIST, moderation.accept("слово", "admin"));
        suggestions.vote("Слово", player);
        assertEquals(Moderation.Result.OK, moderation.accept("слово", "admin"));
        assertTrue(dictionary.contains("слово"));
        assertFalse(suggestions.contains("слово"));
        ActionLogRepo.Entry e = log.search(null, null, null, ActionLogRepo.ACCEPT, null).get(0);
        assertEquals("admin", e.player());
        assertEquals("слово", e.word());
        // уже в словаре, но предложение висит: убирается, ответ - «уже есть»
        suggestions.vote("слово", player);
        assertEquals(Moderation.Result.ALREADY_IN_DICTIONARY, moderation.accept("слово", "admin"));
        assertFalse(suggestions.contains("слово"));
    }

    @Test
    void blockRemovesReportedWord() {
        dictionary.add("плохое");
        assertEquals(Moderation.Result.NOT_IN_LIST, moderation.block("плохое", "admin"));
        reports.vote("плохое", player);
        assertEquals(Moderation.Result.OK, moderation.block("плохое", "admin"));
        assertFalse(dictionary.contains("плохое"));
        assertFalse(reports.contains("плохое"));
        assertEquals(1, log.search(null, null, null, ActionLogRepo.BLOCK, "плохое").size());
    }

    @Test
    void addAndRemoveDirectly() {
        assertEquals(Moderation.Result.OK, moderation.add("новое", "admin"));
        assertEquals(Moderation.Result.ALREADY_IN_DICTIONARY, moderation.add("НОВОЕ", "admin"));
        assertEquals(Moderation.Result.OK, moderation.remove("новое", "admin"));
        assertEquals(Moderation.Result.NOT_IN_DICTIONARY, moderation.remove("новое", "admin"));
        assertEquals(2, log.count());
    }

    @Test
    void rejectsNonRussianWords() {
        assertEquals(Moderation.Result.INVALID_WORD, moderation.add("word", "admin"));
        assertEquals(Moderation.Result.INVALID_WORD, moderation.apply(Moderation.Kind.REMOVE, "", "admin"));
        assertEquals(0, log.count());
    }

    @Test
    void rejectAndDismissDropVotesWithoutTouchingDictionary() {
        assertEquals(Moderation.Result.NOT_IN_LIST, moderation.reject("слово", "admin"));
        suggestions.vote("слово", player);
        assertEquals(Moderation.Result.OK, moderation.reject("слово", "admin"));
        assertFalse(suggestions.contains("слово"));
        assertFalse(dictionary.contains("слово"));

        dictionary.add("спорное");
        assertEquals(Moderation.Result.NOT_IN_LIST, moderation.dismiss("спорное", "admin"));
        reports.vote("спорное", player);
        assertEquals(Moderation.Result.OK, moderation.apply(Moderation.Kind.DISMISS, "спорное", "admin"));
        assertFalse(reports.contains("спорное"));
        assertTrue(dictionary.contains("спорное"));
        assertEquals(1, log.search(null, null, null, ActionLogRepo.REJECT, null).size());
        assertEquals(1, log.search(null, null, null, ActionLogRepo.DISMISS, null).size());
    }
}
