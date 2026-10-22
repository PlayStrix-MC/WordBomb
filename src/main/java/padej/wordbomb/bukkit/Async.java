package padej.wordbomb.bukkit;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Работа с базой вне главного потока. Один выделенный поток-писатель выполняет задачи по очереди
 * (SQLite всё равно пишет последовательно), результат возвращается в главный поток через планировщик.
 */
public final class Async {

    private final Plugin plugin;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WordBomb DB");
        t.setDaemon(true);
        return t;
    });

    public Async(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Выполнить в потоке БД, результат отдать в главный поток. Ошибка - в лог. */
    public <T> void supply(Supplier<T> work, Consumer<T> onMain) {
        supply(work, onMain, e -> plugin.getLogger().log(Level.SEVERE, "WordBomb: ошибка в фоновой задаче", e));
    }

    public <T> void supply(Supplier<T> work, Consumer<T> onMain, Consumer<Throwable> onError) {
        if (worker.isShutdown()) return;
        worker.execute(() -> {
            T result;
            try {
                result = work.get();
            } catch (Throwable e) {
                toMain(() -> onError.accept(e));
                return;
            }
            toMain(() -> onMain.accept(result));
        });
    }

    /** Выполнить в потоке БД без результата. */
    public void run(Runnable work) {
        if (worker.isShutdown()) return;
        worker.execute(() -> {
            try {
                work.run();
            } catch (Throwable e) {
                plugin.getLogger().log(Level.SEVERE, "WordBomb: ошибка в фоновой задаче", e);
            }
        });
    }

    /** В главный поток (если плагин ещё включён; иначе - сразу, в текущем). */
    public void toMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } else {
            task.run();
        }
    }

    /** Дождаться завершения очереди (выключение плагина). */
    public void shutdown() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }
}
