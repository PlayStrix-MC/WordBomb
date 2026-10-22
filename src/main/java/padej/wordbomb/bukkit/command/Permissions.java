package padej.wordbomb.bukkit.command;

import org.bukkit.command.CommandSender;

/** Права: {@code wordbomb.admin}; старое {@code event_manager.event} принимается как алиас. */
public final class Permissions {

    public static final String ADMIN = "wordbomb.admin";
    public static final String LEGACY_ADMIN = "event_manager.event";

    private Permissions() {
    }

    public static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN) || sender.hasPermission(LEGACY_ADMIN);
    }
}
