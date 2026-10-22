package padej.wordbomb.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    /** Словарь из одного слова: шаблон всегда «аб», слово «абв» подходит всегда. */
    private static final Lexicon LEXICON = new Lexicon() {
        @Override
        public boolean contains(String w) {
            return w.startsWith("аб");
        }

        @Override
        public String randomWord() {
            return "аб";
        }
    };

    static final class Recorder implements GameListener {
        final List<String> events = new ArrayList<>();
        UUID winner;
        int endedUsed = -1;
        List<UUID> endedParticipants;

        @Override
        public void onQueueChanged(List<UUID> queue) {
            events.add("queue:" + queue.size());
        }

        @Override
        public void onCountdownStarted(int seconds) {
            events.add("countdown:" + seconds);
        }

        @Override
        public void onCountdownTick(int secondsLeft) {
            events.add("cd:" + secondsLeft);
        }

        @Override
        public void onCountdownCancelled() {
            events.add("cancelled");
        }

        @Override
        public void onGameStarted(List<UUID> players) {
            events.add("start:" + players.size());
        }

        @Override
        public void onTurnStarted(UUID player, String template, int seconds, int round) {
            events.add("turn:" + template + ":" + seconds + ":" + round);
        }

        @Override
        public void onTurnTick(UUID player, String template, int secondsLeft, int round) {
            events.add("tick:" + secondsLeft);
        }

        @Override
        public void onWordAccepted(UUID player, String word, String template, int usedWords) {
            events.add("word:" + word);
        }

        @Override
        public void onRoundCompleted(int round) {
            events.add("round:" + round);
        }

        String lostTemplate;
        java.util.Set<String> lostUsed;

        @Override
        public void onTurnLost(UUID player, String template, java.util.Set<String> usedWords) {
            lostTemplate = template;
            lostUsed = java.util.Set.copyOf(usedWords);
            events.add("lost:" + template);
        }

        @Override
        public void onPlayerEliminated(UUID player, EliminationReason reason, int alivePlayers) {
            events.add("elim:" + reason + ":" + alivePlayers);
        }

        @Override
        public void onRoundAfterElimination(int round) {
            events.add("elimround:" + round);
        }

        @Override
        public void onGameEnded(UUID winner, int usedWords, List<UUID> participants) {
            this.winner = winner;
            this.endedUsed = usedWords;
            this.endedParticipants = participants;
            events.add("end");
        }

        @Override
        public void onSpectatorLeft(UUID player) {
            events.add("spectatorLeft");
        }
    }

    private final Rules rules = new Rules(2, 3, 10, 5, 2, 1.0, 20, 50);
    private Recorder listener;
    private GameSession session;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new Recorder();
        session = new GameSession(rules, LEXICON, new TemplateGenerator(LEXICON, rules, new Random(1)), listener);
    }

    private void startWith(UUID... players) {
        for (UUID p : players) session.join(p);
        assertEquals(GameSession.StartResult.STARTED, session.requestStart());
    }

    private int counter = 0;

    /** Уникальное подходящее слово для текущего хода. */
    private String freshWord() {
        return "аб" + "в".repeat(++counter);
    }

    @Test
    void countdownStartsAtTwoPlayersAndCancelsBelow() {
        assertEquals(GameSession.JoinResult.JOINED, session.join(a));
        assertEquals(GameSession.State.WAITING, session.state());
        assertEquals(GameSession.JoinResult.ALREADY_IN, session.join(a));
        assertEquals(GameSession.JoinResult.JOINED, session.join(b));
        assertEquals(GameSession.State.COUNTDOWN, session.state());
        assertEquals(3, session.countdownLeft());
        assertTrue(listener.events.contains("countdown:3"));

        assertEquals(GameSession.LeaveResult.LEFT, session.leave(b));
        assertEquals(GameSession.State.WAITING, session.state());
        assertEquals(-1, session.countdownLeft());
        assertTrue(listener.events.contains("cancelled"));
        assertEquals(GameSession.LeaveResult.NOT_IN_QUEUE, session.leave(b));
    }

    @Test
    void countdownTicksToStart() {
        session.join(a);
        session.join(b);
        session.tick();
        session.tick();
        assertEquals(GameSession.State.COUNTDOWN, session.state());
        session.tick();
        assertEquals(GameSession.State.RUNNING, session.state());
        assertTrue(listener.events.contains("start:2"));
        assertEquals(Optional.of(a), session.currentPlayer());
        assertEquals(Optional.of("аб"), session.template());
        assertEquals(10, session.turnSecondsLeft());
    }

    @Test
    void manualStartWorksWithTwoAndRefusesWithOne() {
        assertEquals(GameSession.StartResult.NOT_ENOUGH_PLAYERS, session.requestStart());
        session.join(a);
        assertEquals(GameSession.StartResult.NOT_ENOUGH_PLAYERS, session.requestStart());
        session.join(b);
        assertEquals(GameSession.StartResult.STARTED, session.requestStart());
        assertEquals(GameSession.StartResult.ALREADY_RUNNING, session.requestStart());
        assertEquals(GameSession.JoinResult.GAME_RUNNING, session.join(c));
        assertEquals(GameSession.LeaveResult.GAME_RUNNING, session.leave(a));
    }

    @Test
    void turnsGoAroundAndRoundClosesWhenEveryoneAnswered() {
        startWith(a, b, c);
        assertEquals(Optional.of(a), session.currentPlayer());
        assertEquals(GameSession.WordResult.NOT_YOUR_TURN, session.submitWord(b, freshWord()));
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitWord(a, freshWord()));
        assertEquals(Optional.of(b), session.currentPlayer());
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitWord(b, freshWord()));
        assertEquals(Optional.of(c), session.currentPlayer());
        assertEquals(0, session.round());
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitWord(c, freshWord()));
        assertEquals(1, session.round());
        assertTrue(listener.events.contains("round:1"));
        assertEquals(Optional.of(a), session.currentPlayer());
        assertEquals(9, session.turnSecondsLeft(), "turn shortens with round");
        assertEquals(3, session.usedWordCount());
    }

    @Test
    void wordValidationThroughSession() {
        startWith(a, b);
        assertEquals(GameSession.WordResult.NOT_FIT, session.submitWord(a, "hello"));
        assertEquals(GameSession.WordResult.NOT_FIT, session.submitWord(a, "вгд"));
        assertEquals(GameSession.WordResult.NOT_IN_DICTIONARY, session.submitWord(a, "баб"));
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitWord(a, "абв"));
        assertEquals(GameSession.WordResult.ALREADY_USED, session.submitWord(b, "АБВ"));
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitValidatedWord(b, "абвг"));
        assertEquals(GameSession.WordResult.NOT_ACTIVE, new GameSession(rules, LEXICON,
                new TemplateGenerator(LEXICON, rules, new Random()), listener).submitWord(a, "абв"));
    }

    @Test
    void usedWordsAreNormalized() {
        startWith(a, b);
        assertEquals(GameSession.WordResult.ACCEPTED, session.submitWord(a, "абвё"));
        assertTrue(session.usedWords().contains("абве"));
        assertEquals(GameSession.WordResult.ALREADY_USED, session.submitWord(b, "абве"));
    }

    @Test
    void timeoutEliminatesCurrentAndPassesTurnToNext() {
        startWith(a, b, c);
        for (int i = 0; i < 10; i++) session.tick();
        assertTrue(listener.events.contains("elim:TIMEOUT:2"));
        assertEquals(List.of(b, c), session.alivePlayers());
        assertEquals(List.of(a), session.spectators());
        assertEquals(Optional.of(b), session.currentPlayer(), "turn goes forward, not back");
        assertEquals(1, session.round());
        assertTrue(listener.events.contains("elimround:1"));
        assertTrue(session.isSpectator(a));
    }

    @Test
    void timeoutReportsLostTemplateAndUsedWordsBeforeElimination() {
        startWith(a, b, c);
        session.submitWord(a, "абв");
        for (int i = 0; i < 10; i++) session.tick();
        assertEquals("аб", listener.lostTemplate);
        assertEquals(java.util.Set.of("абв"), listener.lostUsed);
        assertTrue(listener.events.indexOf("lost:аб") < listener.events.indexOf("elim:TIMEOUT:2"));
        // выход с сервера - без подсказки
        listener.lostTemplate = null;
        session.eliminate(c, GameListener.EliminationReason.QUIT);
        assertNull(listener.lostTemplate);
    }

    @Test
    void eliminatingPlayerBeforeCurrentKeepsCurrent() {
        startWith(a, b, c);
        session.submitWord(a, freshWord());
        assertEquals(Optional.of(b), session.currentPlayer());
        assertTrue(session.eliminate(a, GameListener.EliminationReason.QUIT));
        assertEquals(Optional.of(b), session.currentPlayer());
        assertEquals(List.of(b, c), session.alivePlayers());
    }

    @Test
    void eliminatingLastInOrderWrapsToFirst() {
        startWith(a, b, c);
        session.submitWord(a, freshWord());
        session.submitWord(b, freshWord());
        assertEquals(Optional.of(c), session.currentPlayer());
        session.eliminate(c, GameListener.EliminationReason.LEFT_ZONE);
        assertEquals(Optional.of(a), session.currentPlayer());
    }

    @Test
    void lastPlayerStandingWins() {
        startWith(a, b);
        session.submitWord(a, freshWord());
        session.eliminate(b, GameListener.EliminationReason.TIMEOUT);
        assertEquals(a, listener.winner);
        assertEquals(1, listener.endedUsed);
        assertEquals(2, listener.endedParticipants.size());
        assertEquals(GameSession.State.WAITING, session.state());
        assertTrue(session.queue().isEmpty());
        assertEquals(Optional.empty(), session.currentPlayer());
        assertEquals(Optional.empty(), session.template());
        assertEquals("queue:0", listener.events.get(listener.events.size() - 1));
    }

    @Test
    void everyoneLeavingEndsWithoutWinner() {
        startWith(a, b);
        session.removePlayer(a, GameListener.EliminationReason.QUIT);
        assertEquals(b, listener.winner);
        listener.winner = null;
        // новая игра: оба выходят по очереди до конца
        startWith(a, b, c);
        session.removePlayer(a, GameListener.EliminationReason.QUIT);
        assertEquals(GameSession.State.RUNNING, session.state());
        session.removePlayer(b, GameListener.EliminationReason.QUIT);
        assertEquals(c, listener.winner);
    }

    @Test
    void spectatorLeavingIsReported() {
        startWith(a, b, c);
        session.eliminate(a, GameListener.EliminationReason.TIMEOUT);
        session.removePlayer(a, GameListener.EliminationReason.QUIT);
        assertTrue(listener.events.contains("spectatorLeft"));
        assertFalse(session.isParticipant(a));
        assertEquals(GameSession.State.RUNNING, session.state());
    }

    @Test
    void removePlayerFromQueueCancelsCountdown() {
        session.join(a);
        session.join(b);
        session.removePlayer(a, GameListener.EliminationReason.QUIT);
        assertEquals(GameSession.State.WAITING, session.state());
        assertEquals(List.of(b), session.queue());
    }

    @Test
    void eliminateIgnoresOutsiders() {
        assertFalse(session.eliminate(a, GameListener.EliminationReason.FORCED));
        startWith(a, b);
        assertFalse(session.eliminate(c, GameListener.EliminationReason.FORCED));
    }

    @Test
    void snapshotFollowsState() {
        assertEquals(GameSession.State.WAITING, session.snapshot().state());
        startWith(a, b);
        GameSession.Snapshot s = session.snapshot();
        assertEquals(GameSession.State.RUNNING, s.state());
        assertTrue(s.isAlive(a));
        assertEquals(a, s.currentPlayer());
        assertEquals("аб", s.template());
        session.submitWord(a, freshWord());
        assertEquals(b, session.snapshot().currentPlayer());
        session.eliminate(a, GameListener.EliminationReason.TIMEOUT);
        assertNull(session.snapshot().currentPlayer());
        assertFalse(session.snapshot().isParticipant(a));
    }

    @Test
    void abortEndsRunningGameWithWinnerlessResultWhenTwoAlive() {
        startWith(a, b);
        session.abort();
        assertNull(listener.winner);
        assertEquals(GameSession.State.WAITING, session.state());
        session.join(a);
        session.abort();
        assertTrue(session.queue().isEmpty());
    }

    @Test
    void emptyLexiconEndsGameImmediately() {
        Lexicon empty = new Lexicon() {
            @Override
            public boolean contains(String w) {
                return false;
            }

            @Override
            public String randomWord() {
                return null;
            }
        };
        GameSession s = new GameSession(rules, empty, new TemplateGenerator(empty, rules, new Random()), listener);
        s.join(a);
        s.join(b);
        s.requestStart();
        assertEquals(GameSession.State.WAITING, s.state());
        assertTrue(listener.events.contains("end"));
    }
}
