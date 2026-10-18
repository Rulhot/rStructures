package ru.rulhot.rStructures.database.repository;

import com.j256.ormlite.dao.Dao;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.database.DatabaseManager;
import ru.rulhot.rStructures.database.model.ValueRecord;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ValueRepository {

    private final @NotNull DatabaseManager database;
    private final @NotNull Dao<ValueRecord, String> dao;
    private final @NotNull Map<String, Integer> cache = new ConcurrentHashMap<>();

    public ValueRepository(@NotNull DatabaseManager database) throws SQLException {
        this.database = database;
        this.dao = database.dao(ValueRecord.class);
        for (ValueRecord record : dao.queryForAll()) {
            cache.put(record.getKey(), record.getValue());
        }
    }

    public int get(@NotNull String key, int fallback) {
        return cache.getOrDefault(key, fallback);
    }

    public void set(@NotNull String key, int value) {
        cache.put(key, value);
        database.execute("Не удалось сохранить значение " + key, () ->
                dao.createOrUpdate(new ValueRecord(key, value)));
    }
}
