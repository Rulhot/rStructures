package ru.rulhot.rStructures.listener;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.CustomItem;
import ru.rulhot.rStructures.model.LootCategory;
import ru.rulhot.rStructures.service.ItemService;
import ru.rulhot.rStructures.service.LootService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class KeyListener implements Listener {

    private static final double FINDER_RADIUS = 64D;

    private final @NotNull ConfigManager configManager;
    private final @NotNull ItemService itemService;
    private final @NotNull LootService lootService;

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        LootCategory category = LootCategory.match(event.getLootTable().getKey());
        if (category == null || !lootService.rollKey(category)) {
            return;
        }
        event.getLoot().add(itemService.create(CustomItem.KEY, 1));

        Player finder = event.getEntity() instanceof Player player ? player : null;
        if (finder == null && event.getInventoryHolder() instanceof BlockState state) {
            finder = nearestPlayer(state.getLocation());
        }
        announce(finder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultDispense(@NotNull BlockDispenseLootEvent event) {
        if (event.getBlock().getType() != Material.VAULT || !lootService.rollKey(LootCategory.VAULT)) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        loot.add(itemService.create(CustomItem.KEY, 1));
        event.setDispensedLoot(loot);
        announce(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSnifferDig(@NotNull EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Sniffer sniffer)) {
            return;
        }
        Sniffer.State state = sniffer.getState();
        if (state != Sniffer.State.DIGGING && state != Sniffer.State.RISING) {
            return;
        }
        if (!lootService.rollKey(LootCategory.SNIFFER)) {
            return;
        }

        Location location = event.getItemDrop().getLocation();
        location.getWorld().dropItemNaturally(location, itemService.create(CustomItem.KEY, 1));
        announce(nearestPlayer(location));
    }

    private void announce(@Nullable Player finder) {
        if (finder == null) {
            return;
        }
        Component message = configManager.message("key.found", Map.of("player", finder.getName()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    private @Nullable Player nearestPlayer(@NotNull Location origin) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : origin.getNearbyPlayers(FINDER_RADIUS)) {
            double distance = player.getLocation().distanceSquared(origin);
            if (distance < best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }
}
