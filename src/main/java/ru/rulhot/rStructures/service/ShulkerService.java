package ru.rulhot.rStructures.service;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.database.model.ShulkerRecord;
import ru.rulhot.rStructures.database.repository.DungeonRepository;
import ru.rulhot.rStructures.database.repository.ShulkerRepository;
import ru.rulhot.rStructures.hook.HologramHook;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.Bounds.BlockPos;
import ru.rulhot.rStructures.model.CustomItem;
import ru.rulhot.rStructures.model.DungeonTemplate;
import ru.rulhot.rStructures.model.DungeonTemplate.OpenType;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.SchedulerUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class ShulkerService {

    private static final double MAX_PERCENT = 100D;
    private static final double BLOCK_CENTER = 0.5D;
    private static final double PARTICLE_HEIGHT = 1D;
    private static final @NotNull String HOLOGRAM_PREFIX = "rstructures_";
    private static final long DENY_COOLDOWN_MILLIS = 1000L;
    private static final long MILLIS_ROUNDING = 999L;
    private static final long TIMER_PERIOD_TICKS = 4L;
    private static final double TIMER_BAR_RADIUS = 8D;
    private static final @NotNull String TIME_PLACEHOLDER = "{time}";

    private final @NotNull ConfigManager configManager;
    private final @NotNull ItemService itemService;
    private final @NotNull LootService lootService;
    private final @NotNull ShulkerRepository shulkerRepository;
    private final @NotNull DungeonRepository dungeonRepository;
    private final @NotNull HologramHook hologramHook;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull Map<BlockPos, Integer> hits = new ConcurrentHashMap<>();
    private final @NotNull Map<BlockPos, String> shownTime = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, Long> deniedAt = new ConcurrentHashMap<>();
    private final @NotNull Set<BlockPos> ticking = ConcurrentHashMap.newKeySet();
    private volatile @Nullable SchedulerUtil.Task timerTask;

    public void forgetPlayer(@NotNull UUID playerId) {
        deniedAt.remove(playerId);
    }

    public boolean isContainerType(@NotNull Material material) {
        return configManager.settings().containers().contains(material);
    }

    public boolean isShulker(@NotNull Block block) {
        return isContainerType(block.getType());
    }

    public @Nullable ShulkerRecord find(@NotNull Block block) {
        if (!isShulker(block)) {
            return null;
        }
        return shulkerRepository.find(pos(block));
    }

    public boolean register(@NotNull Block block) {
        if (!isShulker(block)) {
            return false;
        }
        DungeonRecord dungeon = dungeonRepository.findContaining(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        String regionId = dungeon == null ? null : dungeon.getRegionId();
        ShulkerRecord record = new ShulkerRecord(pos(block), regionId, OpenType.KEY, 0L);
        if (!shulkerRepository.insert(record)) {
            return false;
        }
        showHologram(record);
        return true;
    }

    public boolean unregister(@NotNull Block block) {
        ShulkerRecord record = shulkerRepository.find(pos(block));
        if (record != null) {
            hologramHook.remove(hologramName(record));
            hits.remove(record.pos());
            shownTime.remove(record.pos());
        }
        return shulkerRepository.delete(pos(block));
    }

    public boolean refill(@NotNull Block block) {
        if (lootService.poolSize(LootService.STORAGE_POOL) == 0 || !(block.getState(false) instanceof Container container)) {
            return false;
        }
        Inventory inventory = inventory(container);
        inventory.clear();
        lootService.fill(inventory, LootService.STORAGE_POOL);
        return true;
    }

    public boolean refillAll(@NotNull String regionId) {
        if (lootService.poolSize(LootService.STORAGE_POOL) == 0) {
            return false;
        }
        for (ShulkerRecord record : shulkerRepository.byRegion(regionId)) {
            World world = Bukkit.getWorld(record.getWorldUuid());
            if (world == null) {
                continue;
            }
            int x = record.getX();
            int y = record.getY();
            int z = record.getZ();
            scheduler.runAt(new Location(world, x, y, z), () -> {
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    refill(world.getBlockAt(x, y, z));
                }
            });
        }
        return true;
    }

    public @NotNull Progress progress(@NotNull String regionId) {
        List<ShulkerRecord> shulkers = shulkerRepository.byRegion(regionId);
        int opened = 0;
        for (ShulkerRecord record : shulkers) {
            if (record.isOpened()) {
                opened++;
            }
        }
        return new Progress(opened, shulkers.size());
    }

    public void fillPlain(@NotNull Container box) {
        lootService.fill(inventory(box), LootService.PLAIN_POOL);
    }

    public void populate(@NotNull Container box, @NotNull String regionId, @NotNull DungeonTemplate template) {
        Inventory inventory = inventory(box);
        inventory.clear();
        lootService.fill(inventory, LootService.STORAGE_POOL);
        ShulkerRecord record = new ShulkerRecord(
                pos(box.getBlock()), regionId, template.openType(), template.openSeconds());
        if (shulkerRepository.insert(record)) {
            showHologram(record);
        }
    }

    public void forget(@NotNull String regionId) {
        for (ShulkerRecord record : shulkerRepository.deleteByRegion(regionId)) {
            hits.remove(record.pos());
            shownTime.remove(record.pos());
            hologramHook.remove(hologramName(record));
        }
    }

    public boolean open(@NotNull Player player, @NotNull Block block, @NotNull ShulkerRecord record) {
        if (record.isOpened()) {
            return true;
        }
        if (record.opensWith(OpenType.TIMER)) {
            startTimer(player, record);
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (record.opensWith(OpenType.PICKAXE) || !itemService.is(held, CustomItem.KEY)) {
            deny(player, block, record.opensWith(OpenType.PICKAXE) ? "shulker.closed-pickaxe" : "shulker.closed");
            return false;
        }

        unlock(block, record);
        announce("shulker.opened", Map.of("player", player.getName()));
        DungeonTemplate template = template(record.getRegionId());
        double saveChance = template == null
                ? configManager.settings().items().keySaveChance()
                : template.keySaveChance();
        if (ThreadLocalRandom.current().nextDouble(MAX_PERCENT) < saveChance) {
            play(player, configManager.settings().sounds().keySaved());
            configManager.send(player, "shulker.key-saved");
        } else {
            held.subtract(1);
        }
        return true;
    }

    public boolean hit(@NotNull Player player, @NotNull Block block, @NotNull ShulkerRecord record) {
        if (record.isOpened() || !record.opensWith(OpenType.PICKAXE)) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!itemService.is(held, CustomItem.PICKAXE)) {
            deny(player, block, "shulker.closed-pickaxe");
            return false;
        }

        DungeonTemplate template = template(record.getRegionId());
        int required = template == null ? configManager.settings().items().pickaxeHits() : template.pickaxeHits();
        int done = hits.merge(record.pos(), 1, Integer::sum);
        held.damage(1, player);
        if (done < required) {
            player.sendActionBar(configManager.message("shulker.pickaxe-progress", Map.of("remaining", required - done)));
            play(player, configManager.settings().sounds().shulkerClosed());
            return false;
        }
        unlock(block, record);
        announce("shulker.opened-pickaxe", Map.of("player", player.getName()));
        return true;
    }

    public void startTimers() {
        SchedulerUtil.Task previous = timerTask;
        if (previous != null) {
            previous.cancel();
        }
        timerTask = scheduler.runGlobalTimer(TIMER_PERIOD_TICKS, () -> {
            long now = System.currentTimeMillis();
            for (ShulkerRecord record : shulkerRepository.all()) {
                World world = record.isOpened() || record.getOpenAt() <= 0L ? null : Bukkit.getWorld(record.getWorldUuid());
                if (world == null || !ticking.add(record.pos())) {
                    continue;
                }
                scheduler.runAt(new Location(world, record.getX(), record.getY(), record.getZ()), () -> {
                    try {
                        tick(world, record, now);
                    } finally {
                        ticking.remove(record.pos());
                    }
                });
            }
        });
    }

    private void tick(@NotNull World world, @NotNull ShulkerRecord record, long now) {
        if (record.isOpened()) {
            return;
        }
        if (now < record.getOpenAt()) {
            String time = configManager.formatTime(secondsLeft(record, System.currentTimeMillis()));
            if (!time.equals(shownTime.put(record.pos(), time))) {
                showHologram(record);
                if (!hologramsVisible()) {
                    showTimerBar(world, record, configManager.message("shulker.timer-bar", Map.of("time", time)));
                }
            }
            return;
        }
        shownTime.remove(record.pos());
        if (!hologramsVisible()) {
            showTimerBar(world, record, Component.empty());
        }
        announce("shulker.opened-timer", Map.of());
        if (world.isChunkLoaded(record.getX() >> 4, record.getZ() >> 4)) {
            unlock(world.getBlockAt(record.getX(), record.getY(), record.getZ()), record);
            return;
        }
        shulkerRepository.markOpened(record);
        showHologram(record);
    }

    private boolean hologramsVisible() {
        return hologramHook != HologramHook.NONE && configManager.settings().holograms().enabled();
    }

    private void showTimerBar(@NotNull World world, @NotNull ShulkerRecord record, @NotNull Component message) {
        Location center = new Location(world,
                record.getX() + BLOCK_CENTER, record.getY() + BLOCK_CENTER, record.getZ() + BLOCK_CENTER);
        for (Player player : center.getNearbyPlayers(TIMER_BAR_RADIUS)) {
            player.sendActionBar(message);
        }
    }

    private void startTimer(@NotNull Player player, @NotNull ShulkerRecord record) {
        long now = System.currentTimeMillis();
        if (record.getOpenAt() <= 0L) {
            record.setOpenAt(now + TimeUnit.SECONDS.toMillis(record.getOpenSeconds()));
            shulkerRepository.save(record);
            showHologram(record);
            configManager.send(player, "shulker.timer-started",
                    Map.of("time", configManager.formatTime(record.getOpenSeconds())));
            return;
        }
        if (throttled(player)) {
            return;
        }
        configManager.send(player, "shulker.timer-left", Map.of("time", configManager.formatTime(secondsLeft(record, now))));
    }

    private long secondsLeft(@NotNull ShulkerRecord record, long now) {
        return Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(record.getOpenAt() - now + MILLIS_ROUNDING));
    }

    private void unlock(@NotNull Block block, @NotNull ShulkerRecord record) {
        hits.remove(record.pos());
        shulkerRepository.markOpened(record);
        showHologram(record);
        Sound sound = configManager.settings().sounds().shulkerOpen();
        if (sound != null) {
            block.getWorld().playSound(sound,
                    block.getX() + BLOCK_CENTER, block.getY() + BLOCK_CENTER, block.getZ() + BLOCK_CENTER);
        }
        spawn(block, configManager.settings().particles().shulkerOpen());
    }

    private void announce(@NotNull String messageKey, @NotNull Map<String, String> placeholders) {
        if (configManager.raw(messageKey).isEmpty()) {
            return;
        }
        Component message = configManager.message(messageKey, placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    private void deny(@NotNull Player player, @NotNull Block block, @NotNull String messageKey) {
        if (throttled(player)) {
            return;
        }
        play(player, configManager.settings().sounds().shulkerClosed());
        spawn(block, configManager.settings().particles().shulkerClosed());
        configManager.send(player, messageKey);
    }

    private boolean throttled(@NotNull Player player) {
        long now = System.currentTimeMillis();
        Long last = deniedAt.get(player.getUniqueId());
        if (last != null && now - last < DENY_COOLDOWN_MILLIS) {
            return true;
        }
        deniedAt.put(player.getUniqueId(), now);
        return false;
    }

    public boolean isComplete(
            @NotNull World world,
            @NotNull String regionId,
            boolean requireOpened,
            boolean requireLooted
    ) {
        List<ShulkerRecord> shulkers = shulkerRepository.byRegion(regionId);
        if (shulkers.isEmpty()) {
            return false;
        }
        for (ShulkerRecord record : shulkers) {
            if (requireOpened && !record.isOpened()) {
                return false;
            }
            if (requireLooted && !isLooted(world, record)) {
                return false;
            }
        }
        return true;
    }

    public void refreshHolograms() {
        removeHolograms();
        for (ShulkerRecord record : shulkerRepository.all()) {
            showHologram(record);
        }
    }

    public @Nullable String openType(@NotNull String regionId) {
        List<ShulkerRecord> shulkers = shulkerRepository.byRegion(regionId);
        return shulkers.isEmpty() ? null : shulkers.getFirst().getOpenType();
    }

    public void removeHolograms() {
        for (ShulkerRecord record : shulkerRepository.all()) {
            hologramHook.remove(hologramName(record));
        }
    }

    private void showHologram(@NotNull ShulkerRecord record) {
        Settings.Holograms settings = configManager.settings().holograms();
        World world = Bukkit.getWorld(record.getWorldUuid());
        List<String> lines = record.isOpened() ? settings.opened() : settings.closed();
        if (!record.isOpened() && record.getOpenAt() > 0L) {
            String time = configManager.formatTime(secondsLeft(record, System.currentTimeMillis()));
            lines = settings.timer().stream().map(line -> line.replace(TIME_PLACEHOLDER, time)).toList();
        }
        if (!settings.enabled() || world == null || lines.isEmpty()) {
            hologramHook.remove(hologramName(record));
            return;
        }
        hologramHook.show(hologramName(record), new Location(world,
                record.getX() + BLOCK_CENTER, record.getY() + settings.height(), record.getZ() + BLOCK_CENTER), lines, settings);
    }

    private @NotNull String hologramName(@NotNull ShulkerRecord record) {
        return (HOLOGRAM_PREFIX + Integer.toHexString(record.getWorldUuid().hashCode()) + '_'
                + record.getX() + '_' + record.getY() + '_' + record.getZ()).replace('-', 'm');
    }

    private void spawn(@NotNull Block block, @Nullable Settings.Effect effect) {
        if (effect != null) {
            block.getWorld().spawnParticle(effect.particle(),
                    block.getX() + BLOCK_CENTER, block.getY() + PARTICLE_HEIGHT, block.getZ() + BLOCK_CENTER,
                    effect.count(), effect.spread(), effect.spread(), effect.spread(), effect.speed());
        }
    }

    public @NotNull Inventory inventory(@NotNull Container container) {
        return container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
    }

    private void play(@NotNull Player player, @Nullable Sound sound) {
        if (sound != null) {
            player.playSound(sound);
        }
    }

    private boolean isLooted(@NotNull World world, @NotNull ShulkerRecord record) {
        if (!world.isChunkLoaded(record.getX() >> 4, record.getZ() >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(record.getX(), record.getY(), record.getZ());
        return !(block.getState(false) instanceof Container container) || inventory(container).isEmpty();
    }

    private @Nullable DungeonTemplate template(@Nullable String regionId) {
        DungeonRecord dungeon = regionId == null ? null : dungeonRepository.find(regionId);
        if (dungeon == null) {
            return null;
        }
        return configManager.settings().templateOf(dungeon.getTemplateId(), dungeon.getSchematic());
    }

    private @NotNull BlockPos pos(@NotNull Block block) {
        return BlockPos.of(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public record Progress(int opened, int total) {
    }
}
