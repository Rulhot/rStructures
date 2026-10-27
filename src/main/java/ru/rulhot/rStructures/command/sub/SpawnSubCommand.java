package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.DungeonTemplate;
import ru.rulhot.rStructures.service.DungeonService;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class SpawnSubCommand implements SubCommand {

    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 20;
    private static final @NotNull String CANCEL = "cancel";

    private final @NotNull ConfigManager config;
    private final @NotNull DungeonService dungeonService;

    @Override
    public @NotNull String getName() {
        return "spawn";
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
        if (args.length == 1 && CANCEL.equalsIgnoreCase(args[0])) {
            if (!dungeonService.isSpawning()) {
                config.send(sender, "spawn.not-running");
                return true;
            }
            dungeonService.cancelSpawn();
            return true;
        }
        if (args.length > 3) {
            config.send(sender, "spawn.usage");
            return true;
        }

        int amount = MIN_AMOUNT;
        if (args.length >= 1) {
            Integer parsed = SubCommand.parseInt(args[0], MIN_AMOUNT, MAX_AMOUNT);
            if (parsed == null) {
                config.send(sender, "spawn.invalid-amount", Map.of("min", MIN_AMOUNT, "max", MAX_AMOUNT));
                return true;
            }
            amount = parsed;
        }

        World world = resolveWorld(sender, args.length >= 2 ? args[1] : null);
        if (world == null) {
            return true;
        }
        String templateId = null;
        if (args.length == 3) {
            DungeonTemplate template = findTemplate(world, args[2]);
            if (template == null) {
                config.send(sender, "spawn.unknown-type", Map.of("type", args[2], "world", world.getName()));
                return true;
            }
            if (template.limit() > 0 && dungeonService.count(template) >= template.limit()) {
                config.send(sender, "spawn.limit-reached", Map.of("count", template.limit()));
                return true;
            }
            templateId = template.id();
        }
        if (!dungeonService.beginSpawn()) {
            config.send(sender, "spawn.busy");
            return true;
        }
        spawnNext(sender, world, templateId, amount);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> SubCommand.suggest(Stream.of("1", "2", "3", "5", "10", CANCEL), args);
            case 2 -> SubCommand.suggest(config.settings().dungeonWorlds().stream(), args);
            case 3 -> SubCommand.suggest(config.settings().templates().stream()
                    .filter(template -> template.world().equalsIgnoreCase(args[1]))
                    .map(DungeonTemplate::id), args);
            default -> List.of();
        };
    }

    private @Nullable DungeonTemplate findTemplate(@NotNull World world, @NotNull String id) {
        return config.settings().templates().stream()
                .filter(template -> template.id().equalsIgnoreCase(id)
                        && template.world().equalsIgnoreCase(world.getName()))
                .findFirst()
                .orElse(null);
    }

    private @Nullable World resolveWorld(@NotNull CommandSender sender, @Nullable String name) {
        if (name == null) {
            if (sender instanceof Player player) {
                return player.getWorld();
            }
            config.send(sender, "spawn.usage");
            return null;
        }
        if (!config.settings().isDungeonWorld(name)) {
            config.send(sender, "spawn.no-dungeon-world", Map.of("world", name));
            return null;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            config.send(sender, "spawn.world-not-found", Map.of("world", name));
        }
        return world;
    }

    private void spawnNext(
            @NotNull CommandSender sender,
            @NotNull World world,
            @Nullable String templateId,
            int remaining
    ) {
        if (remaining <= 0) {
            dungeonService.endSpawn();
            return;
        }
        dungeonService.spawn(world, templateId, result -> {
            boolean proceed = dungeonService.report(sender, world, result);
            spawnNext(sender, world, templateId, proceed ? remaining - 1 : 0);
        });
    }
}
