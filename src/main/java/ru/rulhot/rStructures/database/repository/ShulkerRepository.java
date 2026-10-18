package ru.rulhot.rStructures.database.repository;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.DatabaseManager;
import ru.rulhot.rStructures.database.model.ShulkerRecord;
import ru.rulhot.rStructures.model.Bounds.BlockPos;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShulkerRepository {

    private final @NotNull DatabaseManager database;
    private final @NotNull Dao<ShulkerRecord, Integer> dao;
    private final @NotNull Map<BlockPos, ShulkerRecord> cache = new ConcurrentHashMap<>();

    public ShulkerRepository(@NotNull DatabaseManager database) throws SQLException {
        this.database = database;
        this.dao = database.dao(ShulkerRecord.class);
        for (ShulkerRecord record : dao.queryForAll()) {
            cache.put(record.pos(), record);
        }
    }

    public @NotNull Collection<ShulkerRecord> all() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public @Nullable ShulkerRecord find(@NotNull BlockPos pos) {
        return cache.get(pos);
    }

    public @NotNull List<ShulkerRecord> byRegion(@NotNull String regionId) {
        return cache.values().stream()
                .filter(record -> regionId.equals(record.getRegionId()))
                .toList();
    }

    public boolean insert(@NotNull ShulkerRecord record) {
        if (cache.putIfAbsent(record.pos(), record) != null) {
            return false;
        }
        database.execute("Не удалось сохранить шалкер", () -> dao.create(record));
        return true;
    }

    public void markOpened(@NotNull ShulkerRecord record) {
        record.setOpened(true);
        database.execute("Не удалось обновить состояние шалкера", () -> dao.update(record));
    }

    public void save(@NotNull ShulkerRecord record) {
        database.execute("Не удалось обновить состояние шалкера", () -> dao.update(record));
    }

    public boolean delete(@NotNull BlockPos pos) {
        ShulkerRecord record = cache.remove(pos);
        if (record == null) {
            return false;
        }
        database.execute("Не удалось удалить шалкер", () -> dao.delete(record));
        return true;
    }

    public @NotNull List<ShulkerRecord> deleteByRegion(@NotNull String regionId) {
        List<ShulkerRecord> removed = byRegion(regionId);
        for (ShulkerRecord record : removed) {
            cache.remove(record.pos());
        }
        database.execute("Не удалось удалить шалкеры данжа " + regionId, () -> {
            DeleteBuilder<ShulkerRecord, Integer> builder = dao.deleteBuilder();
            builder.where().eq("region_id", regionId);
            builder.delete();
        });
        return removed;
    }
}
