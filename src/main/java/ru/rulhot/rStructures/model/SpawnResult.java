package ru.rulhot.rStructures.model;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public sealed interface SpawnResult {

    record Success(@NotNull String regionId, @NotNull Location location, @NotNull String name) implements SpawnResult {
    }

    record LimitReached(int limit) implements SpawnResult {
    }

    record Unsuitable() implements SpawnResult {
    }

    record Cancelled() implements SpawnResult {
    }

    record Failed(@NotNull Reason reason) implements SpawnResult {
    }

    enum Reason {
        NO_TEMPLATES,
        REGIONS_UNAVAILABLE,
        BACKUP_FAILED,
        PASTE_FAILED
    }

    enum Undo {
        SUCCESS,
        NOT_FOUND,
        BUSY,
        WORLD_MISSING,
        RESTORE_FAILED;

        public @NotNull String messageKey() {
            return switch (this) {
                case SUCCESS -> "undo.success";
                case NOT_FOUND -> "undo.not-found";
                case BUSY -> "undo.busy";
                case WORLD_MISSING -> "undo.world-missing";
                case RESTORE_FAILED -> "undo.restore-failed";
            };
        }
    }
}
