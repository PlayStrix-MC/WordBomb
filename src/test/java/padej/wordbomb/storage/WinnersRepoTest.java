package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinnersRepoTest {

    private Database db;
    private WinnersRepo repo;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        repo = new WinnersRepo(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void winsAreCountedByUuidAndNameFollows() {
        UUID id = UUID.randomUUID();
        assertEquals(0, repo.wins(id));
        repo.addWin(id, "OldName");
        repo.addWin(id, "NewName");
        assertEquals(2, repo.wins(id));
        List<WinnersRepo.Entry> top = repo.top(10);
        assertEquals(1, top.size());
        assertEquals("NewName", top.get(0).name());
        assertEquals(id, top.get(0).player());
    }

    @Test
    void topIsSortedByWins() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        repo.addWins(a, "A", 3);
        repo.addWins(b, "B", 5);
        repo.addWins(c, "C", 1);
        repo.addWins(c, "C", 0);
        List<WinnersRepo.Entry> top = repo.top(2);
        assertEquals(2, top.size());
        assertEquals("B", top.get(0).name());
        assertEquals(5, top.get(0).wins());
        assertEquals("A", top.get(1).name());
    }
}
