package ru.rulhot.rStructures.menu;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.database.model.DungeonRecord;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.menu.MenuHolder.View;
import ru.rulhot.rStructures.model.DungeonTemplate;
import ru.rulhot.rStructures.model.LootCategory;
import ru.rulhot.rStructures.service.DungeonService;
import ru.rulhot.rStructures.service.LootService;
import ru.rulhot.rStructures.service.ShulkerService;
import ru.rulhot.rStructures.util.MessageUtil;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class MenuManager {

    private static final int MAIN_SIZE = 36;
    private static final int KEYS_SLOT = 11;
    private static final int LOOT_SLOT = 12;
    private static final int PLAIN_SLOT = 13;
    private static final int DUNGEONS_SLOT = 14;
    private static final int TYPES_SLOT = 15;
    private static final int SHOWN_BIOMES = 3;
    private static final int LIST_SIZE = 45;
    private static final int CONTENT_SLOTS = 36;
    private static final int INFO_SLOT = 38;
    private static final int PREVIOUS_SLOT = 39;
    private static final int BACK_SLOT = 40;
    private static final int NEXT_SLOT = 41;
    private static final long REFRESH_PERIOD_TICKS = 20L;
    private static final int SMALL_STEP = 1;
    private static final int BIG_STEP = 10;
    private static final @NotNull String ACCENT = "<#FEF06A>";
    private static final @NotNull String KEYS_COLOR = "<#8FB8DE>";
    private static final @NotNull String LOOT_COLOR = "<#D9A26B>";
    private static final @NotNull String DUNGEONS_COLOR = "<#8CC77E>";
    private static final @NotNull String KEYS_TITLE = "Где выпадает ключ";
    private static final @NotNull String LOOT_TITLE = "Лут шалкеров";
    private static final @NotNull String PLAIN_TITLE = "Лут обычных блоков";
    private static final @NotNull String DUNGEONS_TITLE = "Данжи";
    private static final @NotNull String TYPES_COLOR = "<#C79BE0>";
    private static final @NotNull String TYPES_TITLE = "Типы данжей";
    private static final @NotNull String OPEN_HINT = "Нажми, чтобы открыть.";
    private static final @NotNull String HINT_SEPARATOR = " <dark_gray>»</dark_gray> <white>";
    private static final @NotNull String SMALL_HINT = "ЛКМ / ПКМ" + HINT_SEPARATOR + "шанс ±" + SMALL_STEP + "%";
    private static final @NotNull String BIG_HINT = "Shift + клик" + HINT_SEPARATOR + "шанс ±" + BIG_STEP + "%";

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull LootService lootService;
    private final @NotNull DungeonService dungeonService;
    private volatile @Nullable SchedulerUtil.Task refreshTask;

    public void open(@NotNull Player player) {
        open(player, View.MAIN);
    }

    public void handleClick(@NotNull InventoryClickEvent event, @NotNull MenuHolder holder) {
        int slot = event.getRawSlot();
        int size = holder.getInventory().getSize();
        View view = holder.getView();
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission(RStructuresCommand.ADMIN_PERMISSION)) {
            event.setCancelled(true);
            return;
        }
        if (isLoot(view) && (slot < 0 || slot >= size)) {
            if (slot >= size && event.isShiftClick()) {
                event.setCancelled(true);
                addLoot(player, holder, event.getCurrentItem());
            } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (slot < 0 || slot >= size || !holder.acceptClick()) {
            return;
        }
        Sound clickSound = config.settings().sounds().menuClick();
        if (clickSound != null && event.getCurrentItem() != null) {
            player.playSound(clickSound);
        }
        if (view == View.MAIN) {
            clickMain(player, slot);
            return;
        }
        if (slot == BACK_SLOT) {
            open(player, View.MAIN);
            return;
        }
        if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) {
            holder.setPage(holder.getPage() + (slot == NEXT_SLOT ? 1 : -1));
            render(holder);
            return;
        }
        if (slot >= CONTENT_SLOTS) {
            return;
        }

        int index = holder.getPage() * CONTENT_SLOTS + slot;
        ClickType click = event.getClick();
        switch (view) {
            case LOOT, PLAIN -> clickLoot(player, holder, index, click, event.getCursor());
            case KEYS -> clickKey(holder, index, click);
            case DUNGEONS -> clickDungeon(player, holder, index, click);
            case TYPES -> clickType(player, holder, index, click);
            case MAIN -> {
            }
        }
    }

    public void handleDrag(@NotNull InventoryDragEvent event, @NotNull MenuHolder holder) {
        int size = holder.getInventory().getSize();
        if (event.getRawSlots().stream().noneMatch(slot -> slot < size)) {
            return;
        }
        event.setCancelled(true);
        if (!isLoot(holder.getView())
                || !(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission(RStructuresCommand.ADMIN_PERMISSION)) {
            return;
        }
        addLoot(player, holder, event.getOldCursor());
    }

    public void startRefresh() {
        refreshTask = scheduler.runGlobalTimer(REFRESH_PERIOD_TICKS, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runFor(player, () -> refresh(player));
            }
        });
    }

    private void refresh(@NotNull Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        View view = holder.getView();
        if (view == View.MAIN || view == View.DUNGEONS || view == View.TYPES) {
            render(holder);
        }
    }

    public void shutdown() {
        SchedulerUtil.Task task = refreshTask;
        refreshTask = null;
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    private void open(@NotNull Player player, @NotNull View view) {
        MenuHolder holder = new MenuHolder(view, view == View.MAIN ? MAIN_SIZE : LIST_SIZE,
                text("<dark_gray>• " + switch (view) {
                    case MAIN -> "rStructures";
                    case LOOT -> LOOT_TITLE;
                    case PLAIN -> PLAIN_TITLE;
                    case DUNGEONS -> DUNGEONS_TITLE;
                    case KEYS -> KEYS_TITLE;
                    case TYPES -> TYPES_TITLE;
                }));
        render(holder);
        scheduler.runFor(player, () -> player.openInventory(holder.getInventory()));
    }

    private void clickMain(@NotNull Player player, int slot) {
        switch (slot) {
            case KEYS_SLOT -> open(player, View.KEYS);
            case LOOT_SLOT -> open(player, View.LOOT);
            case PLAIN_SLOT -> open(player, View.PLAIN);
            case DUNGEONS_SLOT -> open(player, View.DUNGEONS);
            case TYPES_SLOT -> open(player, View.TYPES);
            default -> {
            }
        }
    }

    private void clickLoot(
            @NotNull Player player,
            @NotNull MenuHolder holder,
            int index,
            @NotNull ClickType click,
            @NotNull ItemStack cursor
    ) {
        if (!cursor.isEmpty()) {
            if (click != ClickType.DOUBLE_CLICK) {
                addLoot(player, holder, cursor);
            }
            return;
        }
        String pool = pool(holder.getView());
        if (index >= lootService.poolSize(pool)) {
            return;
        }
        int step = step(click);
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            lootService.remove(pool, index);
        } else if (step != 0) {
            lootService.adjustChance(pool, index, step);
        } else {
            return;
        }
        render(holder);
        refreshLoot(holder);
    }

    private void clickKey(@NotNull MenuHolder holder, int index, @NotNull ClickType click) {
        LootCategory[] categories = LootCategory.values();
        int step = step(click);
        if (index < categories.length && step != 0) {
            lootService.adjustKeyChance(categories[index], step);
            render(holder);
        }
    }

    private void clickDungeon(@NotNull Player player, @NotNull MenuHolder holder, int index, @NotNull ClickType click) {
        List<DungeonRecord> dungeons = dungeonService.dungeons();
        if (index >= dungeons.size()) {
            return;
        }
        DungeonRecord record = dungeons.get(index);
        if (click == ClickType.LEFT) {
            World world = Bukkit.getWorld(record.getWorldUuid());
            if (world != null) {
                player.teleportAsync(record.bounds().center(world));
            }
            return;
        }
        String id = record.getRegionId();
        if (click == ClickType.RIGHT) {
            config.send(player, dungeonService.refill(record) ? "admin.dungeon-looted" : "admin.loot-empty",
                    Map.of("id", id));
            return;
        }
        if (click != ClickType.SHIFT_RIGHT) {
            return;
        }
        dungeonService.undo(id, result -> {
            config.send(player, result.messageKey(), Map.of("id", id));
            scheduler.runFor(player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) == holder) {
                    render(holder);
                }
            });
        });
    }

    private void addLoot(@NotNull Player player, @NotNull MenuHolder holder, @Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        lootService.add(pool(holder.getView()), item);
        holder.setPage(Integer.MAX_VALUE);
        render(holder);
        refreshLoot(holder);
        config.send(player, "loot.saved");
    }

    private void refreshLoot(@NotNull MenuHolder source) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            scheduler.runFor(online, () -> {
                if (online.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder other
                        && other != source && other.getView() == source.getView()) {
                    render(other);
                }
            });
        }
    }

    private @NotNull String templateName(@NotNull DungeonRecord record) {
        DungeonTemplate template = config.settings().templateOf(record.getTemplateId(), record.getSchematic());
        return template == null ? "<gray>удалён из structures/" : template.name();
    }

    private boolean isLoot(@NotNull View view) {
        return view == View.LOOT || view == View.PLAIN;
    }

    private @NotNull String pool(@NotNull View view) {
        return view == View.PLAIN ? LootService.PLAIN_POOL : LootService.STORAGE_POOL;
    }

    private int step(@NotNull ClickType click) {
        return switch (click) {
            case LEFT -> SMALL_STEP;
            case RIGHT -> -SMALL_STEP;
            case SHIFT_LEFT -> BIG_STEP;
            case SHIFT_RIGHT -> -BIG_STEP;
            default -> 0;
        };
    }

    private void render(@NotNull MenuHolder holder) {
        Inventory inventory = holder.getInventory();
        switch (holder.getView()) {
            case MAIN -> {
                inventory.setItem(KEYS_SLOT, icon(Material.LODESTONE, KEYS_TITLE, describe(KEYS_COLOR, "Ключ",
                        List.of("шанс найти его в луте", "ванильных структур."), List.of(), List.of(OPEN_HINT))));
                inventory.setItem(LOOT_SLOT, icon(Material.BARREL, LOOT_TITLE, describe(LOOT_COLOR, "Лут",
                        List.of("предметы, которые появляются", "в шалкерах данжа."),
                        List.of("Сейчас предметов: " + ACCENT + lootService.poolSize(LootService.STORAGE_POOL)),
                        List.of(OPEN_HINT))));
                inventory.setItem(PLAIN_SLOT, icon(Material.CHEST, PLAIN_TITLE, describe(LOOT_COLOR, "Лут",
                        List.of("предметы для обычных блоков", "из схемы: бочек, сундуков и т.д.",
                                "Блоки задаются в файле данжа:", "functions.loot-containers"),
                        List.of("Сейчас предметов: " + ACCENT + lootService.poolSize(LootService.PLAIN_POOL)),
                        List.of(OPEN_HINT))));
                inventory.setItem(DUNGEONS_SLOT, icon(Material.VAULT, DUNGEONS_TITLE, describe(DUNGEONS_COLOR, "Данжи",
                        List.of("все заспавненные структуры."),
                        List.of("Сейчас данжей: " + ACCENT + dungeonService.dungeons().size()),
                        List.of(OPEN_HINT))));
                inventory.setItem(TYPES_SLOT, icon(Material.STRUCTURE_BLOCK, TYPES_TITLE, describe(TYPES_COLOR, "Типы",
                        List.of("данжи из папки structures,", "можно заспавнить нужный."),
                        List.of("Сейчас типов: " + ACCENT + config.settings().templates().size()),
                        List.of(OPEN_HINT))));
            }
            case LOOT, PLAIN -> {
                String pool = pool(holder.getView());
                boolean plain = holder.getView() == View.PLAIN;
                int offset = clampPage(holder, lootService.poolSize(pool));
                renderList(holder,
                        lootService.entries(pool, offset, CONTENT_SLOTS).stream().map(this::lootIcon).toList(),
                        icon(Material.OAK_HANGING_SIGN, plain ? PLAIN_TITLE : LOOT_TITLE, describe(LOOT_COLOR, "Лут",
                                List.of("положи предмет в это меню,",
                                        plain ? "чтобы он появлялся в обычных блоках." : "чтобы он появлялся в шалкерах."),
                                List.of(SMALL_HINT, BIG_HINT, "Q" + HINT_SEPARATOR + "убрать из лута"), List.of())));
            }
            case KEYS -> {
                LootCategory[] categories = LootCategory.values();
                int offset = clampPage(holder, categories.length);
                List<ItemStack> icons = new ArrayList<>();
                for (int index = offset; index < Math.min(categories.length, offset + CONTENT_SLOTS); index++) {
                    icons.add(keyIcon(categories[index]));
                }
                renderList(holder, icons, icon(Material.OAK_HANGING_SIGN, KEYS_TITLE, describe(KEYS_COLOR, "Ключ",
                        List.of("шанс найти его в луте", "ванильной структуры."),
                        List.of(SMALL_HINT, BIG_HINT), List.of())));
            }
            case TYPES -> {
                List<DungeonTemplate> templates = config.settings().templates();
                int offset = clampPage(holder, templates.size());
                renderList(holder, templates.stream().skip(offset).limit(CONTENT_SLOTS).map(this::typeIcon).toList(),
                        icon(Material.OAK_HANGING_SIGN, TYPES_TITLE, describe(TYPES_COLOR, "Типы",
                                List.of("данжи из папки structures."),
                                List.of("ЛКМ" + HINT_SEPARATOR + "заспавнить данж этого типа"), List.of())));
            }
            case DUNGEONS -> {
                List<DungeonRecord> dungeons = dungeonService.dungeons();
                int offset = clampPage(holder, dungeons.size());
                renderList(holder, dungeons.stream().skip(offset).limit(CONTENT_SLOTS).map(this::dungeonIcon).toList(),
                        icon(Material.OAK_HANGING_SIGN, DUNGEONS_TITLE, describe(DUNGEONS_COLOR, "Данжи",
                                List.of("все заспавненные структуры."),
                                List.of("ЛКМ" + HINT_SEPARATOR + "телепорт",
                                        "ПКМ" + HINT_SEPARATOR + "заполнить шалкеры лутом",
                                        "Shift + ПКМ" + HINT_SEPARATOR + "удалить данж"), List.of())));
            }
        }
    }

    private int clampPage(@NotNull MenuHolder holder, int total) {
        int page = Math.clamp(holder.getPage(), 0, Math.max(0, (total - 1) / CONTENT_SLOTS));
        holder.setPage(page);
        return page * CONTENT_SLOTS;
    }

    private void renderList(@NotNull MenuHolder holder, @NotNull List<ItemStack> icons, @NotNull ItemStack info) {
        Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setItem(slot, slot < icons.size() ? icons.get(slot) : null);
        }
        List<String> page = List.of("<gray>Страница " + (holder.getPage() + 1));
        inventory.setItem(INFO_SLOT, info);
        inventory.setItem(PREVIOUS_SLOT, icon(Material.ARROW, "← Предыдущая страница", page));
        inventory.setItem(BACK_SLOT, icon(Material.BRUSH, "Вернуться в меню", List.of()));
        inventory.setItem(NEXT_SLOT, icon(Material.ARROW, "Следующая страница →", page));
    }

    private @NotNull ItemStack lootIcon(@NotNull LootService.Entry entry) {
        ItemStack icon = entry.item() == null ? new ItemStack(Material.BARRIER) : entry.item().clone();
        icon.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            List<Component> present = meta.lore();
            if (present != null) {
                lore.addAll(present);
            }
            describe(LOOT_COLOR, "", List.of(), List.of("Шанс: " + ACCENT + entry.chance() + "%"), List.of())
                    .forEach(line -> lore.add(text(line)));
            meta.lore(lore);
        });
        return icon;
    }

    private @NotNull ItemStack keyIcon(@NotNull LootCategory category) {
        int chance = lootService.keyChance(category);
        return icon(chance > 0 ? Material.CHEST : Material.ENDER_CHEST, category.title(),
                describe(KEYS_COLOR, "", List.of(), List.of("Шанс ключа: " + ACCENT + chance + "%"), List.of()));
    }

    private void clickType(@NotNull Player player, @NotNull MenuHolder holder, int index, @NotNull ClickType click) {
        List<DungeonTemplate> templates = config.settings().templates();
        if (index >= templates.size() || click != ClickType.LEFT) {
            return;
        }
        DungeonTemplate template = templates.get(index);
        if (!dungeonService.isReady(template)) {
            return;
        }
        World world = Bukkit.getWorld(template.world());
        if (world == null) {
            config.send(player, "spawn.world-not-found", Map.of("world", template.world()));
            return;
        }
        if (template.limit() > 0 && dungeonService.count(template) >= template.limit()) {
            config.send(player, "spawn.limit-reached", Map.of("count", template.limit()));
            return;
        }
        if (!dungeonService.beginSpawn()) {
            config.send(player, "spawn.busy");
            return;
        }

        config.send(player, "spawn.started", Map.of("name", MessageUtil.parseText(template.name())));
        dungeonService.spawn(world, template.id(), result -> {
            dungeonService.endSpawn();
            dungeonService.report(player, world, result);
            scheduler.runFor(player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) == holder) {
                    render(holder);
                }
            });
        });
    }

    private @NotNull ItemStack typeIcon(@NotNull DungeonTemplate template) {
        boolean ready = dungeonService.isReady(template);
        List<String> bars = new ArrayList<>(List.of(
                "Файл: " + ACCENT + template.id() + ".yml",
                "Схема: " + (ready ? ACCENT + template.file() : "<red>" + template.file() + " не найдена"),
                "Мир: " + ACCENT + template.world(),
                "Размещение: " + ACCENT + switch (template.placement()) {
                    case GROUND -> "на земле";
                    case WATER -> "на воде";
                    case AIR -> "в воздухе";
                },
                "Биомы: " + ACCENT + biomeNames(template),
                "Открытие: " + ACCENT + openTypeName(template.openType().id()),
                "Стоит: " + ACCENT + dungeonService.count(template)
                        + (template.limit() > 0 ? " из " + template.limit() : ", без лимита")));
        ItemStack icon = new ItemStack(ready ? worldMaterial(Bukkit.getWorld(template.world())) : Material.BARRIER);
        icon.editMeta(meta -> {
            meta.customName(text(template.name()));
            meta.lore(describe(TYPES_COLOR, "", List.of(), bars,
                    ready ? List.of("ЛКМ" + HINT_SEPARATOR + "заспавнить этот данж") : List.of())
                    .stream().map(this::text).toList());
        });
        return icon;
    }

    private @NotNull String biomeNames(@NotNull DungeonTemplate template) {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        List<String> names = new ArrayList<>();
        for (Biome biome : template.biomes()) {
            NamespacedKey key = registry.getKey(biome);
            if (key != null) {
                names.add(key.getKey());
            }
        }
        names.sort(String::compareTo);
        if (names.size() <= SHOWN_BIOMES) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, SHOWN_BIOMES)) + " и ещё " + (names.size() - SHOWN_BIOMES);
    }

    private @NotNull Material worldMaterial(@Nullable World world) {
        if (world == null) {
            return Material.BARRIER;
        }
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private @NotNull ItemStack dungeonIcon(@NotNull DungeonRecord record) {
        ItemStack icon = new ItemStack(worldMaterial(Bukkit.getWorld(record.getWorldUuid())));
        ShulkerService.Progress shulkers = dungeonService.shulkers(record);
        int undoSeconds = dungeonService.autoUndoSecondsLeft(record);
        long lifetimeSeconds = dungeonService.lifetimeSecondsLeft(record);
        Map<String, String> values = Map.of(
                "id", record.getRegionId(),
                "schematic", record.getSchematic(),
                "world", record.getWorldName());
        icon.editMeta(meta -> {
            meta.customName(MessageUtil.parseItemText("<white>Данж <id>", values));
            meta.lore(describe(DUNGEONS_COLOR, "", List.of(), List.of(
                    "Тип: " + templateName(record),
                    "Схема: " + ACCENT + "<schematic>",
                    "Мир: " + ACCENT + "<world>",
                    "Открытие: " + ACCENT + openTypeName(dungeonService.openType(record)),
                    "Шалкеры: " + ACCENT + shulkers.opened() + " из " + shulkers.total() + " открыто",
                    "Удаление: " + ACCENT + (undoSeconds < 0 ? "не запущено" : "через " + config.formatTime(undoSeconds)),
                    "Срок жизни: " + ACCENT + (lifetimeSeconds < 0 ? "не ограничен" : "ещё " + config.formatTime(lifetimeSeconds)),
                    "Координаты: " + ACCENT + record.getMinX() + " " + record.getMinY() + " " + record.getMinZ()),
                    List.of()).stream().map(line -> MessageUtil.parseItemText(line, values)).toList());
        });
        return icon;
    }

    private @NotNull String openTypeName(@Nullable String openType) {
        if (openType == null) {
            return "нет хранилищ";
        }
        return switch (openType) {
            case "pickaxe" -> "кирка взломщика";
            case "timer" -> "отсчёт после активации";
            default -> "ключ";
        };
    }

    private @NotNull List<String> describe(
            @NotNull String color,
            @NotNull String keyword,
            @NotNull List<String> intro,
            @NotNull List<String> bars,
            @NotNull List<String> hints
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (int index = 0; index < intro.size(); index++) {
            lore.add(index == 0 ? " " + color + keyword + ": <white>" + intro.get(index) : " <white>" + intro.get(index));
        }
        if (!intro.isEmpty() && !bars.isEmpty()) {
            lore.add("");
        }
        for (int index = 0; index < bars.size(); index++) {
            String bar = bars.size() == 1 ? "▪" : index < bars.size() - 1 ? "<u>▌</u>" : "▌";
            lore.add(" " + color + bar + " <white>" + bars.get(index));
        }
        lore.add("");
        if (hints.isEmpty()) {
            return lore;
        }
        hints.forEach(hint -> lore.add(" <white>" + hint));
        lore.add("");
        return lore;
    }

    private @NotNull ItemStack icon(@NotNull Material material, @NotNull String name, @NotNull List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.customName(text("<white>" + name));
            meta.lore(lore.stream().map(this::text).toList());
        });
        return item;
    }

    private @NotNull Component text(@NotNull String raw) {
        return MessageUtil.parseItemText(raw, Map.of());
    }
}
