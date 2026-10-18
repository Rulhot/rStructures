package ru.rulhot.rStructures.model;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

public record DungeonTemplate(
        @NotNull String id,
        @NotNull String name,
        @NotNull String file,
        @NotNull String world,
        @NotNull Set<Biome> biomes,
        @NotNull Placement placement,
        int weight,
        int limit,
        int flatness,
        int height,
        int airMin,
        int airMax,
        @NotNull Settings.Schematic schematic,
        @NotNull OpenType openType,
        long openSeconds,
        int pickaxeHits,
        double keySaveChance,
        @NotNull Set<Material> containers,
        @NotNull Set<Material> lootContainers,
        @NotNull Settings.Auto auto
) {

    private static final double MAX_PERCENT = 100D;

    public DungeonTemplate {
        biomes = Set.copyOf(biomes);
        containers = Set.copyOf(containers);
        lootContainers = Set.copyOf(lootContainers);
        weight = Math.max(1, weight);
        flatness = Math.max(0, flatness);
        airMin = Math.max(1, airMin);
        airMax = Math.max(airMin, airMax);
        pickaxeHits = Math.max(1, pickaxeHits);
        keySaveChance = Math.clamp(keySaveChance, 0D, MAX_PERCENT);
    }

    public boolean matches(@NotNull String worldName, @NotNull Biome biome) {
        return world.equalsIgnoreCase(worldName) && biomes.contains(biome);
    }

    public enum OpenType {
        KEY,
        PICKAXE,
        TIMER;

        public @NotNull String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable OpenType from(@Nullable String raw) {
            if (raw == null) {
                return KEY;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "key" -> KEY;
                case "pickaxe" -> PICKAXE;
                case "timer" -> TIMER;
                default -> null;
            };
        }
    }

    public enum Placement {
        GROUND,
        WATER,
        AIR;

        public @NotNull String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean accepts(boolean water) {
            return switch (this) {
                case GROUND -> !water;
                case WATER -> water;
                case AIR -> true;
            };
        }

        public static @Nullable Placement from(@Nullable String raw) {
            if (raw == null) {
                return GROUND;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "ground" -> GROUND;
                case "water" -> WATER;
                case "air" -> AIR;
                default -> null;
            };
        }
    }

}
