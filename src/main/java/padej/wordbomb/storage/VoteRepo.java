package padej.wordbomb.storage;

import padej.wordbomb.core.TextNormalizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Голоса игроков за слово: предложения ({@code suggestions}) и жалобы ({@code reports}) устроены одинаково -
 * один игрок даёт одному слову один голос. Таблица задаётся подклассом.
 */
public abstract class VoteRepo {

    /** Слово и число голосов. */
    public record Entry(String word, int votes) {}

    protected final Database db;
    private final String table;

    protected VoteRepo(Database db, String table) {
        this.db = db;
        this.table = table;
    }

    public String table() {
        return table;
    }

    /** Голос игрока. {@code false}, если он уже голосовал за это слово. */
    public boolean vote(String word, UUID player) {
        String w = TextNormalizer.normalize(word);
        return db.write(c -> insertVote(c, w, player, System.currentTimeMillis()));
    }

    public boolean hasVoted(String word, UUID player) {
        String w = TextNormalizer.normalize(word);
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE word = ? AND player_uuid = ? LIMIT 1")) {
                ps.setString(1, w);
                ps.setString(2, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /** Есть ли слово в списке (хотя бы один голос). */
    public boolean contains(String word) {
        return votes(word) > 0;
    }

    public int votes(String word) {
        String w = TextNormalizer.normalize(word);
        return db.read(c -> countVotes(c, w));
    }

    /** Топ слов по числу голосов. */
    public List<Entry> top(int limit) {
        return db.read(c -> {
            List<Entry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT word, COUNT(*) AS n FROM " + table + " GROUP BY word ORDER BY n DESC, word ASC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Entry(rs.getString(1), rs.getInt(2)));
                }
            }
            return out;
        });
    }

    /** Убрать слово со всеми голосами. {@code false}, если его не было. */
    public boolean removeWord(String word) {
        String w = TextNormalizer.normalize(word);
        return db.write(c -> deleteWord(c, w));
    }

    // ------------------------------------------------------------------ внутри транзакций

    boolean insertVote(Connection c, String normalized, UUID player, long createdAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO " + table + "(word, player_uuid, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, normalized);
            ps.setString(2, player.toString());
            ps.setLong(3, createdAt);
            return ps.executeUpdate() > 0;
        }
    }

    int countVotes(Connection c, String normalized) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE word = ?")) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    boolean deleteWord(Connection c, String normalized) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE word = ?")) {
            ps.setString(1, normalized);
            return ps.executeUpdate() > 0;
        }
    }
}
