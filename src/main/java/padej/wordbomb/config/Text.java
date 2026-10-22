package padej.wordbomb.config;

import java.util.Map;

/** Подстановка плейсхолдеров {@code {name}} в тексты из {@link Messages}. */
public final class Text {

    private Text() {
    }

    /** {@code fill("Ходит {player}", "player", "Steve")} - пары ключ/значение. */
    public static String fill(String template, Object... keyValues) {
        if (template == null || keyValues.length == 0) return template;
        String out = template;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out = out.replace("{" + keyValues[i] + "}", String.valueOf(keyValues[i + 1]));
        }
        return out;
    }

    public static String fill(String template, Map<String, ?> values) {
        if (template == null || values.isEmpty()) return template;
        String out = template;
        for (Map.Entry<String, ?> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }
}
