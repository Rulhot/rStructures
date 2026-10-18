package ru.rulhot.rStructures.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CustomItem {
    KEY,
    COMPASS,
    PICKAXE;

    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable CustomItem from(@NotNull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "key" -> KEY;
            case "compass" -> COMPASS;
            case "pickaxe" -> PICKAXE;
            default -> null;
        };
    }
}
