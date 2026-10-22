package ru.rulhot.rStructures.hook;

import dev.cakestudio.cakeregionsapi.CakeRegionsAPI;
import dev.cakestudio.cakeregionsapi.api.IRegionsAPI;
import dev.cakestudio.cakeregionsapi.api.data.ICuboid;
import dev.cakestudio.cakeregionsapi.api.data.IRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Bounds;

public final class CakeRegionsHook implements ProtectionHook {

    private final @NotNull IRegionsAPI api = CakeRegionsAPI.getApi();

    @Override
    public boolean overlaps(@NotNull World world, @NotNull Bounds area) {
        for (IRegion region : api.getAllRegions()) {
            ICuboid cuboid = region.getCuboid();
            if (region.isActive() && world.equals(cuboid.getWorld())
                    && cuboid.getMinX() <= area.maxX() && cuboid.getMaxX() >= area.minX()
                    && cuboid.getMinY() <= area.maxY() && cuboid.getMaxY() >= area.minY()
                    && cuboid.getMinZ() <= area.maxZ() && cuboid.getMaxZ() >= area.minZ()) {
                return true;
            }
        }
        return false;
    }
}
