package ru.rulhot.rStructures.menu;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MenuHolder implements InventoryHolder {

    private static final long CLICK_COOLDOWN_MILLIS = 150L;

    @Getter
    private final @NotNull View view;
    private final @NotNull Inventory inventory;
    @Getter
    @Setter
    private int page;
    private long lastClickAt;

    public MenuHolder(@NotNull View view, int size, @NotNull Component title) {
        this.view = view;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public boolean acceptClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickAt < CLICK_COOLDOWN_MILLIS) {
            return false;
        }
        lastClickAt = now;
        return true;
    }

    public enum View {
        MAIN,
        LOOT,
        DUNGEONS,
        KEYS,
        TYPES,
        PLAIN
    }
}
