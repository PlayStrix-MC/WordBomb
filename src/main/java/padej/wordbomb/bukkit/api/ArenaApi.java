package padej.wordbomb.bukkit.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.wordbomb.bukkit.Chat;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.Messages;
import padej.wordbomb.core.GameSession;

/**
 * Действия игрока с ареной: вступить, выйти, запустить. Один вход для команды {@code /wb}, кликов по
 * публичному экрану и (в будущем) скрипта экрана через глобал {@code wordbomb}.
 * Все ответы игроку - текстами из {@link Messages}; методы вызываются в главном потоке.
 */
public final class ArenaApi {

    private final WordBombPlugin plugin;

    public ArenaApi(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    /** Встать в очередь. {@code true} - вступил. */
    public boolean join(Player player) {
        Chat chat = plugin.chat();
        Messages m = chat.messages();
        GameSession session = plugin.session();
        if (session.isRunning()) {
            chat.send(player, session.isParticipant(player.getUniqueId()) ? m.alreadyInGame() : m.gameAlreadyRunning());
            return false;
        }
        if (!isInZone(player)) {
            chat.send(player, m.tooFarToJoin());
            return false;
        }
        switch (session.join(player.getUniqueId())) {
            case JOINED -> {
                chat.send(player, m.joined());
                return true;
            }
            case ALREADY_IN -> chat.send(player, m.alreadyInGame());
            case GAME_RUNNING -> chat.send(player, m.gameAlreadyRunning());
        }
        return false;
    }

    /** Выйти из очереди (только до старта). */
    public boolean leave(Player player) {
        Chat chat = plugin.chat();
        Messages m = chat.messages();
        switch (plugin.session().leave(player.getUniqueId())) {
            case LEFT -> {
                chat.send(player, m.leftQueue());
                return true;
            }
            case GAME_RUNNING -> chat.send(player, m.cannotLeaveDuringGame());
            case NOT_IN_QUEUE -> chat.send(player, m.notInQueue());
        }
        return false;
    }

    /** Ручной старт: при достаточном числе игроков игра начинается сразу. */
    public boolean start(Player player) {
        Chat chat = plugin.chat();
        Messages m = chat.messages();
        GameSession session = plugin.session();
        switch (session.requestStart()) {
            case STARTED -> {
                chat.send(player, m.startedManually());
                return true;
            }
            case ALREADY_RUNNING -> chat.send(player, m.gameAlreadyRunning());
            case NOT_ENOUGH_PLAYERS -> chat.send(player, m.notEnoughPlayers(), "count", session.rules().minPlayers());
        }
        return false;
    }

    /** Состояние словом (для скрипта экрана и отладки): WAITING, COUNTDOWN, RUNNING. */
    public String status() {
        return plugin.session().state().name();
    }

    public int players() {
        return plugin.session().queue().size();
    }

    /** В зоне арены (тот же мир, не дальше {@code zoneRadius}). */
    public boolean isInZone(Player player) {
        Location center = plugin.arenaCenter();
        if (center == null || center.getWorld() == null) return false;
        if (!player.getWorld().equals(center.getWorld())) return false;
        double r = plugin.config().zoneRadius();
        return player.getLocation().distanceSquared(center) <= r * r;
    }
}
