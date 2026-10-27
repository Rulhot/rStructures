package ru.rulhot.rStructures.manager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.model.DatabaseSettings;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.MessageUtil;
import ru.rulhot.rStructures.util.TimeUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final @NotNull String CONFIG = "config.yml";
    private static final @NotNull String DATABASE = "database.yml";
    private static final @NotNull String ITEMS = "items.yml";
    private static final @NotNull String REGION = "region.yml";
    private static final @NotNull String WORLD = "world.yml";
    private static final @NotNull String MESSAGES = "messages.yml";
    private static final @NotNull Set<String> REGION_LIVE_PATHS = Set.of("region.flags");
    private static final @NotNull String DUNGEONS_FOLDER = "structures";
    private static final @NotNull String YAML_SUFFIX = ".yml";
    private static final @NotNull List<String> EXAMPLES = List.of(
            "structures/ground.yml", "structures/water.yml", "structures/air.yml");
    private static final @NotNull String PREFIX_KEY = "prefix";
    private static final @NotNull Pattern HEX_TAG = Pattern.compile("</?#[0-9a-fA-F]{6}>");
    private static final @NotNull String LONG_DASH = String.valueOf((char) 0x2014);
    private static final @NotNull Map<String, String> CONSOLE_SYMBOLS = Map.of("»", ">", LONG_DASH, "-", "▌", "|");

    private final @NotNull JavaPlugin plugin;
    private volatile @Nullable Snapshot snapshot;
    private final @NotNull Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    public ConfigManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull DatabaseSettings readDatabase() {
        return DatabaseSettings.from(ConfigFile.read(plugin, DATABASE, Set.of()));
    }

    public @NotNull Bundle readAll() {
        YamlConfiguration config = ConfigFile.read(plugin, CONFIG, Set.of());
        YamlConfiguration items = ConfigFile.read(plugin, ITEMS, Set.of());
        YamlConfiguration world = ConfigFile.read(plugin, WORLD, Set.of());
        return new Bundle(
                config,
                items,
                ConfigFile.read(plugin, REGION, REGION_LIVE_PATHS),
                world,
                ConfigFile.read(plugin, MESSAGES, Set.of()),
                readDungeons());
    }

    private @NotNull Map<String, YamlConfiguration> readDungeons() {
        File folder = new File(plugin.getDataFolder(), DUNGEONS_FOLDER);
        if (!folder.isDirectory()) {
            EXAMPLES.forEach(path -> plugin.saveResource(path, false));
        }
        File[] files = folder.listFiles((directory, name) -> name.endsWith(YAML_SUFFIX));
        if (files == null) {
            return Map.of();
        }
        Arrays.sort(files);

        Map<String, YamlConfiguration> dungeons = new LinkedHashMap<>();
        for (File file : files) {
            YamlConfiguration dungeon = load(file);
            if (dungeon == null) {
                continue;
            }
            String name = file.getName();
            dungeons.put(name.substring(0, name.length() - YAML_SUFFIX.length()), dungeon);
        }
        return dungeons;
    }

    private @Nullable YamlConfiguration load(@NotNull File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            Logger.error("Ошибка в " + file.getName() + ", файл пропущен до исправления", exception);
            return null;
        }
    }

    public void apply(@NotNull Bundle bundle) {
        Map<String, String> messages = new HashMap<>();
        for (String key : bundle.messages().getKeys(true)) {
            if (bundle.messages().isString(key)) {
                messages.put(key, bundle.messages().getString(key, ""));
            } else if (bundle.messages().isList(key)) {
                messages.put(key, String.join("\n", bundle.messages().getStringList(key)));
            }
        }
        Settings settings = Settings.from(bundle.config(), bundle.items(), bundle.region(), bundle.world(),
                bundle.dungeons());
        Logger.setDebugEnabled(settings.debug());
        snapshot = new Snapshot(settings, Map.copyOf(messages));
        warnedKeys.clear();
    }

    public @NotNull Settings settings() {
        return snapshot().settings();
    }

    public boolean hasMessage(@NotNull String key) {
        return snapshot().messages().containsKey(key);
    }

    public @NotNull String raw(@NotNull String key) {
        String value = snapshot().messages().get(key);
        if (value == null) {
            if (warnedKeys.add(key)) {
                Logger.warn("В messages.yml нет ключа '" + key + "'");
            }
            return "";
        }
        return value;
    }

    public @NotNull Component message(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        return parse(raw(key), Placeholder.parsed(PREFIX_KEY, snapshot().messages().getOrDefault(PREFIX_KEY, "")),
                placeholders);
    }

    public void send(@NotNull Audience audience, @NotNull String key) {
        send(audience, key, Map.of());
    }

    public void send(@NotNull Audience audience, @NotNull String key, @NotNull Map<String, ?> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        if (!(audience instanceof ConsoleCommandSender)) {
            audience.sendMessage(message(key, placeholders));
            return;
        }
        audience.sendMessage(parse(consoleSafe(raw), Placeholder.component(PREFIX_KEY,
                Component.text("[" + plugin.getName() + "]")), placeholders));
    }

    private static @NotNull String consoleSafe(@NotNull String text) {
        String result = HEX_TAG.matcher(text).replaceAll("");
        for (Map.Entry<String, String> symbol : CONSOLE_SYMBOLS.entrySet()) {
            result = result.replace(symbol.getKey(), symbol.getValue());
        }
        return result;
    }

    public @NotNull String formatTime(long seconds) {
        return TimeUtil.format(seconds, raw("time.days"), raw("time.hours"), raw("time.minutes"), raw("time.seconds"));
    }

    private @NotNull Component parse(
            @NotNull String raw,
            @NotNull TagResolver prefix,
            @NotNull Map<String, ?> placeholders
    ) {
        return MessageUtil.parseText(raw,
                prefix,
                Placeholder.unparsed("command", snapshot().settings().command().name()),
                MessageUtil.placeholders(placeholders));
    }

    private @NotNull Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("Настройки ещё не загружены");
        }
        return current;
    }

    public record Bundle(
            @NotNull YamlConfiguration config,
            @NotNull YamlConfiguration items,
            @NotNull YamlConfiguration region,
            @NotNull YamlConfiguration world,
            @NotNull YamlConfiguration messages,
            @NotNull Map<String, YamlConfiguration> dungeons
    ) {
    }

    private record Snapshot(@NotNull Settings settings, @NotNull Map<String, String> messages) {
    }
}
