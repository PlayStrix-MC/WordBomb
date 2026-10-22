package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteRepoTest {

    private Database db;
    private SuggestionRepo suggestions;
    private ReportRepo reports;
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        suggestions = new SuggestionRepo(db);
        reports = new ReportRepo(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void onePlayerOneVotePerWord() {
        assertTrue(suggestions.vote("Слово", p1));
        assertFalse(suggestions.vote("слово", p1));
        assertTrue(suggestions.vote("слово", p2));
        assertEquals(2, suggestions.votes("СЛОВО"));
        assertTrue(suggestions.hasVoted("слово", p1));
        assertTrue(suggestions.contains("слово"));
        assertFalse(suggestions.contains("другое"));
    }

    @Test
    void tablesAreIndependent() {
        suggestions.vote("слово", p1);
        assertFalse(reports.contains("слово"));
        reports.vote("слово", p1);
        assertEquals(1, reports.votes("слово"));
        assertTrue(reports.removeWord("слово"));
        assertFalse(reports.removeWord("слово"));
        assertTrue(suggestions.contains("слово"));
    }

    @Test
    void topIsOrderedByVotesThenWord() {
        suggestions.vote("бета", p1);
        suggestions.vote("бета", p2);
        suggestions.vote("альфа", p1);
        suggestions.vote("гамма", p1);
        List<VoteRepo.Entry> top = suggestions.top(10);
        assertEquals(3, top.size());
        assertEquals(new VoteRepo.Entry("бета", 2), top.get(0));
        assertEquals("альфа", top.get(1).word());
        assertEquals("гамма", top.get(2).word());
        assertEquals(2, suggestions.top(2).size());
    }
}
