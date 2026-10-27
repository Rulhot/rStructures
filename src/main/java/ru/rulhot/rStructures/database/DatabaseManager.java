package ru.rulhot.rStructures.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.logger.Level;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.database.model.LootItemRecord;
import ru.rulhot.rStructures.database.model.ShulkerRecord;
import ru.rulhot.rStructures.database.model.TerrainRecord;
import ru.rulhot.rStructures.database.model.ValueRecord;
import ru.rulhot.rStructures.model.DatabaseSettings;
import ru.rulhot.rStructures.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class DatabaseManager {

    private static final @NotNull List<Class<?>> TABLES = List.of(
            DungeonRecord.class,
            TerrainRecord.class,
            ShulkerRecord.class,
            LootItemRecord.class,
            ValueRecord.class
    );
    private static final @NotNull String SQLITE_FILE = "rstructures.db";
    private static final @NotNull String SQLITE_PARAMS = "?journal_mode=DELETE&synchronous=FULL&busy_timeout=5000";
    private static final @NotNull String MYSQL_PARAMS =
            "&allowPublicKeyRetrieval=true&characterEncoding=utf8&rewriteBatchedStatements=true";
    private static final @NotNull List<String> SHULKER_COLUMNS = List.of(
            "open_type VARCHAR(16) NOT NULL DEFAULT 'key'",
            "open_seconds BIGINT NOT NULL DEFAULT 0",
            "open_at BIGINT NOT NULL DEFAULT 0");
    private static final @NotNull String DUNGEON_TEMPLATE_COLUMN = "template_id VARCHAR(64) NOT NULL DEFAULT ''";
    private static final @NotNull String LOOT_POOL_COLUMN = "pool VARCHAR(64) NOT NULL DEFAULT 'default'";
    private static final @NotNull String DUNGEON_CREATED_COLUMN = "created_at BIGINT NOT NULL DEFAULT 0";
    private static final @NotNull String DUNGEON_READY_COLUMN = "ready BOOLEAN NOT NULL DEFAULT 1";
    private static final int SQLITE_POOL_SIZE = 2;
    private static final int MYSQL_POOL_SIZE = 4;
    private static final long CONNECTION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10L);
    private static final long IDLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10L);
    private static final long MAX_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(30L);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final @NotNull File dataFolder;
    private final @NotNull ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rStructures-Database");
        thread.setDaemon(true);
        return thread;
    });
    private volatile @Nullable HikariDataSource dataSource;
    private volatile @Nullable ConnectionSource connectionSource;

    public boolean connect(@NotNull DatabaseSettings settings) {
        try {
            com.j256.ormlite.logger.Logger.setGlobalLogLevel(Level.WARNING);
            HikariDataSource source = new HikariDataSource(createConfig(settings));
            dataSource = source;
            ConnectionSource connection = new DataSourceConnectionSource(source, source.getJdbcUrl());
            connectionSource = connection;
            for (Class<?> table : TABLES) {
                TableUtils.createTableIfNotExists(connection, table);
            }
            Dao<ShulkerRecord, Integer> shulkers = DaoManager.createDao(connection, ShulkerRecord.class);
            for (String column : SHULKER_COLUMNS) {
                addColumn(shulkers, "rstructures_shulkers", column);
            }
            addColumn(shulkers, "rstructures_dungeons", DUNGEON_TEMPLATE_COLUMN);
            addColumn(shulkers, "rstructures_dungeons", DUNGEON_CREATED_COLUMN);
            addColumn(shulkers, "rstructures_dungeons", DUNGEON_READY_COLUMN);
            addColumn(shulkers, "rstructures_loot", LOOT_POOL_COLUMN);
            return true;
        } catch (SQLException | IOException | RuntimeException exception) {
            Logger.error("Не удалось подключиться к базе данных", exception);
            close();
            return false;
        }
    }

    private void addColumn(
            @NotNull Dao<ShulkerRecord, Integer> dao,
            @NotNull String table,
            @NotNull String definition
    ) throws SQLException {
        try {
            dao.executeRawNoArgs("ALTER TABLE " + table + " ADD COLUMN " + definition);
        } catch (SQLException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("duplicate column")) {
                    return;
                }
            }
            throw exception;
        }
    }

    public <T, I> @NotNull Dao<T, I> dao(@NotNull Class<T> type) throws SQLException {
        ConnectionSource connection = connectionSource;
        if (connection == null) {
            throw new SQLException("База данных не подключена");
        }
        return DaoManager.createDao(connection, type);
    }

    public void transaction(@NotNull String failureMessage, @NotNull SqlTask task) {
        transaction(failureMessage, task, ignored -> {
        });
    }

    public void transaction(@NotNull String failureMessage, @NotNull SqlTask task, @NotNull Consumer<Boolean> callback) {
        boolean queued = submit(failureMessage, () -> {
            boolean saved = false;
            try {
                ConnectionSource connection = connectionSource;
                if (connection == null) {
                    throw new SQLException("База данных не подключена");
                }
                TransactionManager.callInTransaction(connection, () -> {
                    task.run();
                    return null;
                });
                saved = true;
            } catch (SQLException | RuntimeException exception) {
                Logger.error(failureMessage, exception);
            }
            callback.accept(saved);
        });
        if (!queued) {
            callback.accept(false);
        }
    }

    public void execute(@NotNull String failureMessage, @NotNull SqlTask task) {
        submit(failureMessage, () -> {
            try {
                task.run();
            } catch (SQLException | RuntimeException exception) {
                Logger.error(failureMessage, exception);
            }
        });
    }

    private boolean submit(@NotNull String failureMessage, @NotNull Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            Logger.error(failureMessage + ": база данных уже закрыта");
            return false;
        }
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Logger.warn("Очередь записи в базу не успела завершиться за " + SHUTDOWN_TIMEOUT_SECONDS + " с");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ConnectionSource connection = connectionSource;
        connectionSource = null;
        if (connection != null) {
            connection.closeQuietly();
        }

        HikariDataSource source = dataSource;
        dataSource = null;
        if (source != null) {
            source.close();
        }
    }

    private @NotNull HikariConfig createConfig(@NotNull DatabaseSettings settings) throws IOException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("rStructures-Pool");
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        config.setIdleTimeout(IDLE_TIMEOUT_MILLIS);
        config.setMaxLifetime(MAX_LIFETIME_MILLIS);
        config.setMinimumIdle(1);

        if (settings.type() == DatabaseSettings.Type.MYSQL) {
            DatabaseSettings.MySql mysql = settings.mysql();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + mysql.host() + ':' + mysql.port() + '/'
                    + mysql.database() + "?useSSL=" + mysql.ssl() + MYSQL_PARAMS);
            config.setUsername(mysql.username());
            config.setPassword(mysql.password());
            config.setMaximumPoolSize(MYSQL_POOL_SIZE);
            return config;
        }

        Path folder = dataFolder.toPath().resolve("data");
        Files.createDirectories(folder);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve(SQLITE_FILE).toAbsolutePath() + SQLITE_PARAMS);
        config.setMaximumPoolSize(SQLITE_POOL_SIZE);
        return config;
    }

    @FunctionalInterface
    public interface SqlTask {

        void run() throws SQLException;
    }
}
