package ru.rulhot.rStructures.database.repository;

import com.j256.ormlite.dao.Dao;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.database.DatabaseManager;
import ru.rulhot.rStructures.database.model.LootItemRecord;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LootRepository {

    private final @NotNull DatabaseManager database;
    private final @NotNull Dao<LootItemRecord, Integer> dao;
    private volatile @NotNull List<LootItemRecord> cache;

    public LootRepository(@NotNull DatabaseManager database) throws SQLException {
        this.database = database;
        this.dao = database.dao(LootItemRecord.class);
        this.cache = List.copyOf(dao.queryForAll());
    }

    public @NotNull List<LootItemRecord> all() {
        return cache;
    }

    public synchronized void add(@NotNull LootItemRecord record) {
        List<LootItemRecord> updated = new ArrayList<>(cache);
        updated.add(record);
        cache = List.copyOf(updated);
        database.execute("Не удалось сохранить предмет лута", () -> dao.create(record));
    }

    public synchronized void setChance(@NotNull LootItemRecord record, int chance) {
        record.setChance(chance);
        database.execute("Не удалось обновить шанс лута", () -> dao.update(record));
    }

    public synchronized void remove(@NotNull LootItemRecord removed) {
        List<LootItemRecord> updated = new ArrayList<>(cache);
        updated.remove(removed);
        cache = List.copyOf(updated);
        database.execute("Не удалось удалить предмет лута", () -> dao.delete(removed));
    }
}
