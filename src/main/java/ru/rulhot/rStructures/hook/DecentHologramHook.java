package ru.rulhot.rStructures.hook;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.MessageUtil;

import java.util.List;

public final class DecentHologramHook implements HologramHook {

    private static final @NotNull LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    @Override
    public void show(
            @NotNull String name,
            @NotNull Location location,
            @NotNull List<String> lines,
            @NotNull Settings.Holograms style
    ) {
        List<String> legacy = lines.stream().map(line -> LEGACY.serialize(MessageUtil.parseText(line))).toList();
        Hologram existing = DHAPI.getHologram(name);
        if (existing != null) {
            DHAPI.setHologramLines(existing, legacy);
            return;
        }
        Hologram hologram = DHAPI.createHologram(name, location, false, legacy);
        hologram.setDisplayRange(style.viewDistance());
    }

    @Override
    public void remove(@NotNull String name) {
        if (DHAPI.getHologram(name) != null) {
            DHAPI.removeHologram(name);
        }
    }
}
