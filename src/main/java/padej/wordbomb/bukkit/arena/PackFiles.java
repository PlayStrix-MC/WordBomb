package padej.wordbomb.bukkit.arena;

import org.bukkit.plugin.java.JavaPlugin;
import padej.displayLib.DisplayLib;
import padej.displayLib.pack.ContentPack;
import padej.displayLib.pack.ContentPacks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Файлы DisplayLib плагина - в его собственной папке ({@code plugins/WordBomb/screens|scripts|particles} и
 * {@code scripts.jma}), которая зарегистрирована в DisplayLib как набор содержимого ({@link ContentPacks}).
 * Скрипты экранов WordBomb подчиняются {@code plugins/WordBomb/scripts.jma}, а не политике DisplayLib.
 *
 * <p>Прежние версии клали файлы в {@code plugins/DisplayLib/...}: такой файл переносится в папку WordBomb
 * (с правками пользователя), иначе DisplayLib загрузил бы свою копию с тем же id раньше нашей.</p>
 */
final class PackFiles {
    private PackFiles() {
    }

    static DisplayLib displayLib() {
        return (DisplayLib) DisplayLib.getInstance();
    }

    /** Набор WordBomb (регистрируется при первом обращении; повторно - тот же). */
    static ContentPack pack(JavaPlugin plugin) {
        return ContentPacks.register(plugin);
    }

    /**
     * Положить ресурс в {@code plugins/WordBomb/<folder>/<name>}, если файла нет: перенести из
     * {@code plugins/DisplayLib/<folder>/<name>} (прежнее место) или скопировать из jar.
     *
     * @return файл появился (создан или перенесён)
     */
    static boolean install(JavaPlugin plugin, String resource, String folder, String name) {
        Path target = pack(plugin).root().resolve(folder).resolve(name);
        if (Files.exists(target)) return false;
        try {
            Files.createDirectories(target.getParent());
            Path old = displayLib().getDataFolder().toPath().resolve(folder).resolve(name);
            if (Files.isRegularFile(old)) {
                Files.move(old, target, StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Перенесён " + old + " -> " + target);
                return true;
            }
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    plugin.getLogger().warning("Ресурс " + resource + " отсутствует в jar плагина");
                    return false;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("Создан " + target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось создать " + target + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Перечитать экран {@code id} из папки WordBomb. Сначала - из папки DisplayLib: если файл оттуда перенесён,
     * DisplayLib забывает свою копию (иначе id занят ею).
     */
    static void reloadScreen(JavaPlugin plugin, String id) {
        var registry = displayLib().getScreenRegistry();
        registry.reloadScreen(ContentPacks.main(), id);
        registry.reloadScreen(pack(plugin), id);
    }
}
