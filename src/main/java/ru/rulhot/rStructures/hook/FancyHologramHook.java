package ru.rulhot.rStructures.hook;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import ru.rulhot.rStructures.model.Settings;

import java.util.List;

public final class FancyHologramHook implements HologramHook {

    @Override
    public void show(
            @NotNull String name,
            @NotNull Location location,
            @NotNull List<String> lines,
            @NotNull Settings.Holograms style
    ) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram existing = manager.getHologram(name).orElse(null);
        if (existing != null && existing.getData() instanceof TextHologramData text) {
            text.setText(lines);
            existing.forceUpdate();
            existing.refreshForViewersInWorld();
            return;
        }
        TextHologramData data = new TextHologramData(name, location);
        data.setText(lines);
        data.setBackground(style.background());
        data.setTextShadow(style.textShadow());
        data.setSeeThrough(style.seeThrough());
        data.setBillboard(style.billboard());
        data.setScale(new Vector3f(style.scale()));
        data.setVisibilityDistance(style.viewDistance());
        data.setPersistent(false);
        manager.addHologram(manager.create(data));
    }

    @Override
    public void remove(@NotNull String name) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(name).ifPresent(manager::removeHologram);
    }
}
