package ru.rulhot.rStructures.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.model.Bounds;
import ru.rulhot.rStructures.model.DungeonTemplate.Placement;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class ChunkService {

    private static final int MAX_CHUNKS_PER_WORLD = 256;
    private static final int CHUNK_SIZE = 16;
    private static final int FOOTPRINT_SAMPLES = 4;
    private static final int WATER_DEPTH = 3;
    private static final int WATER_SCAN_DEPTH = 8;
    private static final int NETHER_START_Y = 120;
    private static final int NETHER_ROOF_Y = 126;
    private static final int END_MIN_HEIGHT = 16;
    private static final int END_SUPPORT = 3;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final @NotNull Set<Material> PLANTS = Set.of(
            Material.VINE, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN,
            Material.MOSS_CARPET, Material.MOSS_BLOCK, Material.AZALEA, Material.FLOWERING_AZALEA,
            Material.BAMBOO, Material.BAMBOO_SAPLING, Material.SUGAR_CANE, Material.CACTUS,
            Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
            Material.DEAD_BUSH, Material.SWEET_BERRY_BUSH, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
            Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS,
            Material.NETHER_SPROUTS, Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT,
            Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT);
    private static final @NotNull Set<Material> UNSTABLE = Set.of(
            Material.GRAVEL, Material.SUSPICIOUS_GRAVEL, Material.SOUL_SAND, Material.SOUL_SOIL, Material.MUD,
            Material.HAY_BLOCK, Material.FARMLAND, Material.DIRT_PATH, Material.LAVA, Material.MAGMA_BLOCK,
            Material.ICE, Material.FROSTED_ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW);
    private static final @NotNull Set<Material> WATER_PLANTS = Set.of(
            Material.BUBBLE_COLUMN, Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS);

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull Map<String, Map<Long, Index>> byWorld = new ConcurrentHashMap<>();

    public void findNearest(
            @NotNull World world,
            int centerX,
            int centerZ,
            @NotNull Settings.Spawn spawn,
            int maxBlockRadius,
            @NotNull ColumnPredicate predicate,
            @NotNull HitHandler onHit,
            @NotNull Runnable onExhausted
    ) {
        scanRing(new Search(world, centerX, centerZ, spawn, maxBlockRadius, predicate, onHit, onExhausted), 0);
    }

    public @Nullable Index index(@NotNull World world, int blockX, int blockZ) {
        return indexChunk(world, blockX >> 4, blockZ >> 4);
    }

    public boolean fits(
            @NotNull World world,
            @NotNull Bounds bounds,
            int surfaceY,
            @NotNull Placement placement,
            int flatness
    ) {
        for (int stepX = 0; stepX <= FOOTPRINT_SAMPLES; stepX++) {
            int blockX = bounds.minX() + (bounds.maxX() - bounds.minX()) * stepX / FOOTPRINT_SAMPLES;
            for (int stepZ = 0; stepZ <= FOOTPRINT_SAMPLES; stepZ++) {
                int blockZ = bounds.minZ() + (bounds.maxZ() - bounds.minZ()) * stepZ / FOOTPRINT_SAMPLES;
                Index index = index(world, blockX, blockZ);
                if (index != null && !fitsColumn(world, index, bounds, blockX, blockZ, surfaceY, placement, flatness)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void clear() {
        byWorld.clear();
    }

    public static boolean matches(@NotNull World world, @NotNull Index index, int blockX, int blockZ) {
        ColumnSample column = index.sample(blockX, blockZ);
        if (column.highestY <= world.getMinHeight()) {
            return false;
        }
        Material top = column.top;
        return switch (world.getEnvironment()) {
            case NORMAL -> isWaterSurface(top)
                    ? index.isDeepWater(blockX, column.highestY, blockZ)
                    : top.isSolid() && !isTreeOrFoliage(top) && !UNSTABLE.contains(top);
            case NETHER -> top.isSolid() && top != Material.BEDROCK && column.highestY < NETHER_ROOF_Y
                    && !isTreeOrFoliage(top);
            case THE_END -> top == Material.END_STONE && column.highestY >= world.getMinHeight() + END_MIN_HEIGHT
                    && index.hasEndSupport(blockX, blockZ, column.highestY);
            default -> false;
        };
    }

    public static int placementY(@NotNull ColumnSample column) {
        return isWaterSurface(column.top) ? column.highestY + 1 : column.highestY;
    }

    public static boolean isWaterSurface(@NotNull Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }

    public static boolean isTreeOrFoliage(@NotNull Material material) {
        return Tag.LEAVES.isTagged(material)
                || Tag.LOGS.isTagged(material)
                || Tag.SAPLINGS.isTagged(material)
                || Tag.FLOWERS.isTagged(material)
                || PLANTS.contains(material);
    }

    private static boolean isPassable(@NotNull Material material) {
        return !material.isSolid() || material == Material.SNOW || isTreeOrFoliage(material);
    }

    private boolean fitsColumn(
            @NotNull World world,
            @NotNull Index index,
            @NotNull Bounds bounds,
            int blockX,
            int blockZ,
            int surfaceY,
            @NotNull Placement placement,
            int flatness
    ) {
        if (placement == Placement.AIR) {
            return index.isClear(blockX, bounds.minY(), bounds.maxY(), blockZ);
        }
        ColumnSample column = index.sample(blockX, blockZ);
        String reason = rejectReason(world, index, column, bounds, blockX, blockZ, surfaceY, placement, flatness);
        if (reason != null && placement == Placement.GROUND) {
            Logger.debug("ground " + blockX + " " + column.highestY + " " + blockZ + ": " + reason);
        }
        return reason == null;
    }

    private static @Nullable String rejectReason(
            @NotNull World world,
            @NotNull Index index,
            @NotNull ColumnSample column,
            @NotNull Bounds bounds,
            int blockX,
            int blockZ,
            int surfaceY,
            @NotNull Placement placement,
            int flatness
    ) {
        if (!matches(world, index, blockX, blockZ)) {
            return "плохая поверхность " + column.top;
        }
        if (isWaterSurface(column.top) != (placement == Placement.WATER)) {
            return "вода под схемой";
        }
        if (Math.abs(column.highestY - surfaceY) > flatness) {
            return "перепад " + Math.abs(column.highestY - surfaceY) + " при flatness " + flatness;
        }
        if (!index.isClear(blockX, Math.max(column.highestY + 1, bounds.minY()), bounds.maxY(), blockZ)) {
            return "место над землёй занято";
        }
        return null;
    }

    private void scanRing(@NotNull Search search, int chunkRadius) {
        if (chunkRadius > (search.radius() >> 4) + 1) {
            search.onExhausted().run();
            return;
        }
        World world = search.world();
        int centerChunkX = search.centerX() >> 4;
        int centerChunkZ = search.centerZ() >> 4;
        List<int[]> chunks = new ArrayList<>();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int deltaX = -chunkRadius; deltaX <= chunkRadius; deltaX++) {
            for (int deltaZ = -chunkRadius; deltaZ <= chunkRadius; deltaZ++) {
                if (Math.abs(deltaX) == chunkRadius || Math.abs(deltaZ) == chunkRadius) {
                    chunks.add(new int[]{centerChunkX + deltaX, centerChunkZ + deltaZ});
                    futures.add(world.getChunkAtAsync(centerChunkX + deltaX, centerChunkZ + deltaZ,
                            search.spawn().generateChunks(), search.spawn().urgentChunks()));
                }
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((loaded, error) ->
                scheduler.runAt(search.anchor(), () -> scanChunks(search, chunkRadius, chunks, 0)));
    }

    private void scanChunks(@NotNull Search search, int chunkRadius, @NotNull List<int[]> chunks, int from) {
        int limit = Math.min(chunks.size(), from + search.spawn().chunksPerTick());
        for (int position = from; position < limit; position++) {
            int[] chunk = chunks.get(position);
            int[] hit = scanChunk(search, chunk[0], chunk[1], chunkRadius == 0);
            if (hit != null) {
                int next = position + 1;
                search.onHit().handle(hit, () -> scheduler.runAt(search.anchor(),
                        () -> scanChunks(search, chunkRadius, chunks, next)));
                return;
            }
        }
        if (limit >= chunks.size()) {
            scanRing(search, chunkRadius + 1);
            return;
        }
        scheduler.runAtLater(search.anchor(), 1L, () -> scanChunks(search, chunkRadius, chunks, limit));
    }

    private int @Nullable [] scanChunk(@NotNull Search search, int chunkX, int chunkZ, boolean center) {
        Index index = indexChunk(search.world(), chunkX, chunkZ);
        if (index == null) {
            return null;
        }
        if (center && search.predicate().test(index, search.centerX(), search.centerZ())) {
            return new int[]{search.centerX(), search.centerZ()};
        }
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int blockX = baseX; blockX < baseX + CHUNK_SIZE; blockX += search.spawn().scanStep()) {
            for (int blockZ = baseZ; blockZ < baseZ + CHUNK_SIZE; blockZ += search.spawn().scanStep()) {
                if (Math.abs(blockX - search.centerX()) <= search.radius()
                        && Math.abs(blockZ - search.centerZ()) <= search.radius()
                        && search.predicate().test(index, blockX, blockZ)) {
                    return new int[]{blockX, blockZ};
                }
            }
        }
        return null;
    }

    private @Nullable Index indexChunk(@NotNull World world, int chunkX, int chunkZ) {
        Map<Long, Index> chunks = byWorld.computeIfAbsent(world.getName(), ignored -> new ConcurrentHashMap<>());
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        Index cached = chunks.get(key);
        if (cached != null) {
            return cached;
        }
        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }
        if (chunks.size() >= MAX_CHUNKS_PER_WORLD) {
            chunks.clear();
        }
        Index fresh = new Index(world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(true, true, false),
                chunkX, chunkZ, world.getEnvironment(), world.getMinHeight(), world.getMaxHeight());
        chunks.put(key, fresh);
        return fresh;
    }

    @FunctionalInterface
    public interface ColumnPredicate {

        boolean test(@NotNull Index index, int blockX, int blockZ);
    }

    private record Search(
            @NotNull World world,
            int centerX,
            int centerZ,
            @NotNull Settings.Spawn spawn,
            int radius,
            @NotNull ColumnPredicate predicate,
            @NotNull HitHandler onHit,
            @NotNull Runnable onExhausted
    ) {

        private @NotNull Location anchor() {
            return new Location(world, centerX, world.getMinHeight(), centerZ);
        }
    }

    @FunctionalInterface
    public interface HitHandler {

        void handle(int @NotNull [] hit, @NotNull Runnable reject);
    }

    public static final class ColumnSample {

        public final int highestY;
        public final @NotNull Material top;
        public final @NotNull Biome biome;

        private ColumnSample(int highestY, @NotNull Material top, @NotNull Biome biome) {
            this.highestY = highestY;
            this.top = top;
            this.biome = biome;
        }
    }

    public static final class Index {

        private final @NotNull ChunkSnapshot snapshot;
        private final int chunkX;
        private final int chunkZ;
        private final @NotNull World.Environment environment;
        private final int minHeight;
        private final int maxHeight;
        private final @Nullable ColumnSample @NotNull [] samples = new ColumnSample[CHUNK_SIZE * CHUNK_SIZE];

        private Index(
                @NotNull ChunkSnapshot snapshot,
                int chunkX,
                int chunkZ,
                @NotNull World.Environment environment,
                int minHeight,
                int maxHeight
        ) {
            this.snapshot = snapshot;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.environment = environment;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        public @NotNull ColumnSample sample(int blockX, int blockZ) {
            int localX = blockX - (chunkX << 4);
            int localZ = blockZ - (chunkZ << 4);
            int cell = localX * CHUNK_SIZE + localZ;
            ColumnSample cached = samples[cell];
            if (cached != null) {
                return cached;
            }
            int y = switch (environment) {
                case NETHER -> netherFloor(localX, localZ);
                case THE_END -> endSurface(localX, localZ);
                default -> overworldSurface(localX, localZ);
            };
            y = Math.clamp(y, minHeight, maxHeight - 1);
            ColumnSample sample = new ColumnSample(y, snapshot.getBlockType(localX, y, localZ),
                    snapshot.getBiome(localX, y, localZ));
            samples[cell] = sample;
            return sample;
        }

        private boolean contains(int blockX, int blockZ) {
            return blockX >> 4 == chunkX && blockZ >> 4 == chunkZ;
        }

        private boolean isClear(int blockX, int fromY, int toY, int blockZ) {
            int localX = blockX - (chunkX << 4);
            int localZ = blockZ - (chunkZ << 4);
            for (int y = Math.max(minHeight, fromY); y <= Math.min(maxHeight - 1, toY); y++) {
                Material type = snapshot.getBlockType(localX, y, localZ);
                if (!isPassable(type) || type == Material.LAVA) {
                    return false;
                }
            }
            return true;
        }

        private boolean isDeepWater(int blockX, int surfaceY, int blockZ) {
            int localX = blockX - (chunkX << 4);
            int localZ = blockZ - (chunkZ << 4);
            if (surfaceY + 1 < maxHeight && snapshot.getBlockType(localX, surfaceY + 1, localZ).isSolid()) {
                return false;
            }
            int depth = 0;
            for (int y = surfaceY; y >= minHeight && depth < WATER_SCAN_DEPTH; y--) {
                Material type = snapshot.getBlockType(localX, y, localZ);
                if (type != Material.WATER && !WATER_PLANTS.contains(type)) {
                    break;
                }
                depth++;
            }
            return depth >= WATER_DEPTH;
        }

        private boolean hasEndSupport(int blockX, int blockZ, int surfaceY) {
            int support = 0;
            for (int[] offset : NEIGHBORS) {
                int neighborX = blockX + offset[0];
                int neighborZ = blockZ + offset[1];
                if (!contains(neighborX, neighborZ)) {
                    support++;
                    continue;
                }
                ColumnSample neighbor = sample(neighborX, neighborZ);
                if (Math.abs(neighbor.highestY - surfaceY) <= 1 && neighbor.top == Material.END_STONE) {
                    support++;
                }
            }
            return support >= END_SUPPORT;
        }

        private int overworldSurface(int localX, int localZ) {
            int y = Math.clamp(snapshot.getHighestBlockYAt(localX, localZ), minHeight, maxHeight - 1);
            Material top = snapshot.getBlockType(localX, y, localZ);
            if (isWaterSurface(top) || top == Material.LAVA) {
                return y;
            }
            for (; y > minHeight; y--) {
                Material type = snapshot.getBlockType(localX, y, localZ);
                if (isWaterSurface(type) || !isPassable(type) && hasHeadroom(localX, y, localZ, 3)) {
                    return y;
                }
            }
            return minHeight;
        }

        private int netherFloor(int localX, int localZ) {
            for (int y = Math.min(NETHER_START_Y, maxHeight - 2); y > minHeight + 8; y--) {
                Material floor = snapshot.getBlockType(localX, y, localZ);
                if (floor != Material.BEDROCK && !isPassable(floor) && hasHeadroom(localX, y, localZ, 4)) {
                    return y;
                }
            }
            return minHeight;
        }

        private int endSurface(int localX, int localZ) {
            for (int y = snapshot.getHighestBlockYAt(localX, localZ); y > minHeight + 5; y--) {
                if (snapshot.getBlockType(localX, y, localZ) == Material.END_STONE
                        && hasHeadroom(localX, y, localZ, 3)) {
                    return y;
                }
            }
            return minHeight;
        }

        private boolean hasHeadroom(int localX, int floorY, int localZ, int blocks) {
            for (int y = floorY + 1; y <= floorY + blocks; y++) {
                if (y >= maxHeight) {
                    return false;
                }
                Material type = snapshot.getBlockType(localX, y, localZ);
                if (!isPassable(type) || type == Material.LAVA || type == Material.WATER) {
                    return false;
                }
            }
            return true;
        }
    }
}
