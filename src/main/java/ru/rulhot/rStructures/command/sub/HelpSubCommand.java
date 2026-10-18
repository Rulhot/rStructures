package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;

@RequiredArgsConstructor
public final class HelpSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;

    @Override
    public @NotNull String getName() {
        return "help";
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
        config.send(sender, "help");
        return true;
    }
}
