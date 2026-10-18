package ru.rulhot.rStructures.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.function.Consumer;

@RequiredArgsConstructor
public final class SchedulerUtil {

    private final @NotNull Plugin plugin;

    public void runAsync(@NotNull Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, guarded(task));
    }

    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, guarded(task));
    }

    public void runGlobalLater(long delayTicks, @NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, guarded(task), Math.max(1L, delayTicks));
    }

    public @NotNull Task runGlobalTimer(long periodTicks, @NotNull Runnable task) {
        long period = Math.max(1L, periodTicks);
        return new Task(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, guarded(task), period, period));
    }

    public void runAt(@NotNull Location location, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, guarded(task));
    }

    public void runAtLater(@NotNull Location location, long delayTicks, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, guarded(task), Math.max(1L, delayTicks));
    }

    public void runFor(@NotNull Entity entity, @NotNull Runnable task) {
        entity.getScheduler().run(plugin, guarded(task), null);
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static @NotNull Consumer<ScheduledTask> guarded(@NotNull Runnable task) {
        return scheduled -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                Logger.error("Ошибка в запланированной задаче", exception);
            }
        };
    }

    public record Task(@NotNull ScheduledTask handle) {

        public void cancel() {
            handle.cancel();
        }
    }
}
