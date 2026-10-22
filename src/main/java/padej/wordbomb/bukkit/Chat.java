package padej.wordbomb.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import padej.wordbomb.config.Messages;
import padej.wordbomb.config.Text;

import java.util.function.Supplier;

/** Отправка текстов из {@link Messages}: префикс, {@code &}-коды, плейсхолдеры. */
public final class Chat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Supplier<Messages> messages;

    public Chat(Supplier<Messages> messages) {
        this.messages = messages;
    }

    public Messages messages() {
        return messages.get();
    }

    /** Строка с {@code &}-кодами → компонент. */
    public static Component component(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText);
    }

    /** Сообщение с префиксом. {@code keyValues} - пары плейсхолдер/значение. */
    public void send(CommandSender to, String template, Object... keyValues) {
        if (to == null || template == null || template.isEmpty()) return;
        to.sendMessage(component(messages().prefix() + Text.fill(template, keyValues)));
    }

    /** Сообщение без префикса. */
    public void sendRaw(CommandSender to, String template, Object... keyValues) {
        if (to == null || template == null || template.isEmpty()) return;
        to.sendMessage(component(Text.fill(template, keyValues)));
    }

    /** Компонент без префикса (боссбар, экран). */
    public Component format(String template, Object... keyValues) {
        return component(Text.fill(template, keyValues));
    }
}
