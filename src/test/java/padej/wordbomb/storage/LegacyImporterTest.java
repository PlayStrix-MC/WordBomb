package padej.wordbomb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyImporterTest {

    @TempDir
    Path dir;

    private Database db;
    private DictionaryRepo dictionary;
    private SuggestionRepo suggestions;
    private ReportRepo reports;
    private WinnersRepo winners;
    private ActionLogRepo log;
    private final List<String> logged = new ArrayList<>();
    private final UUID voter1 = UUID.randomUUID();
    private final UUID voter2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        dictionary = new DictionaryRepo(db);
        suggestions = new SuggestionRepo(db);
        reports = new ReportRepo(db);
        winners = new WinnersRepo(db);
        log = new ActionLogRepo(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private LegacyImporter importer() {
        return new LegacyImporter(db, dictionary, suggestions, reports, winners, log,
                name -> UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), logged::add);
    }

    private void writeAll() throws IOException {
        Files.writeString(dir.resolve("dictionary.txt"), "Ёжась\nЁжат\nежат\nслово\n\nbad\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("offers.txt"), "новое:" + voter1 + "," + voter2 + "\nпустое:\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("reports.txt"), "слово:" + voter1 + "\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("winners.txt"), "Padej_:3\nHAPSIEL_LOVE:1\nbroken\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("actions.log"),
                "[2025-05-24][18:45:53][HAPSIEL_LOVE][accept][имя]\n[2025-05-25][10:00:00][Padej_][block][Ёж]\nmalformed\n",
                StandardCharsets.UTF_8);
    }

    @Test
    void importsAllFourFormats() throws IOException {
        writeAll();
        assertTrue(LegacyImporter.hasLegacyFiles(dir));
        LegacyImporter.Report report = importer().run(dir);
        assertNotNull(report);
        assertEquals(3, report.dictionaryWords(), "ёжась, ежат (дубликат ё/е), слово; bad пропущен");
        assertTrue(dictionary.contains("ежась"));
        assertTrue(dictionary.contains("Ёжат"));
        assertFalse(dictionary.contains("bad"));

        assertEquals(2, report.suggestions());
        assertEquals(2, suggestions.votes("новое"));
        assertFalse(suggestions.contains("пустое"));
        assertEquals(1, report.reports());
        assertTrue(reports.hasVoted("слово", voter1));

        assertEquals(2, report.winners());
        assertEquals(3, winners.wins(UUID.nameUUIDFromBytes("OfflinePlayer:Padej_".getBytes(StandardCharsets.UTF_8))));
        assertEquals("Padej_", winners.top(1).get(0).name());

        assertEquals(2, report.actions());
        List<ActionLogRepo.Entry> entries = log.search(null, null, null, null, null);
        assertEquals("2025-05-24", entries.get(0).date());
        assertEquals("18:45:53", entries.get(0).time());
        assertEquals("еж", entries.get(1).word(), "слово журнала нормализуется");

        assertFalse(report.warnings().isEmpty(), "broken lines are reported");
        assertTrue(Files.exists(dir.resolve("dictionary.txt")), "files stay as backup");
    }

    @Test
    void isIdempotent() throws IOException {
        writeAll();
        assertNotNull(importer().run(dir));
        assertTrue(importer().alreadyImported());
        assertNull(importer().run(dir));
        assertEquals(3, dictionary.count());
        assertEquals(2, log.count());
    }

    @Test
    void nothingToImport() throws IOException {
        assertFalse(LegacyImporter.hasLegacyFiles(dir));
        assertNull(importer().run(dir));
        assertFalse(importer().alreadyImported());
        Files.writeString(dir.resolve("winners.txt"), "Only:2\n", StandardCharsets.UTF_8);
        LegacyImporter.Report report = importer().run(dir);
        assertNotNull(report);
        assertEquals(0, report.dictionaryWords());
        assertEquals(1, report.winners());
    }
}
