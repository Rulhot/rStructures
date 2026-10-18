package ru.rulhot.rStructures.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.SubCommand;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.CustomItem;
import ru.rulhot.rStructures.service.ItemService;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class GiveSubCommand implements SubCommand {

    private static final int MAX_AMOUNT = 2304;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull ItemService itemService;

    @Override
    public @NotNull String getName() {
        return "give";
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
        CustomItem type = args.length >= 1 && args.length <= 3 ? CustomItem.from(args[0]) : null;
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player self ? self : null;
        Integer amount = args.length == 3 ? SubCommand.parseInt(args[2], 1, MAX_AMOUNT) : Integer.valueOf(1);
        if (type == null || target == null || amount == null) {
            config.send(sender, "give.usage");
            return true;
        }

        scheduler.runFor(target, () -> give(target, type, amount));
        config.send(sender, "give.given", Map.of("item", type.id(), "player", target.getName(), "amount", amount));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> SubCommand.suggest(Arrays.stream(CustomItem.values()).map(CustomItem::id), args);
            case 2 -> SubCommand.suggest(Bukkit.getOnlinePlayers().stream().map(Player::getName), args);
            case 3 -> SubCommand.suggest(Stream.of("1", "8", "16", "32", "64"), args);
            default -> List.of();
        };
    }

    private void give(@NotNull Player target, @NotNull CustomItem type, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemService.create(type, 1);
            int size = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(size);
            itemService.refreshCompass(stack);
            remaining -= size;
            for (ItemStack leftover : target.getInventory().addItem(stack).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }
    }
}
