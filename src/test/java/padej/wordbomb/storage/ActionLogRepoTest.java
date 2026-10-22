package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionLogRepoTest {

    private Database db;
    private ActionLogRepo log;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        log = new ActionLogRepo(db, ZoneOffset.UTC);
        log.logAt(LocalDateTime.of(2025, 5, 24, 18, 45, 53), "HAPSIEL_LOVE", ActionLogRepo.ACCEPT, "имя");
        log.logAt(LocalDateTime.of(2025, 5, 24, 19, 0, 1), "Padej_", ActionLogRepo.BLOCK, "плохое");
        log.logAt(LocalDateTime.of(2025, 6, 1, 9, 30, 0), "Padej_", ActionLogRepo.ADD, "новое");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void searchWithoutFiltersReturnsEverythingInOrder() {
        List<ActionLogRepo.Entry> all = log.search(null, null, null, null, null);
        assertEquals(3, all.size());
        assertEquals("2025-05-24", all.get(0).date());
        assertEquals("18:45:53", all.get(0).time());
        assertEquals("имя", all.get(0).word());
        assertEquals(3, log.count());
    }

    @Test
    void filtersAreSubstringsAndCaseInsensitive() {
        assertEquals(2, log.search("2025-05", null, null, null, null).size());
        assertEquals(2, log.search(null, null, "padej", null, null).size());
        assertEquals(1, log.search(null, null, "padej", "block", null).size());
        assertEquals(1, log.search(null, "18:", null, null, null).size());
        assertEquals(1, log.search(null, null, null, null, "нов").size());
        assertEquals(0, log.search("2024", null, null, null, null).size());
    }

    @Test
    void distinctLists() {
        assertEquals(List.of("2025-05-24", "2025-06-01"), log.dates());
        assertEquals(List.of("HAPSIEL_LOVE", "Padej_"), log.players());
        assertTrue(log.actions().containsAll(List.of("accept", "block", "add")));
    }

    @Test
    void logNowUsesCurrentTime() {
        log.log("admin", ActionLogRepo.REMOVE, "слово");
        ActionLogRepo.Entry last = log.search(null, null, "admin", null, null).get(0);
        assertEquals(ActionLogRepo.REMOVE, last.action());
        assertEquals(10, last.date().length());
        assertEquals(8, last.time().length());
    }
}
