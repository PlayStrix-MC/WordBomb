package padej.wordbomb.bukkit.effects;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import padej.displayLib.particles.Particles;

import java.util.logging.Logger;

/**
 * Текущее слово над центром арены: TextDisplay с градиентом, жирный, яркость 15/15. При смене слова
 * старое рассыпается на буквы ({@code TextShatter} DisplayLib с эффектом {@link EffectPlayer#LETTERS})
 * и вспыхивают искры ({@link EffectPlayer#SPARKLE}).
 */
public final class WordDisplay {

    private final Logger log;
    private final EffectPlayer effects;
    private final Location at;
    private final double scale;
    private TextDisplay display;
    private String current;

    public WordDisplay(Logger log, EffectPlayer effects, Location at, double scale) {
        this.log = log;
        this.effects = effects;
        this.at = at.clone();
        this.scale = scale;
    }

    /** Показать новое слово; предыдущее взрывается на буквы. */
    public void show(String word) {
        shatterCurrent();
        current = word;
        if (at.getWorld() == null) return;
        Component text = Gradient.text(word, Gradient.WORD_FROM, Gradient.WORD_TO, true);
        display = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            float s = (float) scale;
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
        });
    }

    private void shatterCurrent() {
        if (display != null) {
            display.remove();
            display = null;
        }
        if (current == null || at.getWorld() == null) return;
        String word = current;
        current = null;
        // искры - в середине слова (центр картинки буквы на полстроки выше низа текста, как у TextShatter)
        effects.play(EffectPlayer.SPARKLE, at.clone().add(0, 0.1 * scale, 0));
        if (!effects.available(EffectPlayer.LETTERS)) return;
        try {
            Particles.shatter(Gradient.text(word, Gradient.WORD_FROM, Gradient.WORD_TO, true))
                    .at(at)
                    .effect(EffectPlayer.LETTERS)
                    .hold(0)
                    .scale(scale)
                    .play();
        } catch (RuntimeException e) {
            log.warning("Не удалось рассыпать слово: " + e.getMessage());
        }
    }

    /** Рассыпать текущее слово и больше ничего не показывать (конец игры). */
    public void finish() {
        shatterCurrent();
    }

    /** Убрать слово без эффекта (выключение). */
    public void remove() {
        if (display != null) {
            display.remove();
            display = null;
        }
        current = null;
    }

    public String current() {
        return current;
    }
}
