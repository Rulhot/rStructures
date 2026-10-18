package ru.rulhot.rStructures.database.repository;

import com.j256.ormlite.dao.Dao;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.DatabaseManager;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.database.model.TerrainRecord;
import ru.rulhot.rStructures.util.logger.Logger;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DungeonRepository {

    private final @NotNull DatabaseManager database;
    private final @NotNull Dao<DungeonRecord, Integer> dungeonDao;
    private final @NotNull Dao<TerrainRecord, String> terrainDao;
    private final @NotNull Map<String, DungeonRecord> cache = new ConcurrentHashMap<>();

    public DungeonRepository(@NotNull DatabaseManager database) throws SQLException {
        this.database = database;
        this.dungeonDao = database.dao(DungeonRecord.class);
        this.terrainDao = database.dao(TerrainRecord.class);
        for (DungeonRecord record : dungeonDao.queryForAll()) {
            cache.put(record.getRegionId(), record);
        }
    }

    public @NotNull List<DungeonRecord> all() {
        return cache.values().stream()
                .sorted(Comparator.comparing(DungeonRecord::getRegionId))
                .toList();
    }

    public @NotNull List<DungeonRecord> inWorld(@NotNull UUID worldUuid) {
        return cache.values().stream()
                .filter(record -> record.getWorldUuid().equals(worldUuid))
                .toList();
    }

    public int count() {
        return cache.size();
    }

    public int countByTemplate(@NotNull String templateId) {
        int count = 0;
        for (DungeonRecord record : cache.values()) {
            if (record.getTemplateId().equals(templateId)) {
                count++;
            }
        }
        return count;
    }

    public @Nullable DungeonRecord find(@NotNull String regionId) {
        return cache.get(regionId);
    }

    public @Nullable DungeonRecord findContaining(@NotNull UUID worldUuid, int x, int y, int z) {
        for (DungeonRecord record : cache.values()) {
            if (record.getWorldUuid().equals(worldUuid) && record.bounds().contains(x, y, z)) {
                return record;
            }
        }
        return null;
    }

    public void save(@NotNull DungeonRecord record, byte @NotNull [] terrain) {
        cache.put(record.getRegionId(), record);
        database.transaction("Не удалось сохранить данж " + record.getRegionId(), () -> {
            dungeonDao.create(record);
            terrainDao.createOrUpdate(new TerrainRecord(record.getRegionId(), terrain));
        });
    }

    public void delete(@NotNull DungeonRecord record) {
        cache.remove(record.getRegionId());
        database.transaction("Не удалось удалить данж " + record.getRegionId(), () -> {
            dungeonDao.delete(record);
            terrainDao.deleteById(record.getRegionId());
        });
    }

    public void loadTerrain(@NotNull String regionId, @NotNull Consumer<byte @Nullable []> callback) {
        database.execute("Не удалось прочитать бэкап территории " + regionId, () -> {
            TerrainRecord terrain = null;
            try {
                terrain = terrainDao.queryForId(regionId);
            } catch (SQLException exception) {
                Logger.error("Не удалось прочитать бэкап территории " + regionId, exception);
            }
            callback.accept(terrain == null ? null : terrain.getData());
        });
    }
}
