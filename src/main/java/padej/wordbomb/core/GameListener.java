package padej.wordbomb.core;

import java.util.List;
import java.util.UUID;

/**
 * Уведомления ядра для слоя представления (сообщения, боссбар, звуки, экран, эффекты).
 * Все методы вызываются в потоке, который вызвал {@link GameSession}; реализации по умолчанию пустые.
 */
public interface GameListener {

    /** Почему игрок выбыл. */
    enum EliminationReason { TIMEOUT, QUIT, LEFT_ZONE, FORCED }

    /** Изменился размер очереди (до старта) или игра закончилась и очередь обнулилась. */
    default void onQueueChanged(List<UUID> queue) {}

    default void onCountdownStarted(int seconds) {}

    default void onCountdownTick(int secondsLeft) {}

    default void onCountdownCancelled() {}

    default void onGameStarted(List<UUID> players) {}

    /** Новый ход: игроку выдан шаблон, таймер запущен. */
    default void onTurnStarted(UUID player, String template, int seconds, int round) {}

    /** Секунда хода прошла. {@code secondsLeft == 0} означает истечение (за ним следует выбывание). */
    default void onTurnTick(UUID player, String template, int secondsLeft, int round) {}

    default void onWordAccepted(UUID player, String word, String template, int usedWords) {}

    /** Все живые ответили - начался раунд {@code round}. */
    default void onRoundCompleted(int round) {}

    default void onPlayerEliminated(UUID player, EliminationReason reason, int alivePlayers) {}

    /** После выбывания игра продолжается новым раундом {@code round}. */
    default void onRoundAfterElimination(int round) {}

    /**
     * Игра окончена. {@code winner} - {@code null}, если победителя нет; {@code participants} - все, кто
     * играл (победитель и выбывшие).
     */
    default void onGameEnded(UUID winner, int usedWords, List<UUID> participants) {}

    /** Выбывший игрок покинул игру (вышел с сервера) до её окончания. */
    default void onSpectatorLeft(UUID player) {}
}
