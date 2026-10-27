package ru.rulhot.rStructures.service;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BlockTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.Bounds;
import ru.rulhot.rStructures.model.DungeonTemplate;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class SchematicService {

    private static final @NotNull String FOLDER = "schem";
    private static final @NotNull Set<Material> LIQUIDS = Set.of(Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN);
    private static final @NotNull Set<Material> SNOW = Set.of(Material.SNOW, Material.POWDER_SNOW);
    private static final @NotNull SideEffectSet QUIET_EFFECTS = SideEffectSet.defaults()
            .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
            .with(SideEffect.UPDATE, SideEffect.State.OFF);

    private final @NotNull JavaPlugin plugin;
    private final @NotNull ConfigManager configManager;
    private final @NotNull SchedulerUtil scheduler;
    private volatile @NotNull Map<String, Clipboard> clipboards = Map.of();

    public void reload() {
        List<String> files = configManager.settings().templates().stream()
                .map(DungeonTemplate::file)
                .distinct()
                .toList();
        scheduler.runAsync(() -> {
            Map<String, Clipboard> loaded = new HashMap<>();
            List<String> missing = new ArrayList<>();
            for (String file : files) {
                Clipboard clipboard = read(file);
                if (clipboard != null) {
                    loaded.put(file, clipboard);
                } else {
                    missing.add(file);
                }
            }
            clipboards = Map.copyOf(loaded);
            Logger.info("Загружено схем: " + loaded.size() + " из " + files.size());
            if (!missing.isEmpty()) {
                Logger.warn("Данжи без схемы не спавнятся. Положите в plugins/rStructures/" + FOLDER + "/ или в папку из config.yml schematic-folders файлы: "
                        + String.join(", ", missing));
            }
        });
    }

    public @Nullable Clipboard clipboard(@NotNull String file) {
        return clipboards.get(file);
    }

    public @NotNull Bounds pasteBounds(
            @NotNull Location location,
            @NotNull Clipboard clipboard,
            @NotNull Settings.Schematic settings
    ) {
        BlockVector3 shift = BlockVector3.at(
                location.getBlockX() + settings.offsetX(),
                location.getBlockY() + settings.offsetY(),
                location.getBlockZ() + settings.offsetZ()
        ).subtract(clipboard.getOrigin());
        BlockVector3 min = clipboard.getMinimumPoint().add(shift);
        BlockVector3 max = clipboard.getMaximumPoint().add(shift);
        return new Bounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public void capture(
            @NotNull World world,
            @NotNull Bounds bounds,
            @NotNull Consumer<byte @Nullable []> callback
    ) {
        Location anchor = bounds.anchor(world);
        BlockVector3 min = BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockVector3 max = BlockVector3.at(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(BukkitAdapter.adapt(world), min, max));
        clipboard.setOrigin(min);

        Consumer<byte @Nullable []> done = data -> {
            close(clipboard);
            callback.accept(data);
        };
        loadChunks(world, bounds, anchor, loaded -> {
            if (!loaded) {
                done.accept(null);
                return;
            }
            transfer(world, clipboard, min, false, false, 0, anchor, captured -> {
                if (!captured) {
                    done.accept(null);
                    return;
                }
                scheduler.runAsync(() -> {
                    byte[] data = serialize(clipboard);
                    scheduler.runAt(anchor, () -> done.accept(data));
                });
            });
        });
    }

    public void paste(
            @NotNull World world,
            @NotNull Clipboard clipboard,
            @NotNull Bounds bounds,
            @NotNull Settings.Schematic settings,
            @NotNull Consumer<Boolean> callback
    ) {
        Location anchor = bounds.anchor(world);
        BlockVector3 min = BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ());

        loadChunks(world, bounds, anchor, loaded -> {
            if (!loaded) {
                callback.accept(false);
                return;
            }
            Settings.Clear clear = settings.clear();
            if (clear.anyEntities()) {
                clearEntities(world, bounds, clear);
            }
            Runnable paste = () -> transfer(
                    world, clipboard, min, true, settings.ignoreAirBlocks(), 0, anchor, callback);
            if (clear.anyBlocks() && settings.ignoreAirBlocks()) {
                clearBlocks(world, bounds, clear, bounds.minY(), anchor, paste);
            } else {
                paste.run();
            }
        });
    }

    public void restore(
            @NotNull World world,
            @NotNull Bounds bounds,
            byte @NotNull [] terrain,
            @NotNull Consumer<Boolean> callback
    ) {
        Location anchor = bounds.anchor(world);
        BlockVector3 min = BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ());

        scheduler.runAsync(() -> {
            Clipboard clipboard = deserialize(terrain);
            scheduler.runAt(anchor, () -> loadChunks(world, bounds, anchor, loaded -> {
                if (clipboard == null || !loaded) {
                    close(clipboard);
                    callback.accept(false);
                    return;
                }
                transfer(world, clipboard, min, true, false, 0, anchor, restored -> {
                    close(clipboard);
                    callback.accept(restored);
                });
            }));
        });
    }

    private void close(@Nullable Clipboard clipboard) {
        if (!(clipboard instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            Logger.error("Не удалось освободить буфер блоков", exception);
        }
    }

    public void loadChunks(
            @NotNull World world,
            @NotNull Bounds bounds,
            @NotNull Location anchor,
            @NotNull Consumer<Boolean> callback
    ) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                futures.add(world.getChunkAtAsync(chunkX, chunkZ));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (error != null) {
                Logger.error("Не удалось загрузить чанки области данжа", error);
            }
            scheduler.runAt(anchor, () -> {
                if (error == null) {
                    forEachChunk(bounds, (chunkX, chunkZ) -> world.addPluginChunkTicket(chunkX, chunkZ, plugin));
                }
                callback.accept(error == null);
            });
        });
    }

    public void release(@NotNull World world, @NotNull Bounds bounds) {
        forEachChunk(bounds, (chunkX, chunkZ) -> world.removePluginChunkTicket(chunkX, chunkZ, plugin));
    }

    private void forEachChunk(@NotNull Bounds bounds, @NotNull BiConsumer<Integer, Integer> action) {
        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                action.accept(chunkX, chunkZ);
            }
        }
    }

    private void transfer(
            @NotNull World world,
            @NotNull Clipboard clipboard,
            @NotNull BlockVector3 worldMin,
            boolean toWorld,
            boolean ignoreAirBlocks,
            int offsetY,
            @NotNull Location anchor,
            @NotNull Consumer<Boolean> callback
    ) {
        BlockVector3 size = clipboard.getDimensions().subtract(1, 1, 1);
        if (offsetY > size.y()) {
            callback.accept(true);
            return;
        }

        int topOffset = Math.min(size.y(), offsetY + configManager.settings().spawn().layersPerTick() - 1);
        BlockVector3 clipboardMin = clipboard.getMinimumPoint();
        BlockVector3 clipboardFrom = clipboardMin.add(0, offsetY, 0);
        BlockVector3 clipboardTo = clipboardMin.add(size.x(), topOffset, size.z());
        BlockVector3 worldFrom = worldMin.add(0, offsetY, 0);
        BlockVector3 worldTo = worldMin.add(size.x(), topOffset, size.z());

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ForwardExtentCopy copy = toWorld
                    ? new ForwardExtentCopy(clipboard, new CuboidRegion(clipboardFrom, clipboardTo), session, worldFrom)
                    : new ForwardExtentCopy(session, new CuboidRegion(worldFrom, worldTo), clipboard, clipboardFrom);
            session.setSideEffectApplier(QUIET_EFFECTS);
            copy.setCopyingEntities(false);
            if (ignoreAirBlocks) {
                copy.setSourceMask(Masks.negate(new BlockTypeMask(
                        clipboard, BlockTypes.AIR, BlockTypes.CAVE_AIR, BlockTypes.VOID_AIR)));
            }
            Operations.complete(copy);
        } catch (WorldEditException | RuntimeException exception) {
            Logger.error("Не удалось перенести слой блоков со смещением y=" + offsetY, exception);
            callback.accept(false);
            return;
        }

        scheduler.runAtLater(anchor, 1L, () -> transfer(
                world, clipboard, worldMin, toWorld, ignoreAirBlocks, topOffset + 1, anchor, callback));
    }

    private void clearEntities(@NotNull World world, @NotNull Bounds bounds, @NotNull Settings.Clear clear) {
        BoundingBox box = new BoundingBox(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1D, bounds.maxY() + 1D, bounds.maxZ() + 1D
        );
        for (Entity entity : world.getNearbyEntities(box, entity -> isRemovable(entity, clear))) {
            entity.remove();
        }
    }

    private void clearBlocks(
            @NotNull World world,
            @NotNull Bounds bounds,
            @NotNull Settings.Clear clear,
            int fromY,
            @NotNull Location anchor,
            @NotNull Runnable done
    ) {
        if (fromY > bounds.maxY()) {
            done.run();
            return;
        }
        int toY = Math.min(bounds.maxY(), fromY + configManager.settings().spawn().layersPerTick() - 1);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isCleared(block.getType(), clear)) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        scheduler.runAtLater(anchor, 1L, () -> clearBlocks(world, bounds, clear, toY + 1, anchor, done));
    }

    private static boolean isCleared(@NotNull Material type, @NotNull Settings.Clear clear) {
        return clear.vegetation() && ChunkService.isTreeOrFoliage(type)
                || clear.liquids() && LIQUIDS.contains(type)
                || clear.snow() && SNOW.contains(type);
    }

    private static boolean isRemovable(@NotNull Entity entity, @NotNull Settings.Clear clear) {
        if (entity instanceof Player || entity.customName() != null
                || entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) {
            return false;
        }
        if (entity instanceof Item item) {
            return clear.items() && item.getThrower() == null;
        }
        if (entity instanceof LivingEntity living && living.isLeashed()
                || entity instanceof Animals animal && animal.getBreedCause() != null) {
            return false;
        }
        if (entity instanceof Vehicle && !(entity instanceof LivingEntity)) {
            return clear.vehicles();
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (entity instanceof Enemy) {
            return clear.monsters();
        }
        return entity instanceof Animals && clear.animals();
    }

    private byte @Nullable [] serialize(@NotNull Clipboard clipboard) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output)) {
            writer.write(clipboard);
        } catch (IOException | RuntimeException exception) {
            Logger.error("Не удалось сериализовать бэкап территории", exception);
            return null;
        }
        return output.toByteArray();
    }

    private @Nullable Clipboard deserialize(byte @NotNull [] terrain) {
        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                .getReader(new ByteArrayInputStream(terrain))) {
            return reader.read();
        } catch (IOException | RuntimeException exception) {
            Logger.error("Не удалось прочитать бэкап территории", exception);
            return null;
        }
    }

    private @Nullable Clipboard read(@NotNull String fileName) {
        File file = resolve(fileName);
        if (file == null) {
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            Logger.warn("Формат схемы не поддерживается: " + fileName);
            return null;
        }
        try (FileInputStream input = new FileInputStream(file); ClipboardReader reader = format.getReader(input)) {
            return reader.read();
        } catch (IOException | RuntimeException exception) {
            Logger.error("Не удалось прочитать схему " + fileName, exception);
            return null;
        }
    }

    private @Nullable File resolve(@NotNull String fileName) {
        Path ownFolder = plugin.getDataFolder().toPath().resolve(FOLDER).toAbsolutePath().normalize();
        Path own = ownFolder.resolve(fileName).normalize();
        if (!own.startsWith(ownFolder)) {
            Logger.warn("schematic.file: '" + fileName + "' выходит за пределы папки " + FOLDER + ", пропущен");
            return null;
        }
        if (Files.isRegularFile(own)) {
            return own.toFile();
        }
        Path plugins = plugin.getDataFolder().getParentFile().toPath().toAbsolutePath().normalize();
        for (String folder : configManager.settings().schematic().folders()) {
            Path candidate = plugins.resolve(folder).resolve(fileName).normalize();
            if (!candidate.startsWith(plugins)) {
                Logger.warn("config.yml schematic-folders: папка '" + folder + "' выходит за пределы plugins, пропущена");
                continue;
            }
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }
        return null;
    }
}
