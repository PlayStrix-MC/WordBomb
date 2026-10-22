package padej.wordbomb.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.storage.ActionLogRepo;
import padej.wordbomb.storage.VoteRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Автодополнение {@code /wb}. Списки из базы небольшие (топ-10, даты журнала) и читаются синхронно. */
public final class WbTabCompleter implements TabCompleter {

    private final WordBombPlugin plugin;

    public WbTabCompleter(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(WbCommand.subcommandsFor(sender), args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = Permissions.isEventer(sender);
        return switch (sub) {
            case "accept" -> admin && args.length == 2 ? filter(words(plugin.suggestions()), args[1]) : List.of();
            case "block" -> admin && args.length == 2 ? filter(words(plugin.reports()), args[1]) : List.of();
            case "add", "remove", "offer", "report" -> args.length == 2 ? List.of("<слово>") : List.of();
            case "config" -> admin ? config(args) : List.of();
            case "search" -> admin ? search(args) : List.of();
            default -> List.of();
        };
    }

    private List<String> config(String[] args) {
        if (args.length == 2) return filter(List.of("set", "setCenter", "setHologram"), args[1]);
        if (args.length == 3 && args[1].equalsIgnoreCase("set")) return filter(padej.wordbomb.bukkit.arena.ConfigScreen.keys(), args[2]);
        return List.of();
    }

    private List<String> search(String[] args) {
        ActionLogRepo log = plugin.actionLog();
        return switch (args.length) {
            case 2 -> filter(withNone(safe(log::dates)), args[1]);
            case 3 -> filter(List.of("none", "HH:mm:ss"), args[2]);
            case 4 -> filter(withNone(safe(log::players)), args[3]);
            case 5 -> filter(List.of("none", ActionLogRepo.ACCEPT, ActionLogRepo.BLOCK, ActionLogRepo.REMOVE, ActionLogRepo.ADD), args[4]);
            case 6 -> filter(List.of("none", "<слово>"), args[5]);
            default -> List.of();
        };
    }

    private List<String> words(VoteRepo repo) {
        return safe(() -> repo.top(WbCommand.TOP_SIZE).stream().map(VoteRepo.Entry::word).toList());
    }

    private static List<String> withNone(List<String> list) {
        List<String> out = new ArrayList<>(list.size() + 1);
        out.add("none");
        out.addAll(list);
        return out;
    }

    private List<String> safe(java.util.function.Supplier<List<String>> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            plugin.getLogger().fine("tab-complete: " + e.getMessage());
            return List.of();
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
