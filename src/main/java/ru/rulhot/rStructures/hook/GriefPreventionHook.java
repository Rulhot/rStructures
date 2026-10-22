package ru.rulhot.rStructures.hook;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Bounds;

public final class GriefPreventionHook implements ProtectionHook {

    private final @NotNull DataStore dataStore = GriefPrevention.instance.dataStore;

    @Override
    public boolean overlaps(@NotNull World world, @NotNull Bounds area) {
        for (int chunkX = area.minX() >> 4; chunkX <= area.maxX() >> 4; chunkX++) {
            for (int chunkZ = area.minZ() >> 4; chunkZ <= area.maxZ() >> 4; chunkZ++) {
                for (Claim claim : dataStore.getClaims(chunkX, chunkZ)) {
                    if (overlaps(claim, world, area)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean overlaps(@NotNull Claim claim, @NotNull World world, @NotNull Bounds area) {
        Location lesser = claim.getLesserBoundaryCorner();
        Location greater = claim.getGreaterBoundaryCorner();
        return world.equals(lesser.getWorld())
                && lesser.getBlockX() <= area.maxX() && greater.getBlockX() >= area.minX()
                && lesser.getBlockZ() <= area.maxZ() && greater.getBlockZ() >= area.minZ();
    }
}
