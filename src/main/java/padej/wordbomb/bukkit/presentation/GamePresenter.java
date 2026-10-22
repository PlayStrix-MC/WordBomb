package padej.wordbomb.bukkit.presentation;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import padej.wordbomb.bukkit.Chat;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.bukkit.effects.EffectPlayer;
import padej.wordbomb.bukkit.effects.WordDisplay;
import padej.wordbomb.config.Messages;
import padej.wordbomb.config.WordBombConfig;
import padej.wordbomb.core.GameListener;
import padej.wordbomb.core.GameSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Всё, что игрок видит и слышит: сообщения, боссбар, звуки, расстановка по кругу и заморозка, режим наблюдателя,
 * слово в центре и череп, обновление публичного экрана. Реализует {@link GameListener} ядра.
 */
public final class GamePresenter implements GameListener {

    private final WordBombPlugin plugin;
    private final TurnBossBar bossBar = new TurnBossBar();
    private final PlayerFreezer freezer = new PlayerFreezer();
    private final EffectPlayer effects;
    private final Map<UUID, GameMode> previousModes = new HashMap<>();
    private WordDisplay word;
    private BukkitTask leashTask;

    public GamePresenter(WordBombPlugin plugin) {
        this.plugin = plugin;
        this.effects = new EffectPlayer(plugin.getLogger());
    }

    private Chat chat() {
        return plugin.chat();
    }

    private Messages m() {
        return plugin.chat().messages();
    }

    private WordBombConfig cfg() {
        return plugin.config();
    }

    private GameSession session() {
        return plugin.session();
    }

    /** Эффекты частиц плагина (для команд). */
    public EffectPlayer effects() {
        return effects;
    }

    // ------------------------------------------------------------------ очередь и отсчёт

    @Override
    public void onQueueChanged(List<UUID> queue) {
        plugin.arena().render();
    }

    @Override
    public void onCountdownStarted(int seconds) {
        plugin.arena().render();
    }

    @Override
    public void onCountdownTick(int secondsLeft) {
        plugin.arena().render();
    }

    @Override
    public void onCountdownCancelled() {
        plugin.arena().render();
    }

    // ------------------------------------------------------------------ игра

    @Override
    public void onGameStarted(List<UUID> players) {
        Location center = plugin.arenaCenter();
        bossBar.create(chat().format(m().bossbarStarting()));
        if (center != null) {
            word = new WordDisplay(plugin.getLogger(), effects, center.clone().add(0, cfg().wordHeight(), 0), cfg().wordScale());
            arrange(players, center);
        }
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            bossBar.show(p);
            if (cfg().freezePlayers()) freezer.freeze(p);
        }
        startLeash();
        plugin.arena().render();
    }

    /** Игроки по кругу радиуса {@code circleRadius} вокруг центра, лицом к центру. */
    private void arrange(List<UUID> players, Location center) {
        World world = center.getWorld();
        if (world == null) return;
        int n = players.size();
        double r = cfg().circleRadius();
        for (int i = 0; i < n; i++) {
            Player p = Bukkit.getPlayer(players.get(i));
            if (p == null) continue;
            double angle = i * 2 * Math.PI / n;
            double x = Math.floor(center.getX() + r * Math.cos(angle)) + 0.5;
            double z = Math.floor(center.getZ() + r * Math.sin(angle)) + 0.5;
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
            Location spot = new Location(world, x, y, z);
            Vector toCenter = center.clone().add(0, 1, 0).toVector().subtract(spot.clone().add(0, 1.62, 0).toVector());
            if (toCenter.lengthSquared() > 0) spot.setDirection(toCenter);
            p.teleport(spot);
        }
    }

    @Override
    public void onTurnStarted(UUID player, String template, int seconds, int round) {
        String name = name(player);
        for (Player p : online(session().participants())) {
            chat().send(p, m().turnOf(), "player", name, "template", template);
        }
        bossBar.update(chat().format(m().bossbarTurn(), "player", name, "template", template, "seconds", seconds), 1f);
        plugin.arena().render();
    }

    @Override
    public void onTurnTick(UUID player, String template, int secondsLeft, int round) {
        bossBar.update(chat().format(m().bossbarTurn(), "player", name(player), "template", template, "seconds", secondsLeft),
                session().turnProgress());
        if (secondsLeft > 0 && secondsLeft <= cfg().lastSecondsWarning()) {
            Player p = Bukkit.getPlayer(player);
            if (p != null) SoundTable.tick(p);
        }
    }

    @Override
    public void onWordAccepted(UUID player, String word, String template, int usedWords) {
        int idx = word.indexOf(template);
        String before = idx >= 0 ? word.substring(0, idx) : word;
        String shown = idx >= 0 ? word.substring(idx, idx + template.length()) : "";
        String after = idx >= 0 ? word.substring(idx + template.length()) : "";
        for (Player p : online(session().participants())) {
            SoundTable.wordAccepted(p);
            chat().sendRaw(p, m().wordAccepted(), "before", before, "template", shown, "after", after, "player", name(player));
        }
        if (this.word != null) this.word.show(word);
    }

    @Override
    public void onRoundCompleted(int round) {
        for (Player p : online(session().participants())) SoundTable.roundCompleted(p);
        plugin.arena().render();
    }

    /** Подсказка после таймаута: 1-3 слова, подходящие под шаблон и не использованные в этой игре (config: eliminationHints). */
    @Override
    public void onTurnLost(UUID player, String template, Set<String> usedWords) {
        int count = cfg().eliminationHints();
        if (count <= 0) return;
        Set<String> used = Set.copyOf(usedWords);
        List<UUID> audience = session().participants();
        plugin.async().supply(() -> plugin.dictionary().randomContaining(template, used, count), hints -> {
            if (hints.isEmpty()) return;
            String joined = String.join(", ", hints);
            for (Player p : online(audience)) chat().send(p, m().eliminationHints(), "word", joined, "template", template, "player", name(player));
        });
    }

    @Override
    public void onPlayerEliminated(UUID player, EliminationReason reason, int alivePlayers) {
        String name = name(player);
        String text = switch (reason) {
            case TIMEOUT, FORCED -> m().timeUp();
            case QUIT -> m().quitEliminated();
            case LEFT_ZONE -> m().leftZoneEliminated();
        };
        for (Player p : online(session().participants())) {
            SoundTable.eliminated(p);
            chat().send(p, text, "player", name);
        }
        Player p = Bukkit.getPlayer(player);
        if (p != null && reason != EliminationReason.QUIT) {
            effects.skull(p.getLocation());
            toSpectator(p);
        } else {
            freezer.unfreeze(player);
        }
    }

    private void toSpectator(Player p) {
        previousModes.putIfAbsent(p.getUniqueId(), p.getGameMode());
        freezer.unfreeze(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        Location center = plugin.arenaCenter();
        if (center != null) p.teleport(center.clone().add(0, cfg().spectatorLift(), 0));
    }

    private void restoreSpectator(UUID id) {
        GameMode mode = previousModes.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        if (mode != null && p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(mode);
        Location center = plugin.arenaCenter();
        if (center != null && mode != null) p.teleport(center.clone().add(0, 1, 0));
    }

    @Override
    public void onRoundAfterElimination(int round) {
        for (Player p : online(session().participants())) chat().send(p, m().newRound());
        plugin.arena().render();
    }

    @Override
    public void onGameEnded(UUID winner, int usedWords, List<UUID> participants) {
        List<Player> online = online(participants);
        if (winner != null) {
            String name = name(winner);
            for (Player p : online) {
                chat().send(p, m().winner(), "player", name);
                SoundTable.victory(p);
            }
            Player w = Bukkit.getPlayer(winner);
            String winnerName = w != null ? w.getName() : name;
            plugin.async().run(() -> plugin.winners().addWin(winner, winnerName));
            plugin.papiCacheInvalidate();
        } else {
            for (Player p : online) {
                chat().send(p, m().gameOver());
                SoundTable.noWinner(p);
            }
        }
        for (Player p : online) chat().send(p, m().wordsUsed(), "count", usedWords);
        cleanup(participants);
        plugin.arena().render();
    }

    @Override
    public void onSpectatorLeft(UUID player) {
        restoreSpectator(player);
        freezer.unfreeze(player);
    }

    /** Снять всё игровое состояние с игроков (конец игры или выключение). */
    public void cleanup(List<UUID> participants) {
        stopLeash();
        bossBar.remove();
        if (word != null) {
            word.finish();
            word = null;
        }
        for (UUID id : participants) restoreSpectator(id);
        for (UUID id : previousModes.keySet().toArray(new UUID[0])) restoreSpectator(id);
        freezer.unfreezeAll();
    }

    /** Выключение плагина: убрать сущности без эффектов. */
    public void shutdown() {
        stopLeash();
        bossBar.remove();
        if (word != null) {
            word.remove();
            word = null;
        }
        for (UUID id : previousModes.keySet().toArray(new UUID[0])) restoreSpectator(id);
        freezer.unfreezeAll();
    }

    /** Игрок вернулся на сервер с замороженными атрибутами (вышел во время игры) - вернуть. */
    public void onPlayerJoined(Player p) {
        if (freezer.isFrozen(p.getUniqueId())) freezer.unfreeze(p.getUniqueId());
        if (previousModes.containsKey(p.getUniqueId()) && !session().isParticipant(p.getUniqueId())) restoreSpectator(p.getUniqueId());
    }

    // ------------------------------------------------------------------ наблюдатели

    private void startLeash() {
        stopLeash();
        leashTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location center = plugin.arenaCenter();
            if (center == null || !session().isRunning()) return;
            double leash = cfg().spectatorLeash();
            for (Player p : online(session().spectators())) {
                if (!p.getWorld().equals(center.getWorld()) || p.getLocation().distanceSquared(center) > leash * leash) {
                    p.teleport(center.clone().add(0, cfg().spectatorLift(), 0));
                    chat().send(p, m().spectatorTooFar());
                }
            }
        }, 20L, 20L);
    }

    private void stopLeash() {
        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
    }

    // ------------------------------------------------------------------ утилиты

    private static List<Player> online(List<UUID> ids) {
        return ids.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
    }

    public static String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }
}
