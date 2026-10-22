package padej.wordbomb.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ядро игры: очередь, отсчёт, ходы по кругу, раунды, выбывание, конец игры. Без Bukkit.
 * <p>
 * Все методы, меняющие состояние, вызываются из одного потока (на сервере - главного). Для чужих потоков
 * есть неизменяемый {@link #snapshot()}: он обновляется при каждом изменении и читается без блокировок.
 *
 * <pre>
 * WAITING ──(≥minPlayers)──► COUNTDOWN ──(0)──► RUNNING ──(1 живой)──► WAITING
 *    ▲                           │ (&lt;minPlayers)
 *    └───────────────────────────┘
 * </pre>
 */
public final class GameSession {

    public enum State { WAITING, COUNTDOWN, RUNNING }

    public enum JoinResult { JOINED, ALREADY_IN, GAME_RUNNING }

    public enum LeaveResult { LEFT, NOT_IN_QUEUE, GAME_RUNNING }

    public enum StartResult { STARTED, NOT_ENOUGH_PLAYERS, ALREADY_RUNNING }

    public enum WordResult { ACCEPTED, NOT_ACTIVE, NOT_YOUR_TURN, NOT_FIT, NOT_IN_DICTIONARY, ALREADY_USED }

    /** Неизменяемый срез состояния для других потоков. */
    public record Snapshot(State state, Set<UUID> alive, Set<UUID> spectators, UUID currentPlayer, String template, int round) {
        public boolean isAlive(UUID player) {
            return alive.contains(player);
        }

        public boolean isParticipant(UUID player) {
            return alive.contains(player) || spectators.contains(player);
        }
    }

    private final Rules rules;
    private final TemplateGenerator templates;
    private final Lexicon dictionary;
    private final GameListener listener;
    private final TurnTimer timer;

    private State state = State.WAITING;
    /** До старта - очередь; во время игры - живые игроки в порядке ходов. */
    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> spectators = new ArrayList<>();
    private final Set<String> usedWords = new HashSet<>();
    private final Set<UUID> answered = new HashSet<>();
    private int currentIndex;
    private int round;
    private int countdownLeft = -1;
    private String template;

    private volatile Snapshot snapshot = new Snapshot(State.WAITING, Set.of(), Set.of(), null, null, 0);

    public GameSession(Rules rules, Lexicon dictionary, TemplateGenerator templates, GameListener listener) {
        this.rules = rules;
        this.dictionary = dictionary;
        this.templates = templates;
        this.listener = listener;
        this.timer = new TurnTimer(rules);
    }

    // ------------------------------------------------------------------ очередь

    public JoinResult join(UUID player) {
        if (state == State.RUNNING) return JoinResult.GAME_RUNNING;
        if (players.contains(player)) return JoinResult.ALREADY_IN;
        players.add(player);
        publish();
        listener.onQueueChanged(queue());
        if (state == State.WAITING && players.size() >= rules.minPlayers()) startCountdown();
        return JoinResult.JOINED;
    }

    public LeaveResult leave(UUID player) {
        if (state == State.RUNNING) {
            return isParticipant(player) ? LeaveResult.GAME_RUNNING : LeaveResult.NOT_IN_QUEUE;
        }
        if (!players.remove(player)) return LeaveResult.NOT_IN_QUEUE;
        publish();
        listener.onQueueChanged(queue());
        if (state == State.COUNTDOWN && players.size() < rules.minPlayers()) cancelCountdown();
        return LeaveResult.LEFT;
    }

    /** Ручной старт: при достаточном числе игроков игра начинается сразу, без ожидания отсчёта. */
    public StartResult requestStart() {
        if (state == State.RUNNING) return StartResult.ALREADY_RUNNING;
        if (players.size() < rules.minPlayers()) return StartResult.NOT_ENOUGH_PLAYERS;
        if (state == State.COUNTDOWN) {
            countdownLeft = -1;
            timer.stop();
        }
        startGame();
        return StartResult.STARTED;
    }

    private void startCountdown() {
        state = State.COUNTDOWN;
        countdownLeft = rules.countdownSeconds();
        publish();
        listener.onCountdownStarted(countdownLeft);
        if (countdownLeft <= 0) startGame();
    }

    private void cancelCountdown() {
        state = State.WAITING;
        countdownLeft = -1;
        publish();
        listener.onCountdownCancelled();
    }

    // ------------------------------------------------------------------ время

    /** Прошла секунда. Вызывается планировщиком раз в секунду в любом состоянии. */
    public void tick() {
        switch (state) {
            case COUNTDOWN -> {
                countdownLeft = Math.max(0, countdownLeft - 1);
                listener.onCountdownTick(countdownLeft);
                if (countdownLeft == 0) startGame();
            }
            case RUNNING -> {
                if (!timer.running()) return;
                int left = timer.tick();
                UUID current = players.get(currentIndex);
                listener.onTurnTick(current, template, left, round);
                if (left == 0) eliminate(current, GameListener.EliminationReason.TIMEOUT);
            }
            case WAITING -> {
            }
        }
    }

    // ------------------------------------------------------------------ игра

    private void startGame() {
        state = State.RUNNING;
        countdownLeft = -1;
        round = 0;
        currentIndex = 0;
        usedWords.clear();
        answered.clear();
        spectators.clear();
        publish();
        listener.onGameStarted(List.copyOf(players));
        beginTurn();
    }

    private void beginTurn() {
        if (players.isEmpty()) {
            endGame();
            return;
        }
        if (currentIndex >= players.size()) currentIndex = 0;
        template = templates.next(usedWords);
        if (template == null) {
            // словарь пуст - играть нечем
            endGame();
            return;
        }
        int seconds = timer.startTurn(round);
        publish();
        listener.onTurnStarted(players.get(currentIndex), template, seconds, round);
    }

    /** Слово из чата: полная проверка (раскладка, кириллица, шаблон, словарь, повтор) и ход. */
    public WordResult submitWord(UUID player, String rawText) {
        if (state != State.RUNNING) return WordResult.NOT_ACTIVE;
        if (!player.equals(players.get(currentIndex))) return WordResult.NOT_YOUR_TURN;
        WordValidator.Result result = WordValidator.validate(rawText, template, dictionary, usedWords);
        return switch (result.verdict()) {
            case NOT_FIT -> WordResult.NOT_FIT;
            case NOT_IN_DICTIONARY -> WordResult.NOT_IN_DICTIONARY;
            case ALREADY_USED -> WordResult.ALREADY_USED;
            case OK -> accept(player, result.word());
        };
    }

    /**
     * Слово, уже нормализованное и проверенное по словарю снаружи (асинхронно). Здесь остаются
     * проверки, зависящие от состояния: активность, чей ход, шаблон, повтор.
     */
    public WordResult submitValidatedWord(UUID player, String normalizedWord) {
        if (state != State.RUNNING) return WordResult.NOT_ACTIVE;
        if (!player.equals(players.get(currentIndex))) return WordResult.NOT_YOUR_TURN;
        String word = TextNormalizer.normalize(normalizedWord);
        if (!TextNormalizer.isCyrillicWord(word) || !WordValidator.fitsTemplate(word, template)) return WordResult.NOT_FIT;
        if (usedWords.contains(word)) return WordResult.ALREADY_USED;
        return accept(player, word);
    }

    private WordResult accept(UUID player, String word) {
        usedWords.add(word);
        answered.add(player);
        listener.onWordAccepted(player, word, template, usedWords.size());
        currentIndex = (currentIndex + 1) % players.size();
        if (answered.containsAll(players)) {
            round++;
            answered.clear();
            listener.onRoundCompleted(round);
        }
        beginTurn();
        return WordResult.ACCEPTED;
    }

    /** Выбывание живого игрока. {@code false}, если игра не идёт или игрок не играет. */
    public boolean eliminate(UUID player, GameListener.EliminationReason reason) {
        if (state != State.RUNNING) return false;
        int index = players.indexOf(player);
        if (index < 0) return false;
        players.remove(index);
        answered.remove(player);
        spectators.add(player);
        // ход переходит к следующему живому: если выбыл игрок до текущего, индекс сдвигается,
        // если выбыл текущий - индекс уже указывает на следующего
        if (index < currentIndex) currentIndex--;
        if (currentIndex >= players.size()) currentIndex = 0;
        timer.stop();
        publish();
        if (reason == GameListener.EliminationReason.TIMEOUT && template != null) {
            listener.onTurnLost(player, template, Collections.unmodifiableSet(usedWords));
        }
        listener.onPlayerEliminated(player, reason, players.size());
        if (players.size() <= 1) {
            endGame();
            return true;
        }
        round++;
        answered.clear();
        listener.onRoundAfterElimination(round);
        beginTurn();
        return true;
    }

    /**
     * Игрок исчез (вышел с сервера): из очереди - тихо убираем, из игры - выбывает, из наблюдателей - убираем.
     */
    public void removePlayer(UUID player, GameListener.EliminationReason reason) {
        if (state == State.RUNNING) {
            if (players.contains(player)) {
                eliminate(player, reason);
            } else if (spectators.remove(player)) {
                publish();
                listener.onSpectatorLeft(player);
            }
            return;
        }
        leave(player);
    }

    /** Принудительно завершить игру (выключение плагина). В любом состоянии приводит к WAITING. */
    public void abort() {
        if (state == State.RUNNING) {
            endGame();
        } else {
            players.clear();
            if (state == State.COUNTDOWN) cancelCountdown();
            publish();
            listener.onQueueChanged(List.of());
        }
    }

    private void endGame() {
        UUID winner = players.size() == 1 ? players.get(0) : null;
        List<UUID> participants = new ArrayList<>(players);
        participants.addAll(spectators);
        int used = usedWords.size();
        timer.stop();
        state = State.WAITING;
        players.clear();
        spectators.clear();
        usedWords.clear();
        answered.clear();
        currentIndex = 0;
        round = 0;
        template = null;
        countdownLeft = -1;
        templates.reset();
        publish();
        listener.onGameEnded(winner, used, Collections.unmodifiableList(participants));
        listener.onQueueChanged(List.of());
    }

    // ------------------------------------------------------------------ состояние

    private void publish() {
        snapshot = new Snapshot(state,
                Set.copyOf(players),
                Set.copyOf(spectators),
                state == State.RUNNING && !players.isEmpty() ? players.get(Math.min(currentIndex, players.size() - 1)) : null,
                state == State.RUNNING ? template : null,
                round);
    }

    /** Срез состояния для других потоков. */
    public Snapshot snapshot() {
        return snapshot;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Очередь (до старта) или живые игроки в порядке ходов (во время игры). */
    public List<UUID> queue() {
        return List.copyOf(players);
    }

    public List<UUID> alivePlayers() {
        return List.copyOf(players);
    }

    public List<UUID> spectators() {
        return List.copyOf(spectators);
    }

    /** Все, кто участвует сейчас: очередь/живые и выбывшие. */
    public List<UUID> participants() {
        Set<UUID> all = new LinkedHashSet<>(players);
        all.addAll(spectators);
        return List.copyOf(all);
    }

    public boolean isParticipant(UUID player) {
        return players.contains(player) || spectators.contains(player);
    }

    public boolean isAlive(UUID player) {
        return players.contains(player);
    }

    public boolean isSpectator(UUID player) {
        return spectators.contains(player);
    }

    public Optional<UUID> currentPlayer() {
        if (state != State.RUNNING || players.isEmpty()) return Optional.empty();
        return Optional.of(players.get(Math.min(currentIndex, players.size() - 1)));
    }

    public Optional<String> template() {
        return state == State.RUNNING ? Optional.ofNullable(template) : Optional.empty();
    }

    public int round() {
        return round;
    }

    /** Секунды до старта или -1, если отсчёт не идёт. */
    public int countdownLeft() {
        return state == State.COUNTDOWN ? countdownLeft : -1;
    }

    public int turnSecondsLeft() {
        return timer.secondsLeft();
    }

    public float turnProgress() {
        return timer.progress();
    }

    public int usedWordCount() {
        return usedWords.size();
    }

    public Set<String> usedWords() {
        return Collections.unmodifiableSet(usedWords);
    }

    public Rules rules() {
        return rules;
    }
}
