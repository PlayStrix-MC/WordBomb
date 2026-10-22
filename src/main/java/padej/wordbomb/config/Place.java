package padej.wordbomb.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Точка в мире: имя мира, координаты и поворот. Записывается в {@code config.jmc} как таблица. */
public record Place(String world, double x, double y, double z, float yaw, float pitch) {

    /** Представление для {@code Config.update}: таблица с теми же ключами. */
    public Map<String, Object> toTable() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("world", world);
        t.put("x", x);
        t.put("y", y);
        t.put("z", z);
        t.put("yaw", yaw);
        t.put("pitch", pitch);
        return t;
    }

    public Place withYawPitch(float newYaw, float newPitch) {
        return new Place(world, x, y, z, newYaw, newPitch);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s (%.1f, %.1f, %.1f) yaw=%.0f pitch=%.0f", world, x, y, z, yaw, pitch);
    }
}
