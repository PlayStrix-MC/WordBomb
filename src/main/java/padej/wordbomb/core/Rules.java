package padej.wordbomb.core;

/**
 * Правила и тайминги игры. Заполняются из {@code config.jmc}, в ядре - только числа.
 *
 * @param minPlayers         минимум игроков для старта
 * @param countdownSeconds   обратный отсчёт после набора минимума
 * @param turnBaseSeconds    база длины хода
 * @param minTurnSeconds     нижняя граница длины хода
 * @param lastSecondsWarning за сколько секунд до конца хода предупреждать игрока
 * @param twoLetterChance    вероятность шаблона из двух букв (иначе три)
 * @param templateHistory    сколько последних шаблонов не повторять
 * @param templateAttempts   сколько раз пробовать подобрать неповторяющийся шаблон
 */
public record Rules(
        int minPlayers,
        int countdownSeconds,
        int turnBaseSeconds,
        int minTurnSeconds,
        int lastSecondsWarning,
        double twoLetterChance,
        int templateHistory,
        int templateAttempts) {

    public static final Rules DEFAULT = new Rules(2, 20, 20, 5, 5, 0.9, 20, 50);

    public Rules {
        if (minPlayers < 1) throw new IllegalArgumentException("minPlayers must be >= 1");
        if (countdownSeconds < 0) throw new IllegalArgumentException("countdownSeconds must be >= 0");
        if (minTurnSeconds < 1) throw new IllegalArgumentException("minTurnSeconds must be >= 1");
        if (turnBaseSeconds < minTurnSeconds) throw new IllegalArgumentException("turnBaseSeconds must be >= minTurnSeconds");
        if (twoLetterChance < 0 || twoLetterChance > 1) throw new IllegalArgumentException("twoLetterChance must be in [0, 1]");
        if (templateHistory < 0) throw new IllegalArgumentException("templateHistory must be >= 0");
        if (templateAttempts < 1) throw new IllegalArgumentException("templateAttempts must be >= 1");
    }

    /** Длина хода в секундах: {@code max(minTurnSeconds, turnBaseSeconds - round)}. */
    public int turnSeconds(int round) {
        return Math.max(minTurnSeconds, turnBaseSeconds - round);
    }
}
