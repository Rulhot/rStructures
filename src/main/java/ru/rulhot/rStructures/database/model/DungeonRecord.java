package ru.rulhot.rStructures.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.model.Bounds;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rstructures_dungeons")
public final class DungeonRecord {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "region_id", canBeNull = false, unique = true)
    private @NotNull String regionId = "";

    @DatabaseField(columnName = "world_uuid", canBeNull = false)
    private @NotNull UUID worldUuid = new UUID(0L, 0L);

    @DatabaseField(columnName = "world_name", canBeNull = false)
    private @NotNull String worldName = "";

    @DatabaseField(columnName = "min_x")
    private int minX;

    @DatabaseField(columnName = "min_y")
    private int minY;

    @DatabaseField(columnName = "min_z")
    private int minZ;

    @DatabaseField(columnName = "max_x")
    private int maxX;

    @DatabaseField(columnName = "max_y")
    private int maxY;

    @DatabaseField(columnName = "max_z")
    private int maxZ;

    @DatabaseField(columnName = "schematic", canBeNull = false)
    private @NotNull String schematic = "";

    @DatabaseField(columnName = "template_id", canBeNull = false, defaultValue = "")
    private @NotNull String templateId = "";

    @DatabaseField(columnName = "created_at")
    private long createdAt;

    public DungeonRecord(
            @NotNull String regionId,
            @NotNull UUID worldUuid,
            @NotNull String worldName,
            @NotNull Bounds bounds,
            @NotNull String schematic,
            @NotNull String templateId
    ) {
        this.templateId = templateId;
        this.createdAt = System.currentTimeMillis();
        this.regionId = regionId;
        this.worldUuid = worldUuid;
        this.worldName = worldName;
        this.minX = bounds.minX();
        this.minY = bounds.minY();
        this.minZ = bounds.minZ();
        this.maxX = bounds.maxX();
        this.maxY = bounds.maxY();
        this.maxZ = bounds.maxZ();
        this.schematic = schematic;
    }

    public @NotNull Bounds bounds() {
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
