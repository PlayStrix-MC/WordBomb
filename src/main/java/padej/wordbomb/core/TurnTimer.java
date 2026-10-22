package padej.wordbomb.core;

/**
 * Секундный таймер хода и отсчёта. Ядро не знает о планировщике сервера: снаружи раз в секунду
 * вызывается {@link #tick()}, а длина хода считается по {@link Rules#turnSeconds(int)}.
 * <p>
 * Отсчёт без «лишней» секунды: {@code start(5)} → 5 секунд; каждый тик уменьшает на единицу,
 * пятый тик даёт 0 и {@link #expired()}.
 */
public final class TurnTimer {

    private final Rules rules;
    private int secondsLeft;
    private int total;
    private boolean running;

    public TurnTimer(Rules rules) {
        this.rules = rules;
    }

    /** Запустить ход для раунда {@code round}. Возвращает длину хода. */
    public int startTurn(int round) {
        return start(rules.turnSeconds(round));
    }

    /** Запустить отсчёт на {@code seconds} секунд. */
    public int start(int seconds) {
        total = Math.max(0, seconds);
        secondsLeft = total;
        running = total > 0;
        return total;
    }

    public void stop() {
        running = false;
        secondsLeft = 0;
        total = 0;
    }

    /** Прошла секунда. Возвращает, сколько осталось (0 - время вышло). */
    public int tick() {
        if (!running) return 0;
        secondsLeft = Math.max(0, secondsLeft - 1);
        if (secondsLeft == 0) running = false;
        return secondsLeft;
    }

    public boolean running() {
        return running;
    }

    public boolean expired() {
        return !running && total > 0 && secondsLeft == 0;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public int total() {
        return total;
    }

    /** Доля оставшегося времени 0..1 (для боссбара). */
    public float progress() {
        return total <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) secondsLeft / total));
    }
}
