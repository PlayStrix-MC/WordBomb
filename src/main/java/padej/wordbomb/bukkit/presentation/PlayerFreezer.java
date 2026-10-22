package padej.wordbomb.bukkit.presentation;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Заморозка игроков на время игры: скорость и прыжок в 0. При разморозке возвращаются исходные
 * значения атрибутов игрока (не захардкоженные, как в старом плагине).
 */
public final class PlayerFreezer {

    private record Saved(double speed, double jump) {}

    private final Map<UUID, Saved> saved = new HashMap<>();

    public void freeze(Player player) {
        if (player == null || saved.containsKey(player.getUniqueId())) return;
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        saved.put(player.getUniqueId(), new Saved(
                speed != null ? speed.getBaseValue() : 0.1,
                jump != null ? jump.getBaseValue() : 0.42));
        if (speed != null) speed.setBaseValue(0);
        if (jump != null) jump.setBaseValue(0);
    }

    public void unfreeze(UUID id) {
        Saved s = saved.remove(id);
        if (s == null) return;
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (speed != null) speed.setBaseValue(s.speed());
        if (jump != null) jump.setBaseValue(s.jump());
    }

    /** Разморозить всех (конец игры, выключение). */
    public void unfreezeAll() {
        for (UUID id : saved.keySet().toArray(new UUID[0])) unfreeze(id);
    }

    /** Игрок вышел с сервера замороженным: вернуть атрибуты при следующем входе. */
    public boolean isFrozen(UUID id) {
        return saved.containsKey(id);
    }
}
