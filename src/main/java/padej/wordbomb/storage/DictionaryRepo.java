package padej.wordbomb.storage;

import padej.wordbomb.core.Lexicon;
import padej.wordbomb.core.TextNormalizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

/**
 * Словарь в таблице {@code dictionary}: индексированный lookup и случайное слово из всего словаря
 * ({@code WHERE id >= random ORDER BY id LIMIT 1}). Слова хранятся нормализованными.
 */
public final class DictionaryRepo implements Lexicon {

    private final Database db;
    private final Random random;

    public DictionaryRepo(Database db) {
        this(db, new Random());
    }

    public DictionaryRepo(Database db, Random random) {
        this.db = db;
        this.random = random;
    }

    @Override
    public boolean contains(String word) {
        String w = TextNormalizer.normalize(word);
        if (w == null || w.isEmpty()) return false;
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM dictionary WHERE word = ? LIMIT 1")) {
                ps.setString(1, w);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public String randomWord() {
        return db.read(c -> {
            long maxId;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM dictionary")) {
                maxId = rs.next() ? rs.getLong(1) : 0;
            }
            if (maxId <= 0) return null;
            long pick = 1 + (long) (random.nextDouble() * maxId);
            try (PreparedStatement ps = c.prepareStatement("SELECT word FROM dictionary WHERE id >= ? ORDER BY id LIMIT 1")) {
                ps.setLong(1, pick);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            // дыра после удалений в хвосте - берём первое слово
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT word FROM dictionary ORDER BY id LIMIT 1")) {
                return rs.next() ? rs.getString(1) : null;
            }
        });
    }

    /** Добавить слово. {@code false}, если оно уже есть. */
    public boolean add(String word) {
        String w = TextNormalizer.normalize(word);
        if (w == null || w.isEmpty()) return false;
        return db.write(c -> insert(c, w));
    }

    /** Удалить слово. {@code false}, если его не было. */
    public boolean remove(String word) {
        String w = TextNormalizer.normalize(word);
        if (w == null || w.isEmpty()) return false;
        return db.write(c -> delete(c, w));
    }

    public long count() {
        return db.read(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dictionary")) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    /** Массовое добавление (импорт): слова нормализуются, дубликаты пропускаются. Возвращает число добавленных. */
    public long addAll(Iterable<String> words) {
        return db.write(c -> {
            long added = 0;
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO dictionary(word) VALUES (?)")) {
                int batch = 0;
                for (String raw : words) {
                    String w = TextNormalizer.normalize(raw);
                    if (w == null || w.isEmpty() || !TextNormalizer.isCyrillicWord(w)) continue;
                    ps.setString(1, w);
                    ps.addBatch();
                    if (++batch >= 5000) {
                        added += sum(ps.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) added += sum(ps.executeBatch());
            }
            return added;
        });
    }

    // ------------------------------------------------------------------ внутри транзакций других сервисов

    static boolean insert(Connection c, String normalized) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO dictionary(word) VALUES (?)")) {
            ps.setString(1, normalized);
            return ps.executeUpdate() > 0;
        }
    }

    static boolean delete(Connection c, String normalized) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM dictionary WHERE word = ?")) {
            ps.setString(1, normalized);
            return ps.executeUpdate() > 0;
        }
    }

    private static long sum(int[] counts) {
        long s = 0;
        for (int n : counts) if (n > 0) s += n;
        return s;
    }
}
