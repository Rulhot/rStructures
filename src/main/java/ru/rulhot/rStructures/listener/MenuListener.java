package ru.rulhot.rStructures.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.menu.MenuHolder;
import ru.rulhot.rStructures.menu.MenuManager;

@RequiredArgsConstructor
public final class MenuListener implements Listener {

    private final @NotNull MenuManager menuManager;

    @EventHandler(ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleClick(event, holder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleDrag(event, holder);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof MenuHolder
                && event.getPlayer() instanceof Player player) {
            menuManager.resync(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(@NotNull InventoryCreativeEvent event) {
        if (menuManager.isIcon(event.getCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                menuManager.resync(player);
            }
        }
    }
}
