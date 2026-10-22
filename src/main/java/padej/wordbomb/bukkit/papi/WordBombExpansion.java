package padej.wordbomb.bukkit.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.Text;
import padej.wordbomb.storage.WinnersRepo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI: {@code %wordbomb_top_N%} - «ник - победы» N-го места, {@code %wordbomb_wins%} - победы игрока.
 * Топ и победы кешируются и обновляются в фоне (PAPI спрашивает в главном потоке, базу там не трогаем).
 */
public final class WordBombExpansion extends PlaceholderExpansion {

    private static final long REFRESH_MS = 30_000;

    private final WordBombPlugin plugin;
    private volatile List<WinnersRepo.Entry> top = List.of();
    private final Map<UUID, Integer> wins = new ConcurrentHashMap<>();
    private volatile long refreshedAt;
    private volatile boolean refreshing;

    public WordBombExpansion(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "wordbomb";
    }

    @Override
    public String getAuthor() {
        return "Padej_";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /** Сбросить кеш (после победы). */
    public void invalidate() {
        refreshedAt = 0;
        wins.clear();
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        refreshIfStale();
        String p = params.toLowerCase();
        if (p.startsWith("top_")) {
            int n;
            try {
                n = Integer.parseInt(p.substring(4));
            } catch (NumberFormatException e) {
                return null;
            }
            List<WinnersRepo.Entry> t = top;
            if (n < 1 || n > t.size()) return legacy(plugin.chat().messages().papiNoData());
            WinnersRepo.Entry e = t.get(n - 1);
            return legacy(Text.fill(plugin.chat().messages().papiTopLine(), "player", e.name(), "wins", e.wins()));
        }
        if (p.equals("wins")) {
            if (player == null) return "0";
            UUID id = player.getUniqueId();
            Integer cached = wins.get(id);
            if (cached == null) {
                plugin.async().run(() -> wins.put(id, plugin.winners().wins(id)));
                return "0";
            }
            return String.valueOf(cached);
        }
        return null;
    }

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (refreshing || now - refreshedAt < REFRESH_MS) return;
        refreshing = true;
        plugin.async().run(() -> {
            try {
                top = plugin.winners().top(10);
                refreshedAt = System.currentTimeMillis();
            } finally {
                refreshing = false;
            }
        });
    }

    private static String legacy(String ampersand) {
        return ampersand.replace('&', '§');
    }
}
