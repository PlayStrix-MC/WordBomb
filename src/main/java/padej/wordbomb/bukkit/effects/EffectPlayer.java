package padej.wordbomb.bukkit.effects;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.displayLib.particles.Particles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Эффекты частиц плагина (ресурсы {@code examples/wordbomb/particles/*.jmc}, копируются в
 * {@code plugins/WordBomb/particles/}). Об отсутствующем эффекте предупреждает один раз.
 */
public final class EffectPlayer {

    /** Череп над выбывшим игроком. */
    public static final String SKULL = "wordbomb_skull";
    /** Буквы рассыпающегося слова (TextShatter). */
    public static final String LETTERS = "wordbomb_letters";
    /** Искры при смене слова. */
    public static final String SPARKLE = "wordbomb_sparkle";
    /** Подсказка админу над новым центром арены. */
    public static final String SET_CENTER = "wordbomb_set_center";

    public static final List<String> ALL = List.of(SKULL, LETTERS, SPARKLE, SET_CENTER);

    private final Logger log;
    private final Set<String> missingReported = new HashSet<>();

    public EffectPlayer(Logger log) {
        this.log = log;
    }

    /** Есть ли эффект в DisplayLib. Если нет - одно предупреждение в лог. */
    public boolean available(String id) {
        try {
            if (Particles.effect(id) != null) return true;
        } catch (RuntimeException e) {
            log.warning("DisplayLib: эффект " + id + " недоступен: " + e.getMessage());
            return false;
        }
        if (missingReported.add(id)) {
            log.warning("Эффект частиц '" + id + "' не найден в plugins/WordBomb/particles/ - проверьте файл " + id
                    + ".jmc (плагин создаёт его при старте, если файла нет) и выполните /displaylib reload.");
        }
        return false;
    }

    /** Запустить эффект для всех. */
    public void play(String id, Location at) {
        play(id, at, null);
    }

    /** Запустить эффект; {@code owner} - игрок, для которого он запускается (эффекты с {@code viewers: "owner"}). */
    public void play(String id, Location at, Player owner) {
        if (at == null || at.getWorld() == null || !available(id)) return;
        try {
            if (owner != null) {
                Particles.play(id, at, owner);
            } else {
                Particles.play(id, at);
            }
        } catch (RuntimeException e) {
            log.warning("Эффект " + id + " не запустился: " + e.getMessage());
        }
    }

    public void skull(Location at) {
        if (at == null) return;
        play(SKULL, at.clone().add(0, 1.0, 0));
    }

    /** Надпись «Центр арены ▼» над точкой центра - только для админа. */
    public void setCenter(Location center, Player admin) {
        if (center == null) return;
        play(SET_CENTER, center.clone().add(0, 1.6, 0), admin);
    }
}
