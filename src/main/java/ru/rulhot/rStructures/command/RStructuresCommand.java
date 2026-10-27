package ru.rulhot.rStructures.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.manager.ConfigManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class RStructuresCommand implements BasicCommand {

    public static final String ADMIN_PERMISSION = "rstructures.admin";

    private final @NotNull ConfigManager config;
    private final @NotNull List<SubCommand> subCommands;

    public RStructuresCommand(@NotNull ConfigManager config, @NotNull List<SubCommand> subCommands) {
        this.config = config;
        this.subCommands = List.copyOf(subCommands);
    }

    @Override
    public @NotNull String permission() {
        return ADMIN_PERMISSION;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            config.send(sender, "no-permission");
            return;
        }
        if (args.length == 0) {
            config.send(sender, "help");
            return;
        }

        SubCommand subCommand = find(args[0]);
        if (subCommand == null) {
            config.send(sender, "unknown");
            return;
        }
        subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            return SubCommand.suggest(subCommands.stream()
                    .filter(command -> sender.hasPermission(command.getPermission()))
                    .map(SubCommand::getName), args);
        }

        SubCommand subCommand = find(args[0]);
        if (subCommand == null || !sender.hasPermission(subCommand.getPermission())) {
            return List.of();
        }
        return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private @Nullable SubCommand find(@NotNull String name) {
        return subCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
