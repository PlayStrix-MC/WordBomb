package padej.wordbomb.bukkit.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.core.GameListener;
import padej.wordbomb.core.GameSession;

import java.util.UUID;

/**
 * Зона арены ({@code zoneRadius} вокруг центра), проверка раз в секунду: игрок из очереди, вышедший за зону,
 * покидает очередь; живой игрок во время игры - выбывает.
 */
public final class ZoneTask implements Runnable {

    private final WordBombPlugin plugin;

    public ZoneTask(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        GameSession session = plugin.session();
        if (session.state() == GameSession.State.WAITING && session.queue().isEmpty()) return;
        Location center = plugin.arenaCenter();
        if (center == null) return;
        double r = plugin.config().zoneRadius();
        double r2 = r * r;
        for (UUID id : session.queue()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean outside = !p.getWorld().equals(center.getWorld()) || p.getLocation().distanceSquared(center) > r2;
            if (!outside) continue;
            if (session.isRunning()) {
                session.eliminate(id, GameListener.EliminationReason.LEFT_ZONE);
            } else if (session.leave(id) == GameSession.LeaveResult.LEFT) {
                plugin.chat().send(p, plugin.chat().messages().leftZoneQueue());
            }
        }
    }
}
