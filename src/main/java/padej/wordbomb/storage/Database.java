package padej.wordbomb.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Одно соединение SQLite на плагин. Все обращения идут через {@link #read(SqlFunction)} и
 * {@link #write(SqlFunction)}, которые сериализуют доступ (SQLite всё равно выполняет запросы по одному),
 * поэтому репозитории можно звать из любого потока. Долгие записи делайте вне главного потока.
 * <p>
 * Режим: WAL, {@code synchronous=NORMAL}, {@code busy_timeout=5000}, {@code foreign_keys=ON}.
 */
public final class Database implements AutoCloseable {

    /** Версия схемы; при повышении добавьте миграцию в {@link #migrate()}. */
    public static final int SCHEMA_VERSION = 1;

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlAction {
        void run(Connection connection) throws SQLException;
    }

    /** Ошибка хранилища: обёртка над {@link SQLException}, чтобы вызывающий код не был усыпан checked-исключениями. */
    public static final class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Connection connection;
    private final Object lock = new Object();

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Открыть (или создать) файл базы и привести схему к актуальной версии. */
    public static Database open(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Database db = new Database(connect("jdbc:sqlite:" + file.toAbsolutePath()));
            db.configure(true);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new StorageException("Cannot open database " + file + ": " + e.getMessage(), e);
        }
    }

    /** База в памяти - для тестов. */
    public static Database inMemory() {
        try {
            Database db = new Database(connect("jdbc:sqlite::memory:"));
            db.configure(false);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new StorageException("Cannot open in-memory database: " + e.getMessage(), e);
        }
    }

    private static Connection connect(String url) throws SQLException {
        try {
            // драйвер вшит в jar плагина; ServiceLoader через classloader плагина может его не найти
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        return DriverManager.getConnection(url);
    }

    private void configure(boolean fileBacked) throws SQLException {
        try (Statement st = connection.createStatement()) {
            if (fileBacked) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        int version = Integer.parseInt(metaOrDefault("schema_version", "0"));
        if (version < 1) {
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS dictionary (
                          id   INTEGER PRIMARY KEY,
                          word TEXT NOT NULL UNIQUE
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS suggestions (
                          word        TEXT NOT NULL,
                          player_uuid TEXT NOT NULL,
                          created_at  INTEGER NOT NULL,
                          PRIMARY KEY (word, player_uuid)
                        )""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS suggestions_votes ON suggestions(word)");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS reports (
                          word        TEXT NOT NULL,
                          player_uuid TEXT NOT NULL,
                          created_at  INTEGER NOT NULL,
                          PRIMARY KEY (word, player_uuid)
                        )""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS reports_votes ON reports(word)");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS winners (
                          player_uuid TEXT PRIMARY KEY,
                          name        TEXT NOT NULL,
                          wins        INTEGER NOT NULL DEFAULT 0
                        )""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS winners_wins ON winners(wins DESC)");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS action_log (
                          id      INTEGER PRIMARY KEY,
                          ts      INTEGER NOT NULL,
                          date    TEXT NOT NULL,
                          time    TEXT NOT NULL,
                          player  TEXT NOT NULL,
                          action  TEXT NOT NULL,
                          word    TEXT NOT NULL
                        )""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS action_log_date ON action_log(date)");
                setMetaUnlocked("schema_version", String.valueOf(SCHEMA_VERSION));
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------------ доступ

    /** Чтение: один запрос под блокировкой. */
    public <T> T read(SqlFunction<T> query) {
        synchronized (lock) {
            try {
                return query.apply(connection);
            } catch (SQLException e) {
                throw new StorageException("Query failed: " + e.getMessage(), e);
            }
        }
    }

    /** Запись в транзакции: при исключении - откат. */
    public <T> T write(SqlFunction<T> work) {
        synchronized (lock) {
            try {
                connection.setAutoCommit(false);
                try {
                    T result = work.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new StorageException("Transaction failed: " + e.getMessage(), e);
            }
        }
    }

    /** Запись без результата, в транзакции. */
    public void execute(SqlAction work) {
        write(c -> {
            work.run(c);
            return null;
        });
    }

    // ------------------------------------------------------------------ meta

    public String meta(String key) {
        return read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private String metaOrDefault(String key, String def) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : def;
            }
        }
    }

    public void setMeta(String key, String value) {
        execute(c -> setMetaUnlocked(key, value));
    }

    private void setMetaUnlocked(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
