package ru.rulhot.rStructures.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.database.model.ShulkerRecord;
import ru.rulhot.rStructures.service.DungeonService;
import ru.rulhot.rStructures.service.ShulkerService;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class ShulkerListener implements Listener {

    private final @NotNull ShulkerService shulkerService;
    private final @NotNull DungeonService dungeonService;

    @EventHandler(ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        ShulkerRecord record = shulkerService.find(block);
        if (record == null || record.isOpened()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!shulkerService.open(player, block, record)) {
            event.setCancelled(true);
            return;
        }
        if (player.isSneaking() && block.getState(false) instanceof Container container) {
            event.setCancelled(true);
            player.openInventory(container.getInventory());
        }
        dungeonService.checkAutoUndo(block);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        shulkerService.forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        for (Block block : blocks(event.getInventory())) {
            dungeonService.checkAutoUndo(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(@NotNull InventoryMoveItemEvent event) {
        if (isManaged(event.getSource()) || isManaged(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        ShulkerRecord record = shulkerService.find(block);
        if (record == null) {
            return;
        }
        event.setCancelled(true);
        if (shulkerService.hit(event.getPlayer(), block, record)) {
            dungeonService.checkAutoUndo(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        if (shulkerService.find(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(@NotNull BlockBurnEvent event) {
        if (shulkerService.find(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        if (containsManaged(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        if (containsManaged(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        event.blockList().removeIf(block -> shulkerService.find(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        event.blockList().removeIf(block -> shulkerService.find(block) != null);
    }

    private boolean isManaged(@NotNull Inventory inventory) {
        for (Block block : blocks(inventory)) {
            if (shulkerService.find(block) != null) {
                return true;
            }
        }
        return false;
    }

    private @NotNull List<Block> blocks(@NotNull Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof Container container) {
            return shulkerService.isContainerType(container.getType()) ? List.of(container.getBlock()) : List.of();
        }
        if (!(holder instanceof DoubleChest chest)) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>();
        if (chest.getLeftSide(false) instanceof Container left && shulkerService.isContainerType(left.getType())) {
            blocks.add(left.getBlock());
        }
        if (chest.getRightSide(false) instanceof Container right && shulkerService.isContainerType(right.getType())) {
            blocks.add(right.getBlock());
        }
        return blocks;
    }

    private boolean containsManaged(@NotNull List<Block> blocks) {
        for (Block block : blocks) {
            if (shulkerService.find(block) != null) {
                return true;
            }
        }
        return false;
    }
}
