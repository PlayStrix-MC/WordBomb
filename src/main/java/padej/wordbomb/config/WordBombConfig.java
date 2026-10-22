package padej.wordbomb.config;

import me.padej.jumper.interp.Config;
import padej.wordbomb.core.Rules;

/**
 * Настройки плагина ({@code plugins/WordBomb/config.jmc}). Файл создаётся из {@link #DEFAULTS},
 * новые поля дописываются при обновлении, правки сервера не трогаются.
 */
@Config.Comment("WordBomb settings. Edit and run /wb reload.")
public record WordBombConfig(
        @Config.Comment("Game center: the zone and the circle of players are built around it")
        Place arena,
        @Config.Comment("Public screen placement (persistent DisplayLib screen wordbomb_arena); yaw = where the screen faces (towards the viewers)")
        Place screen,
        @Config.Comment("Zone radius: leaving it removes the player from the queue or eliminates them")
        int zoneRadius,
        @Config.Comment("Radius of the circle players are placed on at game start")
        int circleRadius,
        @Config.Comment("Spectators farther than this from the center are teleported back")
        int spectatorLeash,
        @Config.Comment("Height above the center where eliminated players are moved")
        int spectatorLift,
        @Config.Comment("Height above the center where the current word hangs")
        int wordHeight,
        @Config.Comment("Scale of the word display")
        double wordScale,
        @Config.Comment("Freeze players (speed and jump) while the game runs")
        boolean freezePlayers,
        @Config.Comment("Seconds of countdown after minPlayers join")
        int countdownSeconds,
        @Config.Comment("Turn length = max(minTurnSeconds, turnBaseSeconds - round)")
        int turnBaseSeconds,
        int minTurnSeconds,
        @Config.Comment("Warn the player (tick sound) during the last N seconds of the turn")
        int lastSecondsWarning,
        @Config.Comment("Chance of a two-letter template (otherwise three)")
        double twoLetterChance,
        @Config.Comment("How many recent templates are not repeated, and how many attempts to find a new one")
        int templateHistory,
        int templateAttempts,
        int minPlayers,
        @Config.Comment("Folder with the old plugin's txt files (relative to plugins/) imported into the database on first run")
        String legacyDataFolder) {

    public static final WordBombConfig DEFAULTS = new WordBombConfig(
            new Place("world", -451.5, 71.0, 6459.5, 0f, 0f),
            new Place("world", -451.0, 79.0, 6489.0, -180f, 0f),
            50, 10, 20, 10, 8, 3.0, true,
            20, 20, 5, 5, 0.9, 20, 50, 2,
            "word_bomb");

    /** Правила игры для ядра. */
    public Rules rules() {
        return new Rules(minPlayers, countdownSeconds, turnBaseSeconds, minTurnSeconds, lastSecondsWarning,
                twoLetterChance, templateHistory, templateAttempts);
    }
}
