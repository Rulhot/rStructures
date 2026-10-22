package ru.rulhot.rStructures;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rStructures.command.RStructuresCommand;
import ru.rulhot.rStructures.command.sub.AdminSubCommand;
import ru.rulhot.rStructures.command.sub.HelpSubCommand;
import ru.rulhot.rStructures.command.sub.GiveSubCommand;
import ru.rulhot.rStructures.command.sub.MenuSubCommand;
import ru.rulhot.rStructures.command.sub.ReloadSubCommand;
import ru.rulhot.rStructures.command.sub.SpawnSubCommand;
import ru.rulhot.rStructures.command.sub.UndoSubCommand;
import ru.rulhot.rStructures.database.DatabaseManager;
import ru.rulhot.rStructures.database.repository.DungeonRepository;
import ru.rulhot.rStructures.database.repository.LootRepository;
import ru.rulhot.rStructures.database.repository.ShulkerRepository;
import ru.rulhot.rStructures.database.repository.ValueRepository;
import ru.rulhot.rStructures.hook.CakeRegionsHook;
import ru.rulhot.rStructures.hook.DecentHologramHook;
import ru.rulhot.rStructures.hook.FancyHologramHook;
import ru.rulhot.rStructures.hook.GriefPreventionHook;
import ru.rulhot.rStructures.hook.HologramHook;
import ru.rulhot.rStructures.hook.ProtectionHook;
import ru.rulhot.rStructures.listener.ItemListener;
import ru.rulhot.rStructures.listener.KeyListener;
import ru.rulhot.rStructures.listener.MenuListener;
import ru.rulhot.rStructures.listener.ShulkerListener;
import ru.rulhot.rStructures.manager.ConfigManager;
import ru.rulhot.rStructures.menu.MenuManager;
import ru.rulhot.rStructures.model.Settings;
import ru.rulhot.rStructures.service.ChunkService;
import ru.rulhot.rStructures.service.DungeonService;
import ru.rulhot.rStructures.service.ItemService;
import ru.rulhot.rStructures.service.LootService;
import ru.rulhot.rStructures.service.SchematicService;
import ru.rulhot.rStructures.service.ShulkerService;
import ru.rulhot.rStructures.util.Metrics;
import ru.rulhot.rStructures.util.SchedulerUtil;
import ru.rulhot.rStructures.util.UpdateChecker;
import ru.rulhot.rStructures.util.logger.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class RStructures extends JavaPlugin {

    private static final @NotNull String SCHEMATIC_FOLDER = "schem";
    private static final @NotNull Permission ADMIN_PERMISSION =
            new Permission(RStructuresCommand.ADMIN_PERMISSION, PermissionDefault.OP);

    private static final int BSTATS_ID = 34369;

    private @Nullable Metrics metrics;
    private @Nullable SchedulerUtil scheduler;
    private @Nullable DatabaseManager databaseManager;
    private @Nullable DungeonService dungeonService;
    private @Nullable MenuManager menuManager;
    private @Nullable ShulkerService shulkerService;

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil scheduler = new SchedulerUtil(this);
        this.scheduler = scheduler;

        ConfigManager configManager = new ConfigManager(this);
        configManager.apply(configManager.readAll());
        Settings settings = configManager.settings();

        File schematics = new File(getDataFolder(), SCHEMATIC_FOLDER);
        if (!schematics.isDirectory() && !schematics.mkdirs()) {
            Logger.warn("Не удалось создать папку " + schematics.getPath());
        }

        DatabaseManager databaseManager = new DatabaseManager(getDataFolder());
        this.databaseManager = databaseManager;
        DungeonRepository dungeonRepository;
        ShulkerRepository shulkerRepository;
        LootRepository lootRepository;
        ValueRepository valueRepository;
        if (!databaseManager.connect(configManager.readDatabase())) {
            Logger.error("База данных недоступна, rStructures выключен. Проверьте database.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            dungeonRepository = new DungeonRepository(databaseManager);
            shulkerRepository = new ShulkerRepository(databaseManager);
            lootRepository = new LootRepository(databaseManager);
            valueRepository = new ValueRepository(databaseManager);
        } catch (SQLException exception) {
            Logger.error("Не удалось прочитать данные из базы, rStructures выключен", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ItemService itemService = new ItemService(this, configManager);
        itemService.reload(settings.items());
        itemService.startCompassTimer(scheduler);
        SchematicService schematicService = new SchematicService(this, configManager, scheduler);
        schematicService.reload();
        LootService lootService = new LootService(lootRepository, valueRepository);
        PluginManager pluginManager = getServer().getPluginManager();
        ShulkerService shulkerService = new ShulkerService(configManager, itemService, lootService,
                shulkerRepository, dungeonRepository, hookHolograms(pluginManager), scheduler);
        this.shulkerService = shulkerService;
        shulkerService.refreshHolograms();
        shulkerService.startTimers();
        DungeonService dungeonService = new DungeonService(scheduler, configManager, schematicService,
                shulkerService, lootService, dungeonRepository, new ChunkService(scheduler),
                hookProtection(pluginManager));
        this.dungeonService = dungeonService;
        dungeonService.startTimers();
        MenuManager menuManager = new MenuManager(scheduler, configManager, lootService, dungeonService);
        this.menuManager = menuManager;
        menuManager.startRefresh();

        if (pluginManager.getPermission(ADMIN_PERMISSION.getName()) == null) {
            pluginManager.addPermission(ADMIN_PERMISSION);
        }
        pluginManager.registerEvents(new ShulkerListener(shulkerService, dungeonService), this);
        pluginManager.registerEvents(new KeyListener(configManager, itemService, lootService), this);
        pluginManager.registerEvents(new ItemListener(configManager, itemService, dungeonService), this);
        pluginManager.registerEvents(new MenuListener(menuManager), this);

        Settings.Command label = settings.command();
        RStructuresCommand command = new RStructuresCommand(configManager, List.of(
                new HelpSubCommand(configManager),
                new MenuSubCommand(configManager, menuManager),
                new SpawnSubCommand(configManager, dungeonService),
                new UndoSubCommand(configManager, dungeonService),
                new GiveSubCommand(scheduler, configManager, itemService),
                new AdminSubCommand(configManager, shulkerService),
                new ReloadSubCommand(configManager, itemService, schematicService, shulkerService, scheduler)));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(label.name(), label.aliases(), command));

        if (settings.metrics() && BSTATS_ID > 0) {
            metrics = new Metrics(this, BSTATS_ID);
        }
        if (settings.updateCheck()) {
            new UpdateChecker(getPluginMeta().getVersion()).check();
        }
    }

    private @NotNull HologramHook hookHolograms(@NotNull PluginManager pluginManager) {
        try {
            if (pluginManager.isPluginEnabled("FancyHolograms")) {
                return new FancyHologramHook();
            }
            if (pluginManager.isPluginEnabled("DecentHolograms")) {
                return new DecentHologramHook();
            }
        } catch (LinkageError exception) {
            Logger.warn("Плагин голограмм несовместим, значки над шалкерами отключены: " + exception);
        }
        return HologramHook.NONE;
    }

    private @NotNull ProtectionHook hookProtection(@NotNull PluginManager pluginManager) {
        List<ProtectionHook> hooks = new ArrayList<>();
        if (pluginManager.isPluginEnabled("GriefPrevention")) {
            try {
                hooks.add(new GriefPreventionHook());
            } catch (Throwable throwable) {
                Logger.error("Не удалось подключить GriefPrevention, его приваты не проверяются", throwable);
            }
        }
        if (pluginManager.isPluginEnabled("CakeRegionsPlus")) {
            try {
                hooks.add(new CakeRegionsHook());
            } catch (Throwable throwable) {
                Logger.error("Не удалось подключить CakeRegionsPlus, его регионы не проверяются", throwable);
            }
        }
        return switch (hooks.size()) {
            case 0 -> ProtectionHook.NONE;
            case 1 -> hooks.getFirst();
            default -> (world, area) -> hooks.stream().anyMatch(hook -> hook.overlaps(world, area));
        };
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (menuManager != null) {
            menuManager.shutdown();
        }
        if (dungeonService != null) {
            dungeonService.shutdown();
        }
        if (shulkerService != null) {
            shulkerService.removeHolograms();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getServer().getPluginManager().removePermission(ADMIN_PERMISSION);
    }
}
