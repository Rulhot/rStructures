package ru.rulhot.rStructures.listener;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.CustomItem;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.service.DungeonService;
import ru.rulhot.rStructures.service.ItemService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class ItemListener implements Listener {

    private static final char HIDDEN_DIGIT = '?';
    private static final long REPEAT_CLICK_MILLIS = 100L;
    private static final @NotNull Set<Material> WORKSTATIONS = Set.of(
            Material.CRAFTING_TABLE, Material.STONECUTTER, Material.LOOM, Material.CARTOGRAPHY_TABLE,
            Material.SMITHING_TABLE, Material.GRINDSTONE, Material.NOTE_BLOCK);

    private final @NotNull ConfigManager configManager;
    private final @NotNull ItemService itemService;
    private final @NotNull DungeonService dungeonService;
    private final @NotNull Map<UUID, Long> clickedAt = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onCompassUse(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!itemService.is(item, CustomItem.COMPASS)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && isInteractive(clicked)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && itemService.is(player.getInventory().getItemInMainHand(), CustomItem.COMPASS)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = clickedAt.put(player.getUniqueId(), now);
        if (previous != null && now - previous < REPEAT_CLICK_MILLIS) {
            return;
        }

        long secondsLeft = itemService.compassCooldownLeft(item);
        if (secondsLeft > 0L) {
            itemService.refreshCompass(item);
            configManager.send(player, "compass.cooldown", Map.of("time", configManager.formatTime(secondsLeft)));
            return;
        }

        Settings.Compass compass = configManager.settings().items().compassUse();
        DungeonRecord dungeon = dungeonService.compassTarget(player, compass.nearest(),
                itemService.lastCompassTarget(item));
        if (dungeon == null) {
            configManager.send(player, "compass.none");
            return;
        }

        Location target = dungeon.bounds().center(player.getWorld());
        if (compass.pointer()) {
            player.setCompassTarget(target);
        }
        configManager.send(player, "compass.target", Map.of(
                "x", coordinate(target.getBlockX(), compass),
                "y", coordinate(target.getBlockY(), compass),
                "z", coordinate(target.getBlockZ(), compass)));
        Sound sound = configManager.settings().sounds().compass();
        if (sound != null) {
            player.playSound(sound);
        }
        if (compass.consume()) {
            item.subtract(1);
            return;
        }
        if (item.getAmount() == 1) {
            itemService.markCompassUsed(item, dungeon.getRegionId(), compass.cooldownSeconds());
            return;
        }

        ItemStack used = item.asOne();
        item.subtract(1);
        itemService.markCompassUsed(used, dungeon.getRegionId(), compass.cooldownSeconds());
        for (ItemStack leftover : player.getInventory().addItem(used).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(@NotNull PlayerItemHeldEvent event) {
        itemService.refreshCompass(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (itemService.type(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            CustomItem type = itemService.type(ingredient);
            if (type != null && configManager.settings().items().of(type).denyCraft()) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareResult(@NotNull PrepareResultEvent event) {
        Inventory inventory = event.getInventory();
        int resultSlot = inventory.getSize() - 1;
        for (int slot = 0; slot < resultSlot; slot++) {
            CustomItem type = itemService.type(inventory.getItem(slot));
            if (type != null && configManager.settings().items().of(type).denyCraft()) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareEnchant(@NotNull PrepareItemEnchantEvent event) {
        CustomItem type = itemService.type(event.getItem());
        if (type != null && configManager.settings().items().of(type).denyCraft()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        Item dropped = event.getEntity();
        CustomItem type = itemService.type(dropped.getItemStack());
        if (type != null && configManager.settings().items().of(type).glow()) {
            dropped.setGlowing(true);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        clickedAt.remove(event.getPlayer().getUniqueId());
        Settings.Items settings = configManager.settings().items();
        if (!settings.key().dropOnQuit() && !settings.compass().dropOnQuit() && !settings.pickaxe().dropOnQuit()) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            CustomItem type = itemService.type(item);
            if (type == null || !settings.of(type).dropOnQuit()) {
                continue;
            }
            Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), item);
            dropped.setGlowing(settings.of(type).glow());
            inventory.setItem(slot, null);
        }
    }

    private boolean isInteractive(@NotNull Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Openable
                || data instanceof Switch
                || WORKSTATIONS.contains(block.getType())
                || Tag.ANVIL.isTagged(block.getType())
                || block.getState(false) instanceof TileState;
    }

    private @NotNull String coordinate(int value, @NotNull Settings.Compass compass) {
        String text = String.valueOf(value);
        if (!compass.encrypt()) {
            return text;
        }
        char[] symbols = text.toCharArray();
        int firstDigit = value < 0 ? 1 : 0;
        for (int index = Math.max(firstDigit, symbols.length - compass.encryptDigits()); index < symbols.length; index++) {
            symbols[index] = HIDDEN_DIGIT;
        }
        return new String(symbols);
    }
}
