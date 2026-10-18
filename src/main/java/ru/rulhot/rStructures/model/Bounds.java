package ru.rulhot.rStructures.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public @NotNull Location anchor(@NotNull World world) {
        return new Location(world, minX, minY, minZ);
    }

    public @NotNull Location center(@NotNull World world) {
        return new Location(world, (minX + maxX) / 2D + 0.5D, maxY + 1D, (minZ + maxZ) / 2D + 0.5D);
    }

    public record BlockPos(@NotNull UUID world, int x, int y, int z) {

        public static @NotNull BlockPos of(@NotNull World world, int x, int y, int z) {
            return new BlockPos(world.getUID(), x, y, z);
        }
    }
}
