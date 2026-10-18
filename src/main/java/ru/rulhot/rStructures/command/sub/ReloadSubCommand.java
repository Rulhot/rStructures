package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.service.ItemService;
import ru.rulhot.rStructures.service.SchematicService;
import ru.rulhot.rStructures.service.ShulkerService;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

@RequiredArgsConstructor
public final class ReloadSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull ItemService itemService;
    private final @NotNull SchematicService schematicService;
    private final @NotNull ShulkerService shulkerService;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "reload";
    }

    @Override
    public @NotNull String getPermission() {
        return RStructuresCommand.ADMIN_PERMISSION;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            config.send(sender, "no-permission");
            return true;
        }
        scheduler.runAsync(() -> {
            ConfigManager.Bundle bundle = config.readAll();
            scheduler.runGlobal(() -> apply(sender, bundle));
        });
        return true;
    }

    private void apply(@NotNull CommandSender sender, @NotNull ConfigManager.Bundle bundle) {
        try {
            config.apply(bundle);
        } catch (RuntimeException exception) {
            Logger.error("Не удалось применить конфигурацию, оставлена прежняя", exception);
            config.send(sender, "reload.failed");
            return;
        }
        itemService.reload(config.settings().items());
        schematicService.reload();
        shulkerService.refreshHolograms();
        config.send(sender, "reload.done");
    }
}
