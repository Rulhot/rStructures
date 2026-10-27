package ru.rulhot.rStructures.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.model.CustomItem;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.util.MessageUtil;
import ru.rulhot.rStructures.util.SchedulerUtil;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ItemService {

    private static final long MILLIS_ROUNDING = 999L;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long MINUTE_SECONDS = 60L;
    private static final @NotNull String COOLDOWN_PLACEHOLDER = "cooldown";
    private final @NotNull NamespacedKey itemKey;
    private final @NotNull NamespacedKey legacyKey;
    private final @NotNull NamespacedKey cooldownKey;
    private final @NotNull NamespacedKey targetKey;
    private final @NotNull NamespacedKey shownKey;
    private final @NotNull ConfigManager configManager;
    private volatile @NotNull Map<CustomItem, ItemStack> templates = Map.of();
    private volatile @Nullable SchedulerUtil.Task compassTask;

    public ItemService(@NotNull Plugin plugin, @NotNull ConfigManager configManager) {
        this.configManager = configManager;
        this.itemKey = new NamespacedKey(plugin, "item");
        this.legacyKey = new NamespacedKey(plugin, "key");
        this.cooldownKey = new NamespacedKey(plugin, "compass_ready_at");
        this.targetKey = new NamespacedKey(plugin, "compass_target");
        this.shownKey = new NamespacedKey(plugin, "compass_shown");
    }

    public void reload(@NotNull Settings.Items items) {
        Map<CustomItem, ItemStack> built = new EnumMap<>(CustomItem.class);
        for (CustomItem type : CustomItem.values()) {
            built.put(type, build(type, items.of(type), type == CustomItem.PICKAXE ? items.pickaxeDurability() : 0));
        }
        templates = Map.copyOf(built);
    }

    public @NotNull ItemStack create(@NotNull CustomItem type, int amount) {
        ItemStack item = templates.get(type).clone();
        item.setAmount(amount);
        return item;
    }

    public @Nullable CustomItem type(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id != null) {
            return CustomItem.from(id);
        }
        return item.getPersistentDataContainer().has(legacyKey) ? CustomItem.KEY : null;
    }

    public boolean is(@Nullable ItemStack item, @NotNull CustomItem type) {
        return type(item) == type;
    }

    public long compassCooldownLeft(@NotNull ItemStack compass) {
        long readyAt = compass.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(readyAt - System.currentTimeMillis() + MILLIS_ROUNDING));
    }

    public @Nullable String lastCompassTarget(@NotNull ItemStack compass) {
        return compass.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
    }

    public void markCompassUsed(@NotNull ItemStack compass, @NotNull String regionId, long cooldownSeconds) {
        long readyAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.max(0L, cooldownSeconds));
        compass.editMeta(meta -> {
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, regionId);
            meta.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, readyAt);
        });
        refreshCompass(compass);
    }

    public void refreshCompass(@Nullable ItemStack item) {
        if (!is(item, CustomItem.COMPASS)) {
            return;
        }
        long exactSeconds = compassCooldownLeft(item);
        long secondsLeft = exactSeconds < MINUTE_SECONDS
                ? exactSeconds
                : (exactSeconds + MINUTE_SECONDS - 1L) / MINUTE_SECONDS * MINUTE_SECONDS;
        String text = secondsLeft > 0L ? configManager.formatTime(secondsLeft) : configManager.raw("compass.ready");
        if (text.equals(item.getPersistentDataContainer().get(shownKey, PersistentDataType.STRING))) {
            return;
        }
        Map<String, String> values = Map.of(COOLDOWN_PLACEHOLDER, text);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(shownKey, PersistentDataType.STRING, text);
            meta.lore(configManager.settings().items().compass().lore().stream()
                    .map(line -> MessageUtil.parseItemText(line, values))
                    .toList());
        });
    }

    public void startCompassTimer(@NotNull SchedulerUtil scheduler) {
        SchedulerUtil.Task previous = compassTask;
        if (previous != null) {
            previous.cancel();
        }
        compassTask = scheduler.runGlobalTimer(TICKS_PER_SECOND, () -> {
            Material material = configManager.settings().items().compass().material();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runFor(player, () -> {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == material) {
                            refreshCompass(item);
                        }
                    }
                });
            }
        });
    }

    private @NotNull ItemStack build(@NotNull CustomItem type, @NotNull Settings.Item config, int durability) {
        ItemStack item = new ItemStack(config.material());
        item.editMeta(meta -> {
            meta.customName(MessageUtil.parseItemText(config.name(), Map.of()));
            meta.lore(config.lore().stream().map(line -> MessageUtil.parseItemText(line, Map.of())).toList());
            meta.addItemFlags(config.flags().toArray(ItemFlag[]::new));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.id());
            if (durability > 0 && meta instanceof Damageable damageable) {
                damageable.setMaxDamage(durability);
            }
            if (config.glint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (config.model() > 0) {
                CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
                modelData.setFloats(List.of((float) config.model()));
                meta.setCustomModelDataComponent(modelData);
            }
            if (!config.texture().isEmpty() && meta instanceof SkullMeta skull) {
                UUID profileId = UUID.nameUUIDFromBytes(config.texture().getBytes(StandardCharsets.UTF_8));
                PlayerProfile profile = Bukkit.createProfile(profileId);
                profile.setProperty(new ProfileProperty("textures", config.texture()));
                skull.setPlayerProfile(profile);
            }
        });
        return item;
    }
}
