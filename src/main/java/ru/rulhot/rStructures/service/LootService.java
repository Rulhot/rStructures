package ru.rulhot.rStructures.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.model.LootItemRecord;
import ru.rulhot.rStructures.database.repository.LootRepository;
import ru.rulhot.rStructures.database.repository.ValueRepository;
import ru.rulhot.rStructures.model.LootCategory;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public final class LootService {

    public static final int DEFAULT_CHANCE = 50;
    public static final int MAX_CHANCE = 100;
    public static final @NotNull String STORAGE_POOL = "default";
    public static final @NotNull String PLAIN_POOL = "plain";
    private static final @NotNull String KEY_CHANCE_PREFIX = "key-chance.";

    private final @NotNull LootRepository lootRepository;
    private final @NotNull ValueRepository valueRepository;

    public int poolSize(@NotNull String pool) {
        return records(pool).size();
    }

    public int fill(@NotNull Inventory inventory, @NotNull String pool) {
        List<Integer> freeSlots = freeSlots(inventory);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int placed = 0;
        for (LootItemRecord record : records(pool)) {
            if (freeSlots.isEmpty()) {
                break;
            }
            if (random.nextInt(MAX_CHANCE) >= record.getChance()) {
                continue;
            }
            ItemStack item = deserialize(record);
            if (item == null) {
                continue;
            }
            inventory.setItem(freeSlots.remove(random.nextInt(freeSlots.size())), item);
            placed++;
        }
        return placed;
    }

    public void add(@NotNull String pool, @NotNull ItemStack item) {
        lootRepository.add(new LootItemRecord(pool, DEFAULT_CHANCE, item.serializeAsBytes()));
    }

    public void adjustChance(@NotNull String pool, int index, int delta) {
        List<LootItemRecord> records = records(pool);
        if (index < 0 || index >= records.size()) {
            return;
        }
        LootItemRecord record = records.get(index);
        lootRepository.setChance(record, Math.clamp(record.getChance() + delta, 1, MAX_CHANCE));
    }

    public void remove(@NotNull String pool, int index) {
        List<LootItemRecord> records = records(pool);
        if (index < 0 || index >= records.size()) {
            return;
        }
        lootRepository.remove(records.get(index));
    }

    public @NotNull List<Entry> entries(@NotNull String pool, int offset, int count) {
        List<LootItemRecord> records = records(pool);
        List<Entry> entries = new ArrayList<>();
        for (int index = Math.max(0, offset); index < Math.min(records.size(), offset + count); index++) {
            LootItemRecord record = records.get(index);
            entries.add(new Entry(deserialize(record), record.getChance()));
        }
        return entries;
    }

    public int keyChance(@NotNull LootCategory category) {
        return valueRepository.get(KEY_CHANCE_PREFIX + category.id(), 0);
    }

    public void adjustKeyChance(@NotNull LootCategory category, int delta) {
        valueRepository.set(KEY_CHANCE_PREFIX + category.id(), Math.clamp(keyChance(category) + delta, 0, MAX_CHANCE));
    }

    public boolean rollKey(@NotNull LootCategory category) {
        return ThreadLocalRandom.current().nextInt(MAX_CHANCE) < keyChance(category);
    }

    private @NotNull List<LootItemRecord> records(@NotNull String pool) {
        return lootRepository.all().stream()
                .filter(record -> record.getPool().equals(pool))
                .toList();
    }

    private @NotNull List<Integer> freeSlots(@NotNull Inventory inventory) {
        List<Integer> freeSlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.isEmpty()) {
                freeSlots.add(slot);
            }
        }
        return freeSlots;
    }

    private @Nullable ItemStack deserialize(@NotNull LootItemRecord record) {
        try {
            return ItemStack.deserializeBytes(record.getItemData());
        } catch (RuntimeException exception) {
            Logger.error("Не удалось прочитать предмет лута id=" + record.getId(), exception);
            return null;
        }
    }

    public record Entry(@Nullable ItemStack item, int chance) {
    }
}
