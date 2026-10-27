package ru.rulhot.rStructures.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.model.Bounds.BlockPos;
import ru.rulhot.rStructures.model.DungeonTemplate.OpenType;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rstructures_shulkers")
public final class ShulkerRecord {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "world_uuid", canBeNull = false)
    private @NotNull UUID worldUuid = new UUID(0L, 0L);

    @DatabaseField(columnName = "x")
    private int x;

    @DatabaseField(columnName = "y")
    private int y;

    @DatabaseField(columnName = "z")
    private int z;

    @DatabaseField(columnName = "opened")
    private volatile boolean opened;

    @DatabaseField(columnName = "region_id")
    private @Nullable String regionId;

    @DatabaseField(columnName = "open_type", canBeNull = false, defaultValue = "key")
    private @NotNull String openType = OpenType.KEY.id();

    @DatabaseField(columnName = "open_seconds")
    private long openSeconds;

    @DatabaseField(columnName = "open_at")
    private volatile long openAt;

    public ShulkerRecord(
            @NotNull BlockPos pos,
            @Nullable String regionId,
            @NotNull OpenType openType,
            long openSeconds
    ) {
        this.openType = openType.id();
        this.openSeconds = openSeconds;
        this.worldUuid = pos.world();
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();
        this.regionId = regionId;
    }

    public boolean opensWith(@NotNull OpenType type) {
        return type.id().equals(openType);
    }

    public @NotNull BlockPos pos() {
        return new BlockPos(worldUuid, x, y, z);
    }
}
