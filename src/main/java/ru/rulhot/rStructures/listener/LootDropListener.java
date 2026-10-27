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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
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
public final class LootDropListener implements Listener {

    private static final double FINDER_RADIUS = 64D;
    private static final @NotNull String FOUND_SUFFIX = ".found";
    private static final int SINGLE_SLOT = 1;

    private final @NotNull ConfigManager configManager;
    private final @NotNull ItemService itemService;
    private final @NotNull LootService lootService;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        LootCategory category = LootCategory.match(event.getLootTable().getKey());
        if (category == null) {
            return;
        }
        List<CustomItem> dropped = lootService.rollDrops(category);
        if (dropped.isEmpty()) {
            return;
        }
        InventoryHolder holder = event.getInventoryHolder();
        if (holder != null && holder.getInventory().getSize() > SINGLE_SLOT) {
            dropped.forEach(type -> event.getLoot().add(create(type)));
        } else {
            Location location = event.getLootContext().getLocation();
            dropped.forEach(type -> location.getWorld().dropItemNaturally(location, create(type)));
        }

        Player finder = event.getEntity() instanceof Player player ? player : null;
        if (finder == null && event.getInventoryHolder() instanceof BlockState state) {
            finder = nearestPlayer(state.getLocation());
        }
        announce(finder, dropped);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultDispense(@NotNull BlockDispenseLootEvent event) {
        if (event.getBlock().getType() != Material.VAULT) {
            return;
        }
        List<CustomItem> dropped = lootService.rollDrops(LootCategory.VAULT);
        if (dropped.isEmpty()) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        dropped.forEach(type -> loot.add(create(type)));
        event.setDispensedLoot(loot);
        announce(event.getPlayer(), dropped);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSnifferDig(@NotNull EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Sniffer sniffer)) {
            return;
        }
        Sniffer.State state = sniffer.getState();
        if (state != Sniffer.State.DIGGING && state != Sniffer.State.RISING) {
            return;
        }
        List<CustomItem> dropped = lootService.rollDrops(LootCategory.SNIFFER);
        if (dropped.isEmpty()) {
            return;
        }

        Location location = event.getItemDrop().getLocation();
        dropped.forEach(type -> location.getWorld().dropItemNaturally(location, create(type)));
        announce(nearestPlayer(location), dropped);
    }

    private @NotNull ItemStack create(@NotNull CustomItem type) {
        ItemStack item = itemService.create(type, 1);
        itemService.refreshCompass(item);
        return item;
    }

    private void announce(@Nullable Player finder, @NotNull List<CustomItem> dropped) {
        if (finder == null) {
            return;
        }
        for (CustomItem type : dropped) {
            String key = type.id() + FOUND_SUFFIX;
            if (configManager.raw(key).isEmpty()) {
                continue;
            }
            Component message = configManager.message(key, Map.of("player", finder.getName()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
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
