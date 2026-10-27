package ru.rulhot.rStructures.model;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.Locale;

public record DatabaseSettings(@NotNull Type type, @NotNull MySql mysql) {

    public static @NotNull DatabaseSettings from(@NotNull ConfigurationSection root) {
        return new DatabaseSettings(
                type(root.getString("storage", "sqlite")),
                new MySql(
                        root.getString("mysql.host", "localhost"),
                        root.getInt("mysql.port", 3306),
                        root.getString("mysql.database", "rstructures"),
                        root.getString("mysql.user", "root"),
                        root.getString("mysql.password", ""),
                        root.getBoolean("mysql.ssl", false)));
    }

    private static @NotNull Type type(@NotNull String raw) {
        try {
            return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn("database.yml storage: неизвестное значение '" + raw + "', используется SQLITE");
            return Type.SQLITE;
        }
    }

    public enum Type {
        SQLITE,
        MYSQL
    }

    public record MySql(
            @NotNull String host,
            int port,
            @NotNull String database,
            @NotNull String username,
            @NotNull String password,
            boolean ssl
    ) {

        @Override
        public @NotNull String toString() {
            return "MySql[host=" + host + ", port=" + port + ", database=" + database + "]";
        }
    }
}
