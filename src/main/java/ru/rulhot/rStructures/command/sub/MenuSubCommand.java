package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.menu.MenuManager;

@RequiredArgsConstructor
public final class MenuSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull MenuManager menuManager;

    @Override
    public @NotNull String getName() {
        return "menu";
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
        if (!(sender instanceof Player player)) {
            config.send(sender, "player-only");
            return true;
        }
        menuManager.open(player);
        return true;
    }
}
