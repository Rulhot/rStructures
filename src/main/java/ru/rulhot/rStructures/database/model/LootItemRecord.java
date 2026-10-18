package ru.rulhot.rStructures.database.model;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rstructures_loot")
public final class LootItemRecord {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "chance")
    private int chance;

    @DatabaseField(columnName = "item_data", dataType = DataType.BYTE_ARRAY, columnDefinition = "LONGBLOB")
    private byte @NotNull [] itemData = new byte[0];

    @DatabaseField(columnName = "pool", canBeNull = false, defaultValue = "default")
    private @NotNull String pool = "default";

    public LootItemRecord(@NotNull String pool, int chance, byte @NotNull [] itemData) {
        this.pool = pool;
        this.chance = chance;
        this.itemData = itemData;
    }
}
