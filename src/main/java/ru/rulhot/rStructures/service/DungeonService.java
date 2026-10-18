package ru.rulhot.rStructures.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.database.model.ShulkerRecord;
import ru.rulhot.rStructures.database.repository.DungeonRepository;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.Bounds;
import ru.rulhot.rStructures.model.DungeonTemplate;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.model.SpawnResult;
import ru.rulhot.rStructures.util.MessageUtil;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class DungeonService {

    private static final int REGION_ID_RADIX = 36;
    private static final long TICKS_PER_SECOND = 20L;
    private static final int RESPAWN_ATTEMPTS = 5;
    private static final long LIFETIME_CHECK_TICKS = 100L;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager configManager;
    private final @NotNull SchematicService schematicService;
    private final @NotNull ShulkerService shulkerService;
    private final @NotNull LootService lootService;
    private final @NotNull DungeonRepository dungeonRepository;
    private final @NotNull ChunkService chunkService;
    private final @NotNull Map<String, SchedulerUtil.Task> autoUndoTasks = new ConcurrentHashMap<>();
    private final @NotNull Set<String> undoing = ConcurrentHashMap.newKeySet();
    private final @NotNull Map<String, AtomicInteger> autoUndoSeconds = new ConcurrentHashMap<>();
    private final @NotNull AtomicBoolean manualSpawn = new AtomicBoolean();
    private volatile @Nullable SchedulerUtil.Task lifetimeTask;
    private final @NotNull Object slotLock = new Object();
    private int pendingSpawns;
    private volatile boolean cancelled;
    private final @NotNull AtomicInteger searchAttempts = new AtomicInteger();
    private final @NotNull AtomicInteger rejectedPlaces = new AtomicInteger();

    public void cancelSpawn() {
        cancelled = true;
    }

    public @Nullable String openType(@NotNull DungeonRecord record) {
        return shulkerService.openType(record.getRegionId());
    }

    public @NotNull List<DungeonRecord> dungeons() {
        return dungeonRepository.all();
    }

    public void startTimers() {
        lifetimeTask = scheduler.runGlobalTimer(LIFETIME_CHECK_TICKS, () -> {
            for (DungeonRecord record : dungeonRepository.all()) {
                if (lifetimeSecondsLeft(record) == 0L && !autoUndoTasks.containsKey(record.getRegionId())
                        && !undoing.contains(record.getRegionId())) {
                    startAutoUndo(record, lifetimeAuto(record));
                }
            }
        });
    }

    public long lifetimeSecondsLeft(@NotNull DungeonRecord record) {
        Settings.Auto auto = lifetimeAuto(record);
        if (auto == null || auto.lifetimeSeconds() <= 0L || record.getCreatedAt() <= 0L) {
            return -1L;
        }
        long passed = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - record.getCreatedAt());
        return Math.max(0L, auto.lifetimeSeconds() - passed);
    }

    private @Nullable Settings.Auto lifetimeAuto(@NotNull DungeonRecord record) {
        DungeonTemplate template = configManager.settings()
                .templateOf(record.getTemplateId(), record.getSchematic());
        return template == null ? null : template.auto();
    }

    public boolean beginSpawn() {
        return manualSpawn.compareAndSet(false, true);
    }

    public void endSpawn() {
        manualSpawn.set(false);
    }

    public boolean isSpawning() {
        return manualSpawn.get();
    }

    public int count(@NotNull DungeonTemplate template) {
        return dungeonRepository.countByTemplate(template.id());
    }

    public boolean isReady(@NotNull DungeonTemplate template) {
        return schematicService.clipboard(template.file()) != null;
    }

    public @NotNull ShulkerService.Progress shulkers(@NotNull DungeonRecord record) {
        return shulkerService.progress(record.getRegionId());
    }

    public boolean refill(@NotNull DungeonRecord record) {
        return shulkerService.refillAll(record.getRegionId());
    }

    public int autoUndoSecondsLeft(@NotNull DungeonRecord record) {
        AtomicInteger left = autoUndoSeconds.get(record.getRegionId());
        return left == null ? -1 : Math.max(0, left.get());
    }

    public boolean report(@NotNull Audience sender, @NotNull World world, @NotNull SpawnResult result) {
        return switch (result) {
            case SpawnResult.Success success -> {
                Location location = success.location();
                configManager.send(sender, "spawn.success", Map.of(
                        "x", location.getBlockX(),
                        "y", location.getBlockY(),
                        "z", location.getBlockZ(),
                        "world", world.getName(),
                        "id", success.regionId(),
                        "name", MessageUtil.parseText(success.name())
                ));
                yield true;
            }
            case SpawnResult.LimitReached limit -> {
                configManager.send(sender, "spawn.limit-reached", Map.of("count", limit.limit()));
                yield false;
            }
            case SpawnResult.Cancelled cancelled -> {
                configManager.send(sender, "spawn.cancelled");
                yield false;
            }
            case SpawnResult.Unsuitable unsuitable -> {
                configManager.send(sender, "spawn.no-location", Map.of("world", world.getName()));
                yield true;
            }
            case SpawnResult.Failed failed -> {
                String reason = failed.reason().name().toLowerCase(Locale.ROOT).replace('_', '-');
                configManager.send(sender, "spawn.failed", Map.of(
                        "world", world.getName(),
                        "reason", configManager.raw("spawn.reasons." + reason)
                ));
                yield false;
            }
        };
    }

    public void spawnRandom(@NotNull World world, @NotNull Consumer<SpawnResult> callback) {
        spawn(world, null, callback);
    }

    public void spawn(@NotNull World world, @Nullable String templateId, @NotNull Consumer<SpawnResult> callback) {
        cancelled = false;
        searchAttempts.set(0);
        rejectedPlaces.set(0);
        long startedAt = System.currentTimeMillis();
        Consumer<SpawnResult> finish = result -> {
            Logger.debug("Спавн данжа в мире " + world.getName() + ": " + result.getClass().getSimpleName()
                    + ", всего " + (System.currentTimeMillis() - startedAt) + " мс, попыток "
                    + searchAttempts.get() + ", отклонено мест " + rejectedPlaces.get());
            chunkService.clear();
            scheduler.runGlobal(() -> callback.accept(result));
        };
        if (configManager.settings().templates().stream().noneMatch(template ->
                (templateId == null || template.id().equals(templateId))
                        && template.world().equalsIgnoreCase(world.getName())
                        && isReady(template))) {
            finish.accept(new SpawnResult.Failed(SpawnResult.Reason.NO_TEMPLATES));
            return;
        }
        if (regionManager(world) == null) {
            Logger.warn("WorldGuard не управляет регионами мира " + world.getName());
            finish.accept(new SpawnResult.Failed(SpawnResult.Reason.REGIONS_UNAVAILABLE));
            return;
        }
        spawnRandom(world, templateId, configManager.settings().spawn().attempts(), finish);
    }

    public void undo(@NotNull String rawId, @NotNull Consumer<SpawnResult.Undo> callback) {
        String regionId = normalize(rawId);
        DungeonRecord record = dungeonRepository.find(regionId);
        if (record == null) {
            scheduler.runGlobal(() -> callback.accept(SpawnResult.Undo.NOT_FOUND));
            return;
        }
        World world = Bukkit.getWorld(record.getWorldUuid());
        if (world == null) {
            scheduler.runGlobal(() -> callback.accept(SpawnResult.Undo.WORLD_MISSING));
            return;
        }
        if (!undoing.add(regionId)) {
            scheduler.runGlobal(() -> callback.accept(SpawnResult.Undo.BUSY));
            return;
        }

        cancelAutoUndo(regionId);
        Bounds bounds = record.bounds();
        Location anchor = bounds.anchor(world);
        Consumer<SpawnResult.Undo> finish = result -> {
            undoing.remove(regionId);
            schematicService.release(world, bounds);
            scheduler.runGlobal(() -> callback.accept(result));
        };

        dungeonRepository.loadTerrain(regionId, terrain -> scheduler.runAt(anchor, () -> {
            if (terrain == null || terrain.length == 0) {
                finish.accept(SpawnResult.Undo.RESTORE_FAILED);
                return;
            }
            restore(record, world, terrain, finish);
        }));
    }

    public void checkAutoUndo(@NotNull Block block) {
        ShulkerRecord shulker = shulkerService.find(block);
        if (shulker == null || shulker.getRegionId() == null) {
            return;
        }

        String regionId = shulker.getRegionId();
        DungeonRecord record = dungeonRepository.find(regionId);
        if (record == null || autoUndoTasks.containsKey(regionId) || undoing.contains(regionId)) {
            return;
        }
        DungeonTemplate template = configManager.settings()
                .templateOf(record.getTemplateId(), record.getSchematic());
        if (template == null || !template.auto().active()) {
            return;
        }
        Settings.Auto auto = template.auto();
        if (!shulkerService.isComplete(block.getWorld(), regionId, auto.requireOpened(), auto.requireLooted())) {
            return;
        }
        startAutoUndo(record, auto);
    }

    public @Nullable DungeonRecord compassTarget(@NotNull Player player, boolean nearest, @Nullable String lastId) {
        World world = player.getWorld();
        List<DungeonRecord> candidates = new ArrayList<>(dungeonRepository.inWorld(world.getUID()));
        if (candidates.isEmpty()) {
            return null;
        }
        if (!nearest) {
            if (candidates.size() > 1) {
                candidates.removeIf(record -> record.getRegionId().equals(lastId));
            }
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        Location origin = player.getLocation();
        DungeonRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DungeonRecord record : candidates) {
            double distance = record.bounds().center(world).distanceSquared(origin);
            if (distance < bestDistance) {
                best = record;
                bestDistance = distance;
            }
        }
        return best;
    }

    public void shutdown() {
        for (SchedulerUtil.Task task : autoUndoTasks.values()) {
            task.cancel();
        }
        autoUndoTasks.clear();
        SchedulerUtil.Task task = lifetimeTask;
        lifetimeTask = null;
        if (task != null) {
            task.cancel();
        }
        autoUndoSeconds.clear();
        undoing.clear();
    }

    private void spawnRandom(
            @NotNull World world,
            @Nullable String templateId,
            int attemptsLeft,
            @NotNull Consumer<SpawnResult> finish
    ) {
        if (cancelled) {
            finish.accept(new SpawnResult.Cancelled());
            return;
        }
        if (attemptsLeft <= 0) {
            finish.accept(new SpawnResult.Unsuitable());
            return;
        }

        searchAttempts.incrementAndGet();
        Settings settings = configManager.settings();
        Settings.Spawn spawn = settings.spawn();
        int centerX = ThreadLocalRandom.current().nextInt(spawn.minX(), spawn.maxX() + 1);
        int centerZ = ThreadLocalRandom.current().nextInt(spawn.minZ(), spawn.maxZ() + 1);
        int areaRadius = Math.max(spawn.maxX() - spawn.minX(), spawn.maxZ() - spawn.minZ()) / 2;
        List<DungeonRecord> existing = dungeonRepository.inWorld(world.getUID());

        chunkService.findNearest(
                world,
                centerX,
                centerZ,
                spawn,
                Math.min(spawn.scanRadius(), areaRadius),
                (index, blockX, blockZ) -> !cancelled
                        && spawn.contains(blockX, blockZ)
                        && isFarEnough(existing, blockX, blockZ, settings.region())
                        && ChunkService.matches(world, index, blockX, blockZ)
                        && !candidates(world, index, blockX, blockZ, settings, templateId).isEmpty(),
                (hit, reject) -> {
                    ChunkService.Index index = chunkService.index(world, hit[0], hit[1]);
                    List<Candidate> candidates = index == null
                            ? List.of()
                            : candidates(world, index, hit[0], hit[1], settings, templateId);
                    if (cancelled) {
                        finish.accept(new SpawnResult.Cancelled());
                        return;
                    }
                    if (candidates.isEmpty()) {
                        reject.run();
                        return;
                    }
                    prepare(pick(candidates), result -> {
                        if (result instanceof SpawnResult.Unsuitable) {
                            rejectedPlaces.incrementAndGet();
                            reject.run();
                            return;
                        }
                        finish.accept(result);
                    });
                },
                () -> spawnRandom(world, templateId, attemptsLeft - 1, finish));
    }

    private @NotNull List<Candidate> candidates(
            @NotNull World world,
            @NotNull ChunkService.Index index,
            int blockX,
            int blockZ,
            @NotNull Settings settings,
            @Nullable String templateId
    ) {
        ChunkService.ColumnSample sample = index.sample(blockX, blockZ);
        List<Candidate> candidates = new ArrayList<>();
        for (DungeonTemplate template : templates(world.getName(), sample, settings, templateId)) {
            Clipboard clipboard = schematicService.clipboard(template.file());
            if (clipboard == null) {
                continue;
            }
            int y = ChunkService.placementY(sample) + switch (template.placement()) {
                case GROUND, WATER -> template.height();
                case AIR -> ThreadLocalRandom.current().nextInt(template.airMin(), template.airMax() + 1);
            };
            Location location = new Location(world, blockX, y, blockZ);
            Bounds bounds = schematicService.pasteBounds(location, clipboard, template.schematic());
            if (bounds.minY() >= world.getMinHeight() && bounds.maxY() < world.getMaxHeight()
                    && chunkService.fits(world, bounds, sample.highestY, template.placement(), template.flatness())) {
                candidates.add(new Candidate(location, template, sample.highestY));
            }
        }
        return candidates;
    }

    private @NotNull List<DungeonTemplate> templates(
            @NotNull String worldName,
            @NotNull ChunkService.ColumnSample sample,
            @NotNull Settings settings,
            @Nullable String templateId
    ) {
        boolean water = ChunkService.isWaterSurface(sample.top);
        return settings.templates().stream()
                .filter(template -> templateId == null || template.id().equals(templateId))
                .filter(template -> template.matches(worldName, sample.biome))
                .filter(template -> template.placement().accepts(water))
                .filter(template -> schematicService.clipboard(template.file()) != null)
                .filter(template -> template.limit() <= 0
                        || dungeonRepository.countByTemplate(template.id()) < template.limit())
                .toList();
    }

    private @NotNull Candidate pick(@NotNull List<Candidate> candidates) {
        int totalWeight = 0;
        for (Candidate candidate : candidates) {
            totalWeight += candidate.template().weight();
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Candidate candidate : candidates) {
            roll -= candidate.template().weight();
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private boolean isFarEnough(
            @NotNull List<DungeonRecord> existing,
            int x,
            int z,
            @NotNull Settings.Region region
    ) {
        long minDistanceSquared = (long) region.minDistance() * region.minDistance();
        for (DungeonRecord record : existing) {
            long deltaX = (record.getMinX() + record.getMaxX()) / 2L - x;
            long deltaZ = (record.getMinZ() + record.getMaxZ()) / 2L - z;
            if (deltaX * deltaX + deltaZ * deltaZ < minDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    private void prepare(@NotNull Candidate candidate, @NotNull Consumer<SpawnResult> finish) {
        Location location = candidate.location();
        DungeonTemplate template = candidate.template();
        World world = location.getWorld();
        Settings settings = configManager.settings();
        RegionManager regions = regionManager(world);
        Clipboard clipboard = schematicService.clipboard(template.file());
        if (regions == null || clipboard == null) {
            finish.accept(new SpawnResult.Unsuitable());
            return;
        }

        Bounds bounds = schematicService.pasteBounds(location, clipboard, template.schematic());
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()) {
            finish.accept(new SpawnResult.Unsuitable());
            return;
        }
        String regionId = newRegionId();
        ProtectedCuboidRegion region = createRegion(world, bounds, regionId, settings.region());
        if (overlaps(regions, region, settings.region())) {
            finish.accept(new SpawnResult.Unsuitable());
            return;
        }
        if (!reserveSlot(settings.region().maxDungeons())) {
            finish.accept(new SpawnResult.LimitReached(settings.region().maxDungeons()));
            return;
        }

        Consumer<SpawnResult> done = result -> {
            schematicService.release(world, bounds);
            finish.accept(result);
        };
        schematicService.loadChunks(world, bounds, bounds.anchor(world), loaded -> {
            if (!loaded) {
                releaseSlot();
                done.accept(new SpawnResult.Unsuitable());
                return;
            }
            if (!chunkService.fits(world, bounds, candidate.surfaceY(), template.placement(),
                    template.flatness())) {
                releaseSlot();
                done.accept(new SpawnResult.Unsuitable());
                return;
            }
            build(location, regions, region, regionId, template, clipboard, bounds, done);
        });
    }

    private void build(
            @NotNull Location location,
            @NotNull RegionManager regions,
            @NotNull ProtectedCuboidRegion region,
            @NotNull String regionId,
            @NotNull DungeonTemplate template,
            @NotNull Clipboard clipboard,
            @NotNull Bounds bounds,
            @NotNull Consumer<SpawnResult> finish
    ) {
        World world = location.getWorld();
        long buildStartedAt = System.currentTimeMillis();
        schematicService.capture(world, bounds, terrain -> {
            long capturedAt = System.currentTimeMillis();
            Logger.debug("Данж " + regionId + ": бэкап территории " + (capturedAt - buildStartedAt) + " мс");
            if (terrain == null) {
                releaseSlot();
                finish.accept(new SpawnResult.Failed(SpawnResult.Reason.BACKUP_FAILED));
                return;
            }
            if (cancelled) {
                releaseSlot();
                finish.accept(new SpawnResult.Cancelled());
                return;
            }
            schematicService.paste(world, clipboard, bounds, template.schematic(), pasted -> {
                Logger.debug("Данж " + regionId + ": вставка схемы " + (System.currentTimeMillis() - capturedAt) + " мс");
                if (!pasted) {
                    schematicService.restore(world, bounds, terrain, restored -> {
                        releaseSlot();
                        finish.accept(new SpawnResult.Failed(SpawnResult.Reason.PASTE_FAILED));
                    });
                    return;
                }
                if (cancelled) {
                    schematicService.restore(world, bounds, terrain, restored -> {
                        releaseSlot();
                        finish.accept(new SpawnResult.Cancelled());
                    });
                    return;
                }

                dungeonRepository.save(
                        new DungeonRecord(regionId, world.getUID(), world.getName(), bounds, template.file(), template.id()),
                        terrain);
                populateShulkers(world, bounds, regionId, template);
                regions.addRegion(region);
                releaseSlot();
                finish.accept(new SpawnResult.Success(regionId, location, template.name()));
            });
        });
    }

    private void populateShulkers(
            @NotNull World world,
            @NotNull Bounds bounds,
            @NotNull String regionId,
            @NotNull DungeonTemplate template
    ) {
        if (lootService.poolSize(LootService.STORAGE_POOL) == 0) {
            Logger.warn("Пул лута пуст, шалкеры данжа " + regionId + " останутся пустыми. Добавьте предметы: /rstructures menu");
        }

        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (BlockState state : chunk.getTileEntities(block -> isFilled(template, block.getType())
                        && bounds.contains(block.getX(), block.getY(), block.getZ()), false)) {
                    if (!(state instanceof Container box)) {
                        continue;
                    }
                    if (!template.containers().contains(state.getType())) {
                        shulkerService.fillPlain(box);
                        continue;
                    }
                    shulkerService.populate(box, regionId, template);
                }
            }
        }
    }

    private boolean isFilled(@NotNull DungeonTemplate template, @NotNull Material type) {
        return template.containers().contains(type) || template.lootContainers().contains(type);
    }

    private @NotNull ProtectedCuboidRegion createRegion(
            @NotNull World world,
            @NotNull Bounds bounds,
            @NotNull String regionId,
            @NotNull Settings.Region settings
    ) {
        int padding = settings.padding();
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(
                settings.name(regionId),
                BlockVector3.at(
                        bounds.minX() - padding,
                        Math.max(world.getMinHeight(), bounds.minY() - padding),
                        bounds.minZ() - padding),
                BlockVector3.at(
                        bounds.maxX() + padding,
                        Math.min(world.getMaxHeight() - 1, bounds.maxY() + padding),
                        bounds.maxZ() + padding)
        );
        settings.flags().forEach(region::setFlag);
        return region;
    }

    private boolean overlaps(
            @NotNull RegionManager regions,
            @NotNull ProtectedCuboidRegion region,
            @NotNull Settings.Region settings
    ) {
        for (ProtectedRegion other : regions.getApplicableRegions(region)) {
            if (settings.avoidForeignRegions() || other.getId().startsWith(settings.prefix())) {
                return true;
            }
        }
        return false;
    }

    private void restore(
            @NotNull DungeonRecord record,
            @NotNull World world,
            byte @NotNull [] terrain,
            @NotNull Consumer<SpawnResult.Undo> finish
    ) {
        String regionId = record.getRegionId();
        schematicService.restore(world, record.bounds(), terrain, restored -> {
            if (!restored) {
                finish.accept(SpawnResult.Undo.RESTORE_FAILED);
                return;
            }
            RegionManager regions = regionManager(world);
            if (regions != null) {
                regions.removeRegion(configManager.settings().region().name(regionId));
            }
            shulkerService.forget(regionId);
            dungeonRepository.delete(record);
            finish.accept(SpawnResult.Undo.SUCCESS);
        });
    }

    private void startAutoUndo(@NotNull DungeonRecord record, @Nullable Settings.Auto auto) {
        if (auto == null) {
            return;
        }
        String regionId = record.getRegionId();
        UUID worldId = record.getWorldUuid();
        Bounds bounds = record.bounds();
        AtomicInteger secondsLeft = new AtomicInteger(auto.delaySeconds());

        SchedulerUtil.Task task = scheduler.runGlobalTimer(TICKS_PER_SECOND, () -> {
            int seconds = secondsLeft.getAndDecrement();
            if (seconds > 0) {
                if (auto.actionbar()) {
                    showTimer(worldId, bounds, seconds);
                }
                return;
            }
            cancelAutoUndo(regionId);
            undo(regionId, result -> {
                if (result == SpawnResult.Undo.SUCCESS) {
                    respawn(worldId, auto, RESPAWN_ATTEMPTS);
                }
            });
        });

        autoUndoSeconds.put(regionId, secondsLeft);
        SchedulerUtil.Task previous = autoUndoTasks.put(regionId, task);
        if (previous != null) {
            previous.cancel();
        }
        Logger.debug("Авто-удаление данжа " + regionId + " через " + auto.delaySeconds() + " с");
    }

    private void showTimer(@NotNull UUID worldId, @NotNull Bounds bounds, int seconds) {
        Component message = configManager.message("auto.timer", Map.of("time", configManager.formatTime(seconds)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runFor(player, () -> {
                Location location = player.getLocation();
                if (player.getWorld().getUID().equals(worldId)
                        && bounds.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                    player.sendActionBar(message);
                }
            });
        }
    }

    private void respawn(@NotNull UUID worldId, @NotNull Settings.Auto auto, int attemptsLeft) {
        if (!auto.respawn()) {
            return;
        }
        scheduler.runGlobalLater(auto.respawnDelayTicks(), () -> {
            World world = Bukkit.getWorld(worldId);
            if (world == null || auto.respawnOnlyWithPlayers() && world.getPlayerCount() == 0) {
                return;
            }
            spawnRandom(world, result -> {
                if (result instanceof SpawnResult.Success || result instanceof SpawnResult.Cancelled) {
                    return;
                }
                if (result instanceof SpawnResult.Unsuitable && attemptsLeft > 1) {
                    respawn(worldId, auto, attemptsLeft - 1);
                    return;
                }
                Logger.warn("Авто-респавн данжа в мире " + world.getName() + " не удался: " + switch (result) {
                    case SpawnResult.LimitReached limit -> "лимит данжей " + limit.limit();
                    case SpawnResult.Failed failed -> configManager.raw(
                            "spawn.reasons." + failed.reason().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    default -> "не нашлось места";
                });
            });
        });
    }

    private void cancelAutoUndo(@NotNull String regionId) {
        autoUndoSeconds.remove(regionId);
        SchedulerUtil.Task task = autoUndoTasks.remove(regionId);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean reserveSlot(int maxDungeons) {
        synchronized (slotLock) {
            if (maxDungeons > 0 && dungeonRepository.count() + pendingSpawns >= maxDungeons) {
                return false;
            }
            pendingSpawns++;
            return true;
        }
    }

    private void releaseSlot() {
        synchronized (slotLock) {
            pendingSpawns = Math.max(0, pendingSpawns - 1);
        }
    }

    private @Nullable RegionManager regionManager(@NotNull World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    private @NotNull String newRegionId() {
        String regionId;
        do {
            regionId = Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), REGION_ID_RADIX);
        } while (dungeonRepository.find(regionId) != null);
        return regionId;
    }

    private @NotNull String normalize(@NotNull String rawId) {
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String prefix = configManager.settings().region().prefix();
        return !prefix.isEmpty() && id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private record Candidate(@NotNull Location location, @NotNull DungeonTemplate template, int surfaceY) {
    }
}
