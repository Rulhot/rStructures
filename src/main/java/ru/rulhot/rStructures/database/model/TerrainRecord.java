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
@DatabaseTable(tableName = "rstructures_terrain")
public final class TerrainRecord {

    @DatabaseField(columnName = "region_id", id = true)
    private @NotNull String regionId = "";

    @DatabaseField(columnName = "terrain_data", dataType = DataType.BYTE_ARRAY, columnDefinition = "LONGBLOB")
    private byte @NotNull [] data = new byte[0];

    public TerrainRecord(@NotNull String regionId, byte @NotNull [] data) {
        this.regionId = regionId;
        this.data = data;
    }
}
