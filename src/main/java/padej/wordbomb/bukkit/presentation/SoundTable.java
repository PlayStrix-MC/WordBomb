package padej.wordbomb.bukkit.presentation;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Звуки игры (таблица из CLAUDE.md §6.8). */
public final class SoundTable {

    private SoundTable() {
    }

    /** Последние секунды хода - текущему игроку. */
    public static void tick(Player p) {
        play(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
    }

    /** Верное слово - всем. */
    public static void wordAccepted(Player p) {
        play(p, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    /** Повтор слова - игроку. */
    public static void wordRepeated(Player p) {
        play(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    /** Конец раунда - всем. */
    public static void roundCompleted(Player p) {
        play(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    }

    /** Выбывание - всем. */
    public static void eliminated(Player p) {
        play(p, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }

    /** Победа - всем. */
    public static void victory(Player p) {
        play(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** Игра без победителя - всем. */
    public static void noWinner(Player p) {
        play(p, Sound.ENTITY_WITHER_DEATH, 1f, 1f);
    }

    private static void play(Player p, Sound sound, float volume, float pitch) {
        if (p != null && p.isOnline()) p.playSound(p.getLocation(), sound, volume, pitch);
    }
}
