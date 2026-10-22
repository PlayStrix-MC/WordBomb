package padej.wordbomb.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Победы по UUID игрока; ник хранится как последний известный и обновляется при каждой победе. */
public final class WinnersRepo {

    /** Строка топа. */
    public record Entry(UUID player, String name, int wins) {}

    private final Database db;

    public WinnersRepo(Database db) {
        this.db = db;
    }

    public void addWin(UUID player, String name) {
        addWins(player, name, 1);
    }

    /** Прибавить {@code count} побед (импорт старой статистики). */
    public void addWins(UUID player, String name, int count) {
        if (count <= 0) return;
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO winners(player_uuid, name, wins) VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET wins = wins + excluded.wins, name = excluded.name""")) {
                ps.setString(1, player.toString());
                ps.setString(2, name);
                ps.setInt(3, count);
                ps.executeUpdate();
            }
        });
    }

    public int wins(UUID player) {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT wins FROM winners WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public List<Entry> top(int limit) {
        return db.read(c -> {
            List<Entry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT player_uuid, name, wins FROM winners ORDER BY wins DESC, name ASC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3)));
                }
            }
            return out;
        });
    }
}
