package padej.wordbomb.bukkit.command;

import org.bukkit.command.CommandSender;

/**
 * Права. Одно право {@link #EVENTER} даёт всё служебное: очередь командами ({@code /wb join|leave|start}),
 * модерацию словаря, настройки, перезагрузку. Обычные игроки вступают в игру кнопками публичного экрана,
 * а из команд им доступны только {@code offer}, {@code report}, {@code offer-list}, {@code report-list}, {@code top}.
 */
public final class Permissions {

    public static final String EVENTER = "wordbomb.eventer";

    private Permissions() {
    }

    public static boolean isEventer(CommandSender sender) {
        return sender.hasPermission(EVENTER);
    }
}
