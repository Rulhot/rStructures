package ru.rulhot.rStructures.hook;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Settings;

import java.util.List;

public interface HologramHook {

    @NotNull HologramHook NONE = new HologramHook() {

        @Override
        public void show(
                @NotNull String name,
                @NotNull Location location,
                @NotNull List<String> lines,
                @NotNull Settings.Holograms style
        ) {
        }

        @Override
        public void remove(@NotNull String name) {
        }
    };

    void show(
            @NotNull String name,
            @NotNull Location location,
            @NotNull List<String> lines,
            @NotNull Settings.Holograms style
    );

    void remove(@NotNull String name);
}
