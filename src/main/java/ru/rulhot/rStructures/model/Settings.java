package ru.rulhot.rStructures.model;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.Biome;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.model.DungeonTemplate.OpenType;
import ru.rulhot.rStructures.model.DungeonTemplate.Placement;
import ru.rulhot.rStructures.util.TimeUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record Settings(
        boolean debug,
        boolean metrics,
        boolean updateCheck,
        @NotNull Command command,
        @NotNull Items items,
        @NotNull Sounds sounds,
        @NotNull Particles particles,
        @NotNull Holograms holograms,
        @NotNull Set<Material> containers,
        @NotNull Region region,
        @NotNull Schematic schematic,
        @NotNull List<DungeonTemplate> templates,
        @NotNull Spawn spawn
) {

    private static final @NotNull String DEFAULT_COMMAND = "rstructures";
    private static final @NotNull String DISABLED = "none";
    private static final @NotNull Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);
    private static final @NotNull String WILDCARD = "*";
    private static final @NotNull String RANDOM_TARGET = "random";
    private static final @NotNull String NEAREST_TARGET = "nearest";
    private static final double MAX_PERCENT = 100D;
    private static final double MIN_PITCH = 0.5D;
    private static final double MAX_PITCH = 2D;
    private static final long TICKS_PER_SECOND = 20L;

    public static @NotNull Settings from(
            @NotNull ConfigurationSection config,
            @NotNull ConfigurationSection items,
            @NotNull ConfigurationSection region,
            @NotNull ConfigurationSection world,
            @NotNull Map<String, ? extends ConfigurationSection> dungeons
    ) {
        List<String> folders = List.copyOf(config.getStringList("schematic-folders"));
        YamlConfiguration defaults = dungeonDefaults(config, world, items);
        List<DungeonTemplate> templates = templates(dungeons, defaults, folders);
        Set<Material> containers = new HashSet<>(containers(config, "containers", "config.yml", true));
        for (DungeonTemplate template : templates) {
            containers.addAll(template.containers());
        }
        String name = config.getString("command.main", DEFAULT_COMMAND).trim().toLowerCase(Locale.ROOT);
        return new Settings(
                config.getBoolean("settings.debug"),
                config.getBoolean("settings.metrics", true),
                config.getBoolean("settings.update-check", true),
                new Command(name.isEmpty() ? DEFAULT_COMMAND : name, List.copyOf(config.getStringList("command.aliases"))),
                items(items),
                new Sounds(
                        sound(config, "sounds.shulker-open"),
                        sound(config, "sounds.shulker-closed"),
                        sound(config, "sounds.key-saved"),
                        sound(config, "sounds.compass"),
                        sound(config, "sounds.menu-click")),
                new Particles(
                        effect(config, "particles.shulker-open"),
                        effect(config, "particles.shulker-closed")),
                holograms(config),
                Set.copyOf(containers),
                region(region),
                schematic(defaults, "schematic.", folders),
                templates,
                new Spawn(
                        world.getInt("spawn.min-x"),
                        world.getInt("spawn.max-x"),
                        world.getInt("spawn.min-z"),
                        world.getInt("spawn.max-z"),
                        world.getInt("spawn.step"),
                        world.getInt("spawn.radius"),
                        world.getInt("spawn.attempts"),
                        world.getInt("spawn.chunks-per-tick"),
                        world.getBoolean("spawn.generate-chunks"),
                        world.getBoolean("spawn.urgent-chunks"),
                        world.getInt("build.layers-per-tick")));
    }

    private static @NotNull YamlConfiguration dungeonDefaults(
            @NotNull ConfigurationSection config,
            @NotNull ConfigurationSection world,
            @NotNull ConfigurationSection items
    ) {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("schematic.ignore-air", true);
        defaults.set("schematic.offset.x", 0);
        defaults.set("schematic.offset.y", 1);
        defaults.set("schematic.offset.z", 0);
        defaults.set("schematic.clear.items", true);
        defaults.set("schematic.clear.monsters", true);
        defaults.set("schematic.clear.animals", true);
        defaults.set("schematic.clear.vehicles", false);
        defaults.set("schematic.clear.vegetation", true);
        defaults.set("schematic.clear.liquids", false);
        defaults.set("schematic.clear.snow", false);
        defaults.set("spawn.weight", 1);
        defaults.set("spawn.limit", -1);
        defaults.set("spawn.flatness", world.get("spawn.flatness"));
        defaults.set("spawn.height", 0);
        defaults.set("spawn.air.min", 30);
        defaults.set("spawn.air.max", 60);
        defaults.set("functions.open-type", OpenType.KEY.id());
        defaults.set("functions.open-time", "60s");
        defaults.set("functions.pickaxe-hits", items.get("pickaxe.functions.hits"));
        defaults.set("functions.key-save-chance", items.get("key.functions.save-chance"));
        defaults.set("functions.containers", config.getStringList("containers"));
        defaults.set("functions.loot-containers", List.of());
        defaults.set("auto.enabled", true);
        defaults.set("auto.time", "10s");
        defaults.set("auto.lifetime", "0");
        defaults.set("auto.require.opened", true);
        defaults.set("auto.require.looted", true);
        defaults.set("auto.actionbar", true);
        defaults.set("auto.respawn.enabled", true);
        defaults.set("auto.respawn.time", "1s");
        defaults.set("auto.respawn.need-players", false);
        return defaults;
    }

    private static @NotNull YamlConfiguration withDefaults(
            @NotNull ConfigurationSection own,
            @NotNull ConfigurationSection defaults
    ) {
        YamlConfiguration merged = new YamlConfiguration();
        for (String key : own.getKeys(true)) {
            if (!own.isConfigurationSection(key)) {
                merged.set(key, own.get(key));
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !merged.contains(key)) {
                merged.set(key, defaults.get(key));
            }
        }
        return merged;
    }

    public @Nullable DungeonTemplate templateOf(@NotNull String templateId, @NotNull String schematicFile) {
        DungeonTemplate sameFile = null;
        for (DungeonTemplate template : templates) {
            if (template.id().equals(templateId)) {
                return template;
            }
            if (sameFile == null && template.file().equals(schematicFile)) {
                sameFile = template;
            }
        }
        return sameFile;
    }

    private static @NotNull Schematic schematic(
            @NotNull ConfigurationSection root,
            @NotNull String prefix,
            @NotNull List<String> folders
    ) {
        return new Schematic(
                root.getBoolean(prefix + "ignore-air"),
                root.getInt(prefix + "offset.x"),
                root.getInt(prefix + "offset.y"),
                root.getInt(prefix + "offset.z"),
                new Clear(
                        root.getBoolean(prefix + "clear.items"),
                        root.getBoolean(prefix + "clear.monsters"),
                        root.getBoolean(prefix + "clear.animals"),
                        root.getBoolean(prefix + "clear.vehicles"),
                        root.getBoolean(prefix + "clear.vegetation"),
                        root.getBoolean(prefix + "clear.liquids"),
                        root.getBoolean(prefix + "clear.snow")),
                folders);
    }

    private static @NotNull Auto auto(
            @NotNull ConfigurationSection root,
            @NotNull String prefix,
            @NotNull String file
    ) {
        return new Auto(
                root.getBoolean(prefix + "enabled"),
                (int) Math.max(1L, seconds(root, prefix + "time", file)),
                seconds(root, prefix + "lifetime", file),
                root.getBoolean(prefix + "require.opened"),
                root.getBoolean(prefix + "require.looted"),
                root.getBoolean(prefix + "actionbar"),
                root.getBoolean(prefix + "respawn.enabled"),
                Math.max(1L, seconds(root, prefix + "respawn.time", file) * TICKS_PER_SECOND),
                root.getBoolean(prefix + "respawn.need-players"));
    }

    public boolean isDungeonWorld(@NotNull String worldName) {
        return templates.stream().anyMatch(template -> template.world().equalsIgnoreCase(worldName));
    }

    public @NotNull Set<String> dungeonWorlds() {
        Set<String> worlds = new LinkedHashSet<>();
        for (DungeonTemplate template : templates) {
            worlds.add(template.world());
        }
        return worlds;
    }

    private static @NotNull Items items(@NotNull ConfigurationSection root) {
        String target = root.getString("compass.functions.target", RANDOM_TARGET).trim().toLowerCase(Locale.ROOT);
        if (!target.equals(RANDOM_TARGET) && !target.equals(NEAREST_TARGET)) {
            Logger.warn("items.yml compass.functions.target: нужно random или nearest, используется random");
        }
        return new Items(
                item(root, "key", Material.TRIPWIRE_HOOK),
                item(root, "compass", Material.COMPASS),
                item(root, "pickaxe", Material.NETHERITE_PICKAXE),
                Math.max(1, root.getInt("pickaxe.functions.hits")),
                Math.max(0, root.getInt("pickaxe.functions.durability")),
                Math.clamp(number(root, "key.functions.save-chance", "items.yml"), 0D, MAX_PERCENT),
                new Compass(
                        root.getBoolean("compass.functions.encrypt"),
                        Math.max(1, root.getInt("compass.functions.encrypt-digits")),
                        root.getBoolean("compass.functions.pointer"),
                        seconds(root, "compass.functions.cooldown", "items.yml"),
                        root.getBoolean("compass.functions.consume"),
                        target.equals(NEAREST_TARGET)));
    }

    private static @NotNull Item item(@NotNull ConfigurationSection root, @NotNull String id, @NotNull Material fallback) {
        String raw = root.getString(id + ".material", "").trim();
        Material material = Material.matchMaterial(raw);
        if (material == null || material.isAir() || !material.isItem()) {
            Logger.warn("items.yml " + id + ".material: '" + raw + "' не предмет, используется " + fallback);
            material = fallback;
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String flag : root.getStringList(id + ".flags")) {
            try {
                flags.add(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                Logger.warn("items.yml " + id + ".flags: неизвестный флаг '" + flag + "', пропущен");
            }
        }
        return new Item(
                material,
                root.getString(id + ".texture", "").trim(),
                root.getString(id + ".name", ""),
                List.copyOf(root.getStringList(id + ".lore")),
                Math.max(0, root.getInt(id + ".model-data")),
                root.getBoolean(id + ".glint"),
                List.copyOf(flags),
                root.getBoolean(id + ".functions.glow"),
                root.getBoolean(id + ".functions.drop-on-quit"),
                root.getBoolean(id + ".functions.deny-craft"));
    }

    private static long seconds(@NotNull ConfigurationSection root, @NotNull String path, @NotNull String file) {
        String raw = String.valueOf(root.get(path));
        long value = TimeUtil.parseSeconds(raw);
        if (value < 0L) {
            Logger.warn(file + " " + path + ": '" + raw + "' не время, нужно например 10s, 5m или 1h, используется 0");
            return 0L;
        }
        return value;
    }

    private static @Nullable Sound sound(@NotNull ConfigurationSection root, @NotNull String path) {
        String raw = root.getString(path + ".key", DISABLED).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || raw.equals(DISABLED)) {
            return null;
        }
        if (!Key.parseable(raw)) {
            Logger.warn("config.yml " + path + ".key: некорректный звук '" + raw + "', звук отключён");
            return null;
        }
        return Sound.sound(
                Key.key(raw),
                Sound.Source.MASTER,
                (float) Math.clamp(number(root, path + ".volume", "config.yml"), 0D, 1D),
                (float) Math.clamp(number(root, path + ".pitch", "config.yml"), MIN_PITCH, MAX_PITCH));
    }

    private static @Nullable Effect effect(@NotNull ConfigurationSection root, @NotNull String path) {
        String raw = root.getString(path + ".type", DISABLED).trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase(DISABLED)) {
            return null;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn("config.yml " + path + ".type: неизвестная частица '" + raw + "', частицы отключены");
            return null;
        }
        if (particle.getDataType() != Void.class) {
            Logger.warn("config.yml " + path + ".type: частице '" + raw + "' нужны данные, частицы отключены");
            return null;
        }
        return new Effect(
                particle,
                Math.max(1, root.getInt(path + ".count")),
                number(root, path + ".spread", "config.yml"),
                number(root, path + ".speed", "config.yml"));
    }

    private static double number(@NotNull ConfigurationSection root, @NotNull String path, @NotNull String file) {
        Object raw = root.get(path);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            Logger.warn(file + " " + path + ": '" + raw + "' не число, используется 0");
            return 0D;
        }
    }

    private static @NotNull Set<Material> containers(
            @NotNull ConfigurationSection root,
            @NotNull String path,
            @NotNull String file,
            boolean required
    ) {
        Set<Material> containers = new HashSet<>();
        for (String raw : root.getStringList(path)) {
            Material material = Material.matchMaterial(raw.trim());
            if (material == Material.SHULKER_BOX) {
                containers.addAll(Tag.SHULKER_BOXES.getValues());
                continue;
            }
            if (material == null || !material.isBlock()
                    || !(material.createBlockData().createBlockState() instanceof Container)) {
                Logger.warn(file + " " + path + ": '" + raw + "' не блок с инвентарём, пропущен");
                continue;
            }
            containers.add(material);
        }
        if (required && containers.isEmpty()) {
            Logger.warn(file + " " + path + ": список пуст, используются шалкеры");
            containers.addAll(Tag.SHULKER_BOXES.getValues());
        }
        return Set.copyOf(containers);
    }

    private static @NotNull Holograms holograms(@NotNull ConfigurationSection config) {
        String rawBillboard = config.getString("holograms.billboard", "center").trim().toUpperCase(Locale.ROOT);
        Display.Billboard billboard = Display.Billboard.CENTER;
        try {
            billboard = Display.Billboard.valueOf(rawBillboard);
        } catch (IllegalArgumentException exception) {
            Logger.warn("config.yml holograms.billboard: нужно center, vertical, horizontal или fixed, используется center");
        }
        String rawBackground = config.getString("holograms.background", DISABLED).trim().toLowerCase(Locale.ROOT);
        Color background = TRANSPARENT;
        if (rawBackground.equals("default")) {
            background = null;
        } else if (!rawBackground.equals(DISABLED)) {
            try {
                background = Color.fromRGB(Integer.parseInt(rawBackground.replace("#", ""), 16));
            } catch (IllegalArgumentException exception) {
                Logger.warn("config.yml holograms.background: нужно none, default или цвет #RRGGBB, фон отключён");
            }
        }
        return new Holograms(
                config.getBoolean("holograms.enabled"),
                number(config, "holograms.height", "config.yml"),
                Math.max(1, config.getInt("holograms.view-distance", 32)),
                lines(config, "holograms.closed"),
                lines(config, "holograms.opened"),
                lines(config, "holograms.timer"),
                (float) Math.max(0.1D, number(config, "holograms.scale", "config.yml")),
                billboard,
                background,
                config.getBoolean("holograms.text-shadow"),
                config.getBoolean("holograms.see-through"));
    }

    private static @NotNull List<String> lines(@NotNull ConfigurationSection config, @NotNull String path) {
        String single = config.isString(path) ? config.getString(path, "") : "";
        return single.isEmpty() ? List.copyOf(config.getStringList(path)) : List.of(single);
    }

    private static @NotNull Region region(@NotNull ConfigurationSection root) {
        Map<StateFlag, StateFlag.State> flags = new HashMap<>();
        ConfigurationSection section = root.getConfigurationSection("region.flags");
        Set<String> names = section == null ? Set.of() : section.getKeys(false);
        for (String name : names) {
            Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name);
            String raw = String.valueOf(section.get(name)).trim().toUpperCase(Locale.ROOT);
            if (!(flag instanceof StateFlag stateFlag)) {
                Logger.warn("region.yml region.flags: неизвестный флаг '" + name + "', пропущен");
                continue;
            }
            try {
                flags.put(stateFlag, StateFlag.State.valueOf(raw));
            } catch (IllegalArgumentException exception) {
                Logger.warn("region.yml region.flags." + name + ": нужно allow или deny, флаг пропущен");
            }
        }

        String format = root.getString("region.name", "struct_{id}").trim().toLowerCase(Locale.ROOT);
        if (!format.contains(Region.ID_PLACEHOLDER)) {
            Logger.warn("region.yml region.name: нет {id}, он добавлен в конец имени");
            format = format + Region.ID_PLACEHOLDER;
        }
        return new Region(
                format,
                Math.max(0, root.getInt("region.padding")),
                Map.copyOf(flags),
                Math.max(0, root.getInt("limits.distance")),
                root.getInt("limits.max"),
                root.getBoolean("limits.avoid-regions"));
    }

    private static @NotNull List<DungeonTemplate> templates(
            @NotNull Map<String, ? extends ConfigurationSection> dungeons,
            @NotNull ConfigurationSection defaults,
            @NotNull List<String> folders
    ) {
        List<DungeonTemplate> templates = new ArrayList<>();
        Registry<Biome> biomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (Map.Entry<String, ? extends ConfigurationSection> entry : dungeons.entrySet()) {
            String id = entry.getKey();
            String context = "structures/" + id + ".yml";
            ConfigurationSection section = withDefaults(entry.getValue(), defaults);
            String file = section.getString("schematic.file", "");
            String worldName = section.getString("spawn.world", "");
            if (file.isBlank() || worldName.isBlank()) {
                Logger.warn(context + ": не указан schematic.file или spawn.world, данж пропущен");
                continue;
            }

            Set<Biome> biomes = new HashSet<>();
            for (String name : section.getStringList("spawn.biomes")) {
                List<Biome> matched = biomes(biomeRegistry, name.trim().toLowerCase(Locale.ROOT));
                if (matched.isEmpty()) {
                    Logger.warn(context + " spawn.biomes: под '" + name + "' не подходит ни один биом, пропущено");
                    continue;
                }
                biomes.addAll(matched);
            }
            if (biomes.isEmpty()) {
                Logger.warn(context + ": нет ни одного известного биома, данж пропущен");
                continue;
            }

            Placement placement = Placement.from(section.getString("spawn.placement"));
            if (placement == null) {
                Logger.warn(context + " spawn.placement: нужно ground, water или air, используется ground");
                placement = Placement.GROUND;
            }
            OpenType openType = OpenType.from(section.getString("functions.open-type"));
            if (openType == null) {
                Logger.warn(context + " functions.open-type: нужно key, pickaxe или timer, используется key");
                openType = OpenType.KEY;
            }
            templates.add(new DungeonTemplate(
                    id,
                    section.getString("name", id),
                    file,
                    worldName,
                    biomes,
                    placement,
                    section.getInt("spawn.weight"),
                    section.getInt("spawn.limit"),
                    section.getInt("spawn.flatness"),
                    section.getInt("spawn.height"),
                    section.getInt("spawn.air.min"),
                    section.getInt("spawn.air.max"),
                    schematic(section, "schematic.", folders),
                    openType,
                    Math.max(1L, seconds(section, "functions.open-time", context)),
                    section.getInt("functions.pickaxe-hits"),
                    number(section, "functions.key-save-chance", context),
                    containers(section, "functions.containers", context, true),
                    containers(section, "functions.loot-containers", context, false),
                    auto(section, "auto.", context)));
        }
        return List.copyOf(templates);
    }

    private static @NotNull List<Biome> biomes(@NotNull Registry<Biome> registry, @NotNull String name) {
        if (!name.contains(WILDCARD)) {
            NamespacedKey key = NamespacedKey.fromString(name);
            Biome biome = key == null ? null : registry.get(key);
            return biome == null ? List.of() : List.of(biome);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : name.split(Pattern.quote(WILDCARD), -1)) {
            regex.append(regex.isEmpty() ? "" : ".*").append(Pattern.quote(part));
        }
        Pattern pattern = Pattern.compile(regex.toString());
        boolean namespaced = name.contains(":");
        List<Biome> matched = new ArrayList<>();
        for (Biome biome : registry) {
            NamespacedKey key = registry.getKey(biome);
            if (key != null && pattern.matcher(namespaced ? key.toString() : key.getKey()).matches()) {
                matched.add(biome);
            }
        }
        return matched;
    }

    public record Item(
            @NotNull Material material,
            @NotNull String texture,
            @NotNull String name,
            @NotNull List<String> lore,
            int model,
            boolean glint,
            @NotNull List<ItemFlag> flags,
            boolean glow,
            boolean dropOnQuit,
            boolean denyCraft
    ) {
    }

    public record Compass(
            boolean encrypt,
            int encryptDigits,
            boolean pointer,
            long cooldownSeconds,
            boolean consume,
            boolean nearest
    ) {
    }

    public record Items(
            @NotNull Item key,
            @NotNull Item compass,
            @NotNull Item pickaxe,
            int pickaxeHits,
            int pickaxeDurability,
            double keySaveChance,
            @NotNull Compass compassUse
    ) {

        public @NotNull Item of(@NotNull CustomItem type) {
            return switch (type) {
                case KEY -> key;
                case COMPASS -> compass;
                case PICKAXE -> pickaxe;
            };
        }
    }

    public record Sounds(
            @Nullable Sound shulkerOpen,
            @Nullable Sound shulkerClosed,
            @Nullable Sound keySaved,
            @Nullable Sound compass,
            @Nullable Sound menuClick
    ) {
    }

    public record Effect(@NotNull Particle particle, int count, double spread, double speed) {
    }

    public record Holograms(
            boolean enabled,
            double height,
            int viewDistance,
            @NotNull List<String> closed,
            @NotNull List<String> opened,
            @NotNull List<String> timer,
            float scale,
            @NotNull Display.Billboard billboard,
            @Nullable Color background,
            boolean textShadow,
            boolean seeThrough
    ) {
    }

    public record Particles(@Nullable Effect shulkerOpen, @Nullable Effect shulkerClosed) {
    }

    public record Command(@NotNull String name, @NotNull List<String> aliases) {
    }

    public record Region(
            @NotNull String nameFormat,
            int padding,
            @NotNull Map<StateFlag, StateFlag.State> flags,
            int minDistance,
            int maxDungeons,
            boolean avoidForeignRegions
    ) {

        public static final @NotNull String ID_PLACEHOLDER = "{id}";

        public @NotNull String prefix() {
            return nameFormat.substring(0, nameFormat.indexOf(ID_PLACEHOLDER));
        }

        public @NotNull String name(@NotNull String regionId) {
            return nameFormat.replace(ID_PLACEHOLDER, regionId);
        }
    }

    public record Schematic(
            boolean ignoreAirBlocks,
            int offsetX,
            int offsetY,
            int offsetZ,
            @NotNull Clear clear,
            @NotNull List<String> folders
    ) {
    }

    public record Clear(
            boolean items,
            boolean monsters,
            boolean animals,
            boolean vehicles,
            boolean vegetation,
            boolean liquids,
            boolean snow
    ) {

        public boolean anyEntities() {
            return items || monsters || animals || vehicles;
        }

        public boolean anyBlocks() {
            return vegetation || liquids || snow;
        }
    }

    public record Spawn(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int scanStep,
            int scanRadius,
            int attempts,
            int chunksPerTick,
            boolean generateChunks,
            boolean urgentChunks,
            int layersPerTick
    ) {

        private static final int CHUNK_SIZE = 16;
        private static final int MAX_ATTEMPTS = 500;
        private static final int MAX_CHUNKS_PER_TICK = 256;
        private static final int MAX_LAYERS_PER_TICK = 64;

        public Spawn {
            maxX = Math.max(minX, maxX);
            maxZ = Math.max(minZ, maxZ);
            scanStep = Math.clamp(scanStep, 1, CHUNK_SIZE);
            scanRadius = Math.max(0, scanRadius);
            attempts = Math.clamp(attempts, 1, MAX_ATTEMPTS);
            chunksPerTick = Math.clamp(chunksPerTick, 1, MAX_CHUNKS_PER_TICK);
            layersPerTick = Math.clamp(layersPerTick, 1, MAX_LAYERS_PER_TICK);
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    public record Auto(
            boolean enabled,
            int delaySeconds,
            long lifetimeSeconds,
            boolean requireOpened,
            boolean requireLooted,
            boolean actionbar,
            boolean respawn,
            long respawnDelayTicks,
            boolean respawnOnlyWithPlayers
    ) {

        public boolean active() {
            return enabled && (requireOpened || requireLooted);
        }
    }
}
