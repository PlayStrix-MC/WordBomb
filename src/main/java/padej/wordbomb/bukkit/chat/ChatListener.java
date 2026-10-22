package padej.wordbomb.bukkit.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.bukkit.presentation.SoundTable;
import padej.wordbomb.config.Messages;
import padej.wordbomb.core.GameSession;
import padej.wordbomb.core.WordValidator;

import java.util.Optional;
import java.util.UUID;

/**
 * Слова из чата. Перехватываются только сообщения живых участников идущей игры (чат остальных не трогаем).
 * В асинхронном потоке события: раскладка, кириллица, шаблон, словарь (SQLite). Ход делается в главном потоке.
 */
public final class ChatListener implements Listener {

    private final WordBombPlugin plugin;

    public ChatListener(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        GameSession.Snapshot snapshot = plugin.session().snapshot();
        if (snapshot.state() != GameSession.State.RUNNING || !snapshot.isAlive(id)) return;

        event.setCancelled(true);
        Messages m = plugin.chat().messages();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        Optional<String> prepared = WordValidator.prepare(raw);
        if (prepared.isEmpty()) {
            plugin.chat().send(player, m.wordNotFit());
            return;
        }
        String word = prepared.get();
        if (!id.equals(snapshot.currentPlayer())) {
            plugin.chat().send(player, m.notYourTurn());
            return;
        }
        if (!WordValidator.fitsTemplate(word, snapshot.template())) {
            plugin.chat().send(player, m.wordNotFit());
            return;
        }
        // словарь - в этом же (асинхронном) потоке: главный поток не ждёт SQLite
        boolean inDictionary;
        try {
            inDictionary = plugin.dictionary().contains(word);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Проверка слова '" + word + "' не удалась: " + e.getMessage());
            return;
        }
        if (!inDictionary) {
            plugin.chat().send(player, m.notInDictionary());
            return;
        }
        plugin.async().toMain(() -> submit(player, word));
    }

    private void submit(Player player, String word) {
        Messages m = plugin.chat().messages();
        switch (plugin.session().submitValidatedWord(player.getUniqueId(), word)) {
            case ACCEPTED, NOT_ACTIVE -> {
            }
            case NOT_YOUR_TURN -> plugin.chat().send(player, m.notYourTurn());
            case NOT_FIT, NOT_IN_DICTIONARY -> plugin.chat().send(player, m.wordNotFit());
            case ALREADY_USED -> {
                SoundTable.wordRepeated(player);
                plugin.chat().send(player, m.wordAlreadyUsed());
            }
        }
    }
}
