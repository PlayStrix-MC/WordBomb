package padej.wordbomb.bukkit.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import padej.wordbomb.bukkit.Chat;
import padej.wordbomb.bukkit.arena.ConfigScreen;
import padej.wordbomb.bukkit.arena.ModerationScreen;
import padej.wordbomb.config.ConfigLoader;
import padej.wordbomb.bukkit.WordBombPlugin;
import padej.wordbomb.config.Messages;
import padej.wordbomb.config.Place;
import padej.wordbomb.storage.ActionLogRepo;
import padej.wordbomb.storage.Moderation;
import padej.wordbomb.storage.VoteRepo;
import padej.wordbomb.storage.WinnersRepo;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Команда {@code /wb}: очередь, словарь, модерация, настройки. Работа с базой - вне главного потока. */
public final class WbCommand implements CommandExecutor {

    public static final int TOP_SIZE = 10;

    private final WordBombPlugin plugin;

    public WbCommand(WordBombPlugin plugin) {
        this.plugin = plugin;
    }

    private Chat chat() {
        return plugin.chat();
    }

    private Messages m() {
        return plugin.chat().messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            chat().send(sender, m().unknownCommand());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> eventer(sender, () -> asPlayer(sender, p -> plugin.api().join(p)));
            case "leave" -> eventer(sender, () -> asPlayer(sender, p -> plugin.api().leave(p)));
            case "start" -> eventer(sender, () -> asPlayer(sender, p -> plugin.api().start(p)));
            case "top" -> top(sender);
            case "offer" -> asPlayer(sender, p -> offer(p, arg(args, 1)));
            case "report" -> asPlayer(sender, p -> report(p, arg(args, 1)));
            case "offer-list" -> voteList(sender, plugin.suggestions(), m().offerListHeader());
            case "report-list" -> voteList(sender, plugin.reports(), m().reportListHeader());
            case "accept" -> eventer(sender, () -> moderate(sender, Moderation.Kind.ACCEPT, arg(args, 1)));
            case "block" -> eventer(sender, () -> moderate(sender, Moderation.Kind.BLOCK, arg(args, 1)));
            case "add" -> eventer(sender, () -> moderate(sender, Moderation.Kind.ADD, arg(args, 1)));
            case "remove" -> eventer(sender, () -> moderate(sender, Moderation.Kind.REMOVE, arg(args, 1)));
            case "search" -> eventer(sender, () -> search(sender, args));
            case "config" -> eventer(sender, () -> asPlayer(sender, p -> config(p, args)));
            case "mod" -> eventer(sender, () -> asPlayer(sender, p -> mod(p, args)));
            case "reload" -> eventer(sender, () -> reload(sender));
            default -> chat().send(sender, m().unknownCommand());
        }
        return true;
    }

    // ------------------------------------------------------------------ игроки

    private void top(CommandSender sender) {
        plugin.async().supply(() -> plugin.winners().top(TOP_SIZE), top -> {
            chat().sendRaw(sender, m().topHeader());
            if (top.isEmpty()) {
                chat().sendRaw(sender, m().emptyList());
                return;
            }
            int place = 1;
            for (WinnersRepo.Entry e : top) {
                chat().sendRaw(sender, m().topLine(), "place", place++, "player", e.name(), "wins", e.wins());
            }
        });
    }

    private void offer(Player player, String wordArg) {
        String word = Moderation.normalizedOrNull(wordArg);
        if (word == null) {
            chat().send(player, m().onlyRussianLetters());
            return;
        }
        UUID id = player.getUniqueId();
        plugin.async().supply(() -> {
            if (plugin.dictionary().contains(word)) return false;
            return plugin.suggestions().vote(word, id);
        }, ok -> chat().send(player, ok ? m().offered() : m().alreadyInDictionaryOrOffered()));
    }

    private void report(Player player, String wordArg) {
        String word = Moderation.normalizedOrNull(wordArg);
        if (word == null) {
            chat().send(player, m().onlyRussianLetters());
            return;
        }
        UUID id = player.getUniqueId();
        plugin.async().supply(() -> {
            if (!plugin.dictionary().contains(word)) return false;
            return plugin.reports().vote(word, id);
        }, ok -> chat().send(player, ok ? m().reported() : m().notInDictionaryOrReported()));
    }

    private void voteList(CommandSender sender, VoteRepo repo, String header) {
        plugin.async().supply(() -> repo.top(TOP_SIZE), top -> {
            chat().sendRaw(sender, header);
            if (top.isEmpty()) {
                chat().sendRaw(sender, m().emptyList());
                return;
            }
            int place = 1;
            for (VoteRepo.Entry e : top) {
                chat().sendRaw(sender, m().voteLine(), "place", place++, "word", e.word(), "votes", e.votes());
            }
        });
    }

    // ------------------------------------------------------------------ админ

    private void moderate(CommandSender sender, Moderation.Kind kind, String wordArg) {
        String word = Moderation.normalizedOrNull(wordArg);
        if (word == null) {
            chat().send(sender, m().onlyRussianLetters());
            return;
        }
        String admin = sender.getName();
        plugin.async().supply(() -> plugin.moderation().apply(kind, word, admin), result -> {
            String text = switch (result) {
                case OK -> switch (kind) {
                    case ACCEPT -> m().accepted();
                    case BLOCK -> m().blocked();
                    case ADD -> m().added();
                    case REMOVE -> m().removed();
                };
                case INVALID_WORD -> m().onlyRussianLetters();
                case NOT_IN_LIST -> kind == Moderation.Kind.ACCEPT ? m().notInOffers() : m().notInReports();
                case ALREADY_IN_DICTIONARY -> m().alreadyInDictionary();
                case NOT_IN_DICTIONARY -> m().notInDictionaryAdmin();
            };
            chat().send(sender, text, "word", word);
            if (sender instanceof Player p) {
                plugin.configScreen().refreshIfOpen(p);
                plugin.moderationScreen().refreshIfOpen(p);
            }
        });
    }

    /** {@code /wb search [дата] [время] [игрок] [действие] [слово]}, {@code none} - поле не фильтруется. */
    private void search(CommandSender sender, String[] args) {
        String date = filter(args, 1);
        String time = filter(args, 2);
        String player = filter(args, 3);
        String action = filter(args, 4);
        String word = filter(args, 5);
        plugin.async().supply(() -> plugin.actionLog().search(date, time, player, action, word), entries -> {
            if (entries.isEmpty()) {
                chat().sendRaw(sender, m().searchEmpty());
                return;
            }
            chat().sendRaw(sender, m().searchHeader(), "count", entries.size());
            for (ActionLogRepo.Entry e : entries) {
                chat().sendRaw(sender, m().searchLine(), "date", e.date(), "time", e.time(), "player", e.player(),
                        "action", colorAction(e.action()), "word", e.word());
            }
        });
    }

    private static String colorAction(String action) {
        String color = switch (action) {
            case ActionLogRepo.ACCEPT -> "&a";
            case ActionLogRepo.BLOCK -> "&c";
            case ActionLogRepo.REMOVE -> "&4";
            case ActionLogRepo.ADD -> "&2";
            default -> "&f";
        };
        return color + "[" + action + "]";
    }

    private void config(Player player, String[] args) {
        String what = arg(args, 1);
        if (what == null) {
            if (!plugin.configScreen().open(player)) chat().send(player, m().configScreenFailed());
            return;
        }
        switch (what.toLowerCase(Locale.ROOT)) {
            case "setcenter" -> setCenter(player);
            case "sethologram" -> setHologram(player, args);
            case "set" -> setValue(player, arg(args, 2), arg(args, 3));
            default -> chat().send(player, m().unknownCommand());
        }
    }

    /** {@code /wb mod [tab <offers|reports> | page <next|prev|N>]}: экран модерации словаря. */
    private void mod(Player player, String[] args) {
        String what = arg(args, 1);
        if (what == null) {
            plugin.moderationScreen().open(player);
            return;
        }
        switch (what.toLowerCase(Locale.ROOT)) {
            case "tab" -> {
                ModerationScreen.Kind kind = ModerationScreen.Kind.parse(arg(args, 2));
                if (kind == null) chat().send(player, m().unknownCommand());
                else plugin.moderationScreen().tab(player, kind);
            }
            case "page" -> plugin.moderationScreen().page(player, arg(args, 2));
            default -> chat().send(player, m().unknownCommand());
        }
    }

    /** {@code /wb config set <ключ> <значение>}: проверка до записи, затем reload и перерисовка экрана настроек. */
    private void setValue(Player player, String key, String value) {
        if (key == null || value == null) {
            chat().send(player, m().configKeys(), "word", String.join(", ", ConfigScreen.keys()));
            return;
        }
        Object stored;
        try {
            stored = plugin.configLoader().setValue(plugin.config(), key, value);
            plugin.reload();
        } catch (ConfigLoader.BadValue e) {
            chat().send(player, m().configBadValue(), "word", key, "count", value);
            chat().send(player, m().configKeys(), "word", String.join(", ", ConfigScreen.keys()));
            return;
        } catch (RuntimeException e) {
            chat().send(player, m().reloadFailed(), "word", e.getMessage());
            return;
        }
        chat().send(player, m().configSet(), "word", key, "count", stored);
        plugin.configScreen().refreshIfOpen(player);
    }

    /** Центр арены = блок, на который смотрит админ (до 5 блоков), +(0.5, 1, 0.5). */
    private void setCenter(Player player) {
        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType().isAir()) {
            chat().send(player, m().lookAtBlock());
            return;
        }
        Location loc = block.getLocation().add(0.5, 1, 0.5);
        Place place = new Place(player.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), 0f, 0f);
        try {
            plugin.configLoader().saveArena(place);
            plugin.reload();
        } catch (RuntimeException e) {
            chat().send(player, m().reloadFailed(), "word", e.getMessage());
            return;
        }
        chat().send(player, m().centerSet(), "x", fmt(loc.getX()), "y", fmt(loc.getY()), "z", fmt(loc.getZ()));
        // подсказка над новой точкой - видит только админ
        plugin.presenter().effects().setCenter(loc, player);
    }

    /**
     * {@code /wb config setHologram [x y z [yaw pitch]]}. Без аргументов: экран ставится в двух блоках перед админом
     * по его взгляду (yaw округляется до кратного 90°, позиция прилипает к центру блока) и смотрит на админа.
     * {@code yaw} в конфиге - куда смотрит экран, то есть направление взгляда игрока + 180°.
     */
    private void setHologram(Player player, String[] args) {
        World world = player.getWorld();
        Place place;
        if (args.length >= 5) {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                float yaw = args.length >= 6 ? Float.parseFloat(args[5]) : snapYaw(player.getLocation().getYaw() + 180f);
                float pitch = args.length >= 7 ? Float.parseFloat(args[6]) : 0f;
                place = new Place(world.getName(), x, y, z, yaw, pitch);
            } catch (NumberFormatException e) {
                chat().send(player, m().badNumbers());
                return;
            }
        } else {
            Location eye = player.getEyeLocation();
            float lookYaw = snapYaw(eye.getYaw());
            Location dir = eye.clone();
            dir.setYaw(lookYaw);
            dir.setPitch(0f);
            Location l = eye.clone().add(dir.getDirection().multiply(2));
            place = new Place(world.getName(),
                    Math.floor(l.getX()) + 0.5, Math.floor(l.getY()) + 0.5, Math.floor(l.getZ()) + 0.5,
                    snapYaw(lookYaw + 180f), 0f);
        }
        try {
            plugin.configLoader().saveScreen(place);
            plugin.reload();
        } catch (RuntimeException e) {
            chat().send(player, m().reloadFailed(), "word", e.getMessage());
            return;
        }
        chat().send(player, m().hologramSet(), "x", fmt(place.x()), "y", fmt(place.y()), "z", fmt(place.z()),
                "yaw", fmt(place.yaw()), "pitch", fmt(place.pitch()));
    }

    /** Ближайший yaw, кратный 90°, в диапазоне [-180, 180). */
    static float snapYaw(float yaw) {
        float snapped = Math.round(yaw / 90f) * 90f;
        snapped = ((snapped % 360f) + 540f) % 360f - 180f;
        return snapped;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reload();
            chat().send(sender, m().reloaded());
        } catch (RuntimeException e) {
            chat().send(sender, m().reloadFailed(), "word", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ утилиты

    private void asPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player p) {
            action.accept(p);
        } else {
            chat().send(sender, m().playersOnly());
        }
    }

    private void eventer(CommandSender sender, Runnable action) {
        if (Permissions.isEventer(sender)) {
            action.run();
        } else {
            chat().send(sender, m().noPermission());
        }
    }

    private static String arg(String[] args, int index) {
        return args.length > index ? args[index] : null;
    }

    private static String filter(String[] args, int index) {
        String v = arg(args, index);
        return v == null || v.equalsIgnoreCase("none") ? null : v;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static List<String> subcommandsFor(CommandSender sender) {
        List<String> common = List.of("offer", "report", "offer-list", "report-list", "top");
        if (!Permissions.isEventer(sender)) return common;
        List<String> all = new java.util.ArrayList<>(common);
        all.addAll(List.of("join", "leave", "start", "accept", "block", "add", "remove", "search", "config", "mod", "reload"));
        return all;
    }
}
