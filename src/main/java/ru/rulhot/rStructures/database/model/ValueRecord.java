package ru.rulhot.rStructures.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rstructures_values")
public final class ValueRecord {

    @DatabaseField(columnName = "value_key", id = true)
    private @NotNull String key = "";

    @DatabaseField(columnName = "value_number")
    private int value;

    public ValueRecord(@NotNull String key, int value) {
        this.key = key;
        this.value = value;
    }
}
