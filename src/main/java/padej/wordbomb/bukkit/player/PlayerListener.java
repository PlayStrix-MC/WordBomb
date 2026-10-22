package padej.wordbomb.bukkit.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.core.GameListener;

/** Выход с сервера: из очереди - тихо, из игры - выбывание, из наблюдателей - возврат режима. */
public final class PlayerListener implements Listener {

    private final WordBombPlugin plugin;

    public PlayerListener(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.session().removePlayer(event.getPlayer().getUniqueId(), GameListener.EliminationReason.QUIT);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.presenter().onPlayerJoined(event.getPlayer());
    }
}
