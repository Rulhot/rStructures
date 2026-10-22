package ru.rulhot.rStructures.hook;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Bounds;

public interface ProtectionHook {

    @NotNull ProtectionHook NONE = (world, area) -> false;

    boolean overlaps(@NotNull World world, @NotNull Bounds area);
}
