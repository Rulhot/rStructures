package ru.rulhot.rStructures.model;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum LootCategory {
    VILLAGE("village", "Деревня", "chests/village/"),
    MINESHAFT("mineshaft", "Заброшенная шахта", "chests/abandoned_mineshaft"),
    DESERT_PYRAMID("desert-pyramid", "Пустынный храм", "chests/desert_pyramid", "archaeology/desert_pyramid"),
    JUNGLE_TEMPLE("jungle-temple", "Храм джунглей", "chests/jungle_temple"),
    IGLOO("igloo", "Иглу", "chests/igloo_chest"),
    UNDERWATER_RUIN("underwater-ruin", "Подводные руины", "chests/underwater_ruin", "archaeology/ocean_ruin"),
    SHIPWRECK("shipwreck", "Затонувший корабль", "chests/shipwreck"),
    STRONGHOLD("stronghold", "Крепость", "chests/stronghold"),
    NETHER_FORTRESS("nether-fortress", "Адская крепость", "chests/nether_bridge"),
    BASTION("bastion", "Бастион", "chests/bastion"),
    RUINED_PORTAL("ruined-portal", "Разрушенный портал", "chests/ruined_portal"),
    END_CITY("end-city", "Город Края", "chests/end_city_treasure"),
    ANCIENT_CITY("ancient-city", "Древний город", "chests/ancient_city"),
    PILLAGER_OUTPOST("pillager-outpost", "Аванпост разбойников", "chests/pillager_outpost"),
    WOODLAND_MANSION("woodland-mansion", "Лесной особняк", "chests/woodland_mansion"),
    BURIED_TREASURE("buried-treasure", "Зарытый клад", "chests/buried_treasure"),
    TRIAL_CHAMBERS("trial-chambers", "Испытательные камеры", "chests/trial_chambers/", "pots/trial_chambers/"),
    TRAIL_RUINS("trail-ruins", "Руины тропы", "archaeology/trail_ruins"),
    VAULT("vault", "Хранилище испытаний"),
    SNIFFER("sniffer", "Нюхач");

    private final @NotNull String id;
    private final @NotNull String title;
    private final @NotNull String @NotNull [] pathPrefixes;

    LootCategory(@NotNull String id, @NotNull String title, @NotNull String @NotNull ... pathPrefixes) {
        this.id = id;
        this.title = title;
        this.pathPrefixes = pathPrefixes;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String title() {
        return title;
    }

    public static @Nullable LootCategory match(@NotNull NamespacedKey lootTable) {
        String path = lootTable.getKey();
        LootCategory best = null;
        int bestLength = -1;
        for (LootCategory category : values()) {
            for (String prefix : category.pathPrefixes) {
                if (path.startsWith(prefix) && prefix.length() > bestLength) {
                    best = category;
                    bestLength = prefix.length();
                }
            }
        }
        return best;
    }
}
