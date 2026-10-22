package padej.wordbomb.bukkit.effects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Посимвольный градиент текста между двумя цветами (слово в центре арены: {@code #FFCC45 → #FF6545}). */
public final class Gradient {

    public static final TextColor WORD_FROM = TextColor.fromHexString("#FFCC45");
    public static final TextColor WORD_TO = TextColor.fromHexString("#FF6545");

    private Gradient() {
    }

    public static Component text(String text, TextColor from, TextColor to, boolean bold) {
        Component out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n <= 1 ? 0f : (float) i / (n - 1);
            Component ch = Component.text(text.charAt(i), lerp(from, to, t));
            if (bold) ch = ch.decorate(TextDecoration.BOLD);
            out = out.append(ch);
        }
        return out;
    }

    public static TextColor lerp(TextColor a, TextColor b, float t) {
        int r = Math.round(a.red() + (b.red() - a.red()) * t);
        int g = Math.round(a.green() + (b.green() - a.green()) * t);
        int bl = Math.round(a.blue() + (b.blue() - a.blue()) * t);
        return TextColor.color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
