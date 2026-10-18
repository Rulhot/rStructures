package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.service.DungeonService;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class UndoSubCommand implements SubCommand {

    private static final int MAX_ID_LENGTH = 64;

    private final @NotNull ConfigManager config;
    private final @NotNull DungeonService dungeonService;

    @Override
    public @NotNull String getName() {
        return "undo";
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
        if (args.length != 1 || args[0].length() > MAX_ID_LENGTH) {
            config.send(sender, "undo.usage");
            return true;
        }
        String id = args[0];
        dungeonService.undo(id, result -> config.send(sender, result.messageKey(), Map.of("id", id)));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return SubCommand.suggest(dungeonService.dungeons().stream().map(DungeonRecord::getRegionId), args);
    }
}
