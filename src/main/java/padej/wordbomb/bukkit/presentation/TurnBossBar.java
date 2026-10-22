package padej.wordbomb.bukkit.presentation;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Боссбар игры: «ник | шаблон | Nс», зелёный, сплошной. Показывается всем участникам. */
public final class TurnBossBar {

    private BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();

    public void create(Component title) {
        remove();
        bar = BossBar.bossBar(title, 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    }

    public void show(Player player) {
        if (bar == null || player == null) return;
        player.showBossBar(bar);
        viewers.add(player.getUniqueId());
    }

    public void hide(Player player) {
        if (bar == null || player == null) return;
        player.hideBossBar(bar);
        viewers.remove(player.getUniqueId());
    }

    public void update(Component title, float progress) {
        if (bar == null) return;
        bar.name(title);
        bar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    public void remove() {
        if (bar == null) return;
        for (UUID id : viewers) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        viewers.clear();
        bar = null;
    }

    public boolean active() {
        return bar != null;
    }
}
