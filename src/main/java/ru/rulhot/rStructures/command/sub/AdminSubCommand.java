package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.service.ShulkerService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class AdminSubCommand implements SubCommand {

    private static final int TARGET_DISTANCE = 6;
    private static final @NotNull String TEST = "test";
    private static final @NotNull String REMOVE = "remove";
    private static final @NotNull String LOOT = "loot";

    private final @NotNull ConfigManager config;
    private final @NotNull ShulkerService shulkerService;

    @Override
    public @NotNull String getName() {
        return "admin";
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
        if (args.length != 1) {
            config.send(sender, "admin.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case TEST -> test(sender);
            case REMOVE -> remove(sender);
            case LOOT -> loot(sender);
            default -> config.send(sender, "admin.usage");
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return SubCommand.suggest(Stream.of(TEST, REMOVE, LOOT), args);
    }

    private void test(@NotNull CommandSender sender) {
        Block target = targetShulker(sender);
        if (target == null) {
            return;
        }
        config.send(sender, shulkerService.register(target)
                ? "admin.shulker-registered"
                : "admin.shulker-already");
    }

    private void remove(@NotNull CommandSender sender) {
        Block target = targetShulker(sender);
        if (target == null) {
            return;
        }
        config.send(sender, shulkerService.unregister(target)
                ? "admin.shulker-removed"
                : "admin.shulker-not-managed");
    }

    private void loot(@NotNull CommandSender sender) {
        Block target = targetShulker(sender);
        if (target == null) {
            return;
        }
        config.send(sender, shulkerService.refill(target) ? "admin.shulker-looted" : "admin.loot-empty");
    }

    private @Nullable Block targetShulker(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            config.send(sender, "player-only");
            return null;
        }
        Block target = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null || !shulkerService.isShulker(target)) {
            config.send(sender, "admin.look-at-shulker");
            return null;
        }
        return target;
    }
}
