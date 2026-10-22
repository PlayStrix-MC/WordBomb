package padej.wordbomb.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Журнал модерации словаря ({@code action_log}): кто, когда и что сделал со словом.
 * Поиск - по подстроке в каждом поле (как в старом плагине), любое поле можно опустить.
 */
public final class ActionLogRepo {

    public static final String ACCEPT = "accept";
    public static final String BLOCK = "block";
    public static final String ADD = "add";
    public static final String REMOVE = "remove";

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Запись журнала. */
    public record Entry(long id, String date, String time, String player, String action, String word) {}

    private final Database db;
    private final ZoneId zone;

    public ActionLogRepo(Database db) {
        this(db, ZoneId.systemDefault());
    }

    public ActionLogRepo(Database db, ZoneId zone) {
        this.db = db;
        this.zone = zone;
    }

    public void log(String player, String action, String word) {
        logAt(LocalDateTime.now(zone), player, action, word);
    }

    public void logAt(LocalDateTime at, String player, String action, String word) {
        db.execute(c -> insert(c, at, player, action, word));
    }

    /** Поиск: {@code null} или пустая строка - поле не фильтруется; совпадение - подстрока без учёта регистра. */
    public List<Entry> search(String date, String time, String player, String action, String word) {
        return db.read(c -> {
            StringBuilder sql = new StringBuilder("SELECT id, date, time, player, action, word FROM action_log WHERE 1=1");
            List<String> args = new ArrayList<>();
            addFilter(sql, args, "date", date);
            addFilter(sql, args, "time", time);
            addFilter(sql, args, "player", player);
            addFilter(sql, args, "action", action);
            addFilter(sql, args, "word", word);
            sql.append(" ORDER BY id ASC");
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    List<Entry> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(new Entry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
                    }
                    return out;
                }
            }
        });
    }

    public List<String> dates() {
        return distinct("date");
    }

    public List<String> players() {
        return distinct("player");
    }

    public List<String> actions() {
        return distinct("action");
    }

    public long count() {
        return db.read(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM action_log")) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    private List<String> distinct(String column) {
        return db.read(c -> {
            List<String> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DISTINCT " + column + " FROM action_log ORDER BY " + column)) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        });
    }

    private static void addFilter(StringBuilder sql, List<String> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND lower(").append(column).append(") LIKE ?");
        args.add("%" + value.toLowerCase() + "%");
    }

    // ------------------------------------------------------------------ внутри транзакций

    void insert(Connection c, LocalDateTime at, String player, String action, String word) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO action_log(ts, date, time, player, action, word) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, at.atZone(zone).toInstant().toEpochMilli());
            ps.setString(2, at.toLocalDate().format(DATE));
            ps.setString(3, at.toLocalTime().format(TIME));
            ps.setString(4, player);
            ps.setString(5, action);
            ps.setString(6, word);
            ps.executeUpdate();
        }
    }

    /** Разбор даты и времени из строки старого журнала. */
    static LocalDateTime parse(String date, String time) {
        LocalDate d = LocalDate.parse(date, DATE);
        LocalTime t = LocalTime.parse(time, TIME);
        return LocalDateTime.of(d, t);
    }

    /** Для тестов: время, которым репозиторий помечает записи. */
    LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(), zone);
    }
}
