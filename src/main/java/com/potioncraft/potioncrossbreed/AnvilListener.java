package com.potioncraft.potioncrossbreed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

public class AnvilListener implements Listener {

    private final PotionCrossbreed plugin;
    private final NamespacedKey CROSSBREED_KEY;
    private final NamespacedKey POTION_LEVEL_KEY;

    // Максимальный уровень зелья
    private static final int MAX_LEVEL = 3;
    // Стоимость в уровнях опыта
    private static final int EXP_COST = 5;

    public AnvilListener(PotionCrossbreed plugin) {
        this.plugin = plugin;
        this.CROSSBREED_KEY = new NamespacedKey(plugin, "crossbreed");
        this.POTION_LEVEL_KEY = new NamespacedKey(plugin, "potion_level");
    }

    /**
     * Проверяет, является ли предмет зельем (обычное, взрывное, туманное)
     */
    private boolean isPotion(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    /**
     * Получает все эффекты зелья (базовые + кастомные)
     */
    private List<PotionEffect> getAllEffects(PotionMeta meta) {
        List<PotionEffect> effects = new ArrayList<>();

        // Базовые эффекты из типа зелья
        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            effects.addAll(baseType.getPotionEffects());
        }

        // Кастомные эффекты (добавленные вручную)
        if (meta.hasCustomEffects()) {
            effects.addAll(meta.getCustomEffects());
        }

        return effects;
    }

    /**
     * Получает уровень зелья из PDC, или 1 по умолчанию
     */
    private int getPotionLevel(PotionMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(POTION_LEVEL_KEY, PersistentDataType.INTEGER)) {
            return pdc.get(POTION_LEVEL_KEY, PersistentDataType.INTEGER);
        }
        return 1;
    }

    /**
     * Получает набор типов эффектов зелья для сравнения
     */
    private Set<PotionEffectType> getEffectTypes(List<PotionEffect> effects) {
        Set<PotionEffectType> types = new HashSet<>();
        for (PotionEffect effect : effects) {
            types.add(effect.getType());
        }
        return types;
    }

    /**
     * Событие подготовки наковальни - показ результата
     */
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);

        // Оба слота должны содержать зелья
        if (!isPotion(first) || !isPotion(second)) {
            return;
        }

        PotionMeta firstMeta = (PotionMeta) first.getItemMeta();
        PotionMeta secondMeta = (PotionMeta) second.getItemMeta();

        if (firstMeta == null || secondMeta == null) return;

        int firstLevel = getPotionLevel(firstMeta);
        int secondLevel = getPotionLevel(secondMeta);

        // Уровни должны совпадать для скрещивания
        if (firstLevel != secondLevel) {
            return;
        }

        // Нельзя превысить максимальный уровень
        int newLevel = firstLevel + 1;
        if (newLevel > MAX_LEVEL) {
            return;
        }

        // Собираем эффекты обоих зелий
        List<PotionEffect> firstEffects = getAllEffects(firstMeta);
        List<PotionEffect> secondEffects = getAllEffects(secondMeta);

        if (firstEffects.isEmpty() && secondEffects.isEmpty()) return;

        // Определяем, одинаковые ли зелья (по типам эффектов)
        Set<PotionEffectType> firstTypes = getEffectTypes(firstEffects);
        Set<PotionEffectType> secondTypes = getEffectTypes(secondEffects);
        boolean isCrossbreed = !firstTypes.equals(secondTypes);

        // Создаём результат
        ItemStack result = new ItemStack(first.getType());
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();

        // Убираем базовый тип, чтобы использовать только кастомные эффекты
        resultMeta.setBasePotionType(null);

        // Объединяем эффекты: если одинаковый тип — берём с бОльшим amplifier, увеличиваем на 1
        Map<PotionEffectType, PotionEffect> mergedEffects = new LinkedHashMap<>();

        for (PotionEffect effect : firstEffects) {
            mergedEffects.put(effect.getType(), effect);
        }

        for (PotionEffect effect : secondEffects) {
            if (mergedEffects.containsKey(effect.getType())) {
                PotionEffect existing = mergedEffects.get(effect.getType());
                // При скрещивании одинаковых эффектов — увеличиваем amplifier на 1
                int newAmplifier = Math.max(existing.getAmplifier(), effect.getAmplifier()) + 1;
                int newDuration = Math.max(existing.getDuration(), effect.getDuration());
                mergedEffects.put(effect.getType(), new PotionEffect(
                        effect.getType(),
                        newDuration,
                        newAmplifier,
                        existing.isAmbient(),
                        existing.hasParticles(),
                        existing.hasIcon()
                ));
            } else {
                // Разные эффекты — просто добавляем
                mergedEffects.put(effect.getType(), effect);
            }
        }

        // Добавляем все объединённые эффекты
        for (PotionEffect effect : mergedEffects.values()) {
            resultMeta.addCustomEffect(effect, true);
        }

        // Записываем уровень зелья в PDC
        resultMeta.getPersistentDataContainer().set(POTION_LEVEL_KEY, PersistentDataType.INTEGER, newLevel);

        // Устанавливаем название
        if (isCrossbreed) {
            resultMeta.getPersistentDataContainer().set(CROSSBREED_KEY, PersistentDataType.BOOLEAN, true);
            resultMeta.displayName(
                    Component.text("Скрещенное зелье", NamedTextColor.LIGHT_PURPLE)
                            .decoration(TextDecoration.ITALIC, false)
            );
        } else {
            // Одинаковые зелья — добавляем уровень к названию
            String baseName = getPotionBaseName(firstMeta, firstEffects);
            resultMeta.displayName(
                    Component.text(baseName + " " + toRoman(newLevel), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }

        // Добавляем лор с информацией
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Уровень: " + toRoman(newLevel), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (isCrossbreed) {
            lore.add(Component.text("Скрещенное", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        resultMeta.lore(lore);

        result.setItemMeta(resultMeta);

        // Устанавливаем результат и стоимость
        inv.setRepairCost(EXP_COST);
        event.setResult(result);
    }

    /**
     * Обработка клика по результату наковальни — забрать опыт
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvilInv)) return;
        if (event.getRawSlot() != 2) return; // Слот результата

        ItemStack result = anvilInv.getItem(2);
        if (result == null || !isPotion(result)) return;

        PotionMeta meta = (PotionMeta) result.getItemMeta();
        if (meta == null) return;

        // Проверяем, что это наш результат (есть PDC ключ уровня)
        if (!meta.getPersistentDataContainer().has(POTION_LEVEL_KEY, PersistentDataType.INTEGER)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Проверяем уровень опыта
        if (player.getLevel() < EXP_COST) {
            event.setCancelled(true);
            player.sendMessage(
                    Component.text("✖ Недостаточно опыта! Нужно ", NamedTextColor.RED)
                            .append(Component.text(EXP_COST + " уровней", NamedTextColor.GOLD))
                            .append(Component.text(".", NamedTextColor.RED))
            );
            return;
        }

        // Забираем опыт
        player.setLevel(player.getLevel() - EXP_COST);
        player.sendMessage(
                Component.text("✔ Зелья успешно скрещены! ", NamedTextColor.GREEN)
                        .append(Component.text("-" + EXP_COST + " уровней", NamedTextColor.GOLD))
        );
    }

    /**
     * Получает красивое имя зелья на основе эффектов
     */
    private String getPotionBaseName(PotionMeta meta, List<PotionEffect> effects) {
        // Пробуем получить имя из базового типа
        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            return formatPotionTypeName(baseType);
        }

        // Если базового типа нет — генерируем из эффектов
        if (!effects.isEmpty()) {
            return "Зелье " + formatEffectName(effects.get(0).getType());
        }

        return "Зелье";
    }

    /**
     * Форматирует имя типа зелья
     */
    private String formatPotionTypeName(PotionType type) {
        String key = type.getKey().getKey();
        return POTION_NAMES.getOrDefault(key, "Зелье");
    }

    /**
     * Форматирует имя типа эффекта
     */
    private String formatEffectName(PotionEffectType type) {
        String key = type.getKey().getKey();
        return EFFECT_NAMES.getOrDefault(key, key);
    }

    /**
     * Переводит число в римские цифры
     */
    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }

    // Русские названия зелий
    private static final Map<String, String> POTION_NAMES = Map.ofEntries(
            Map.entry("speed", "Зелье скорости"),
            Map.entry("long_speed", "Зелье скорости"),
            Map.entry("strong_speed", "Зелье скорости"),
            Map.entry("slowness", "Зелье замедления"),
            Map.entry("long_slowness", "Зелье замедления"),
            Map.entry("strong_slowness", "Зелье замедления"),
            Map.entry("strength", "Зелье силы"),
            Map.entry("long_strength", "Зелье силы"),
            Map.entry("strong_strength", "Зелье силы"),
            Map.entry("healing", "Зелье лечения"),
            Map.entry("strong_healing", "Зелье лечения"),
            Map.entry("harming", "Зелье урона"),
            Map.entry("strong_harming", "Зелье урона"),
            Map.entry("poison", "Зелье отравления"),
            Map.entry("long_poison", "Зелье отравления"),
            Map.entry("strong_poison", "Зелье отравления"),
            Map.entry("regeneration", "Зелье регенерации"),
            Map.entry("long_regeneration", "Зелье регенерации"),
            Map.entry("strong_regeneration", "Зелье регенерации"),
            Map.entry("fire_resistance", "Зелье огнестойкости"),
            Map.entry("long_fire_resistance", "Зелье огнестойкости"),
            Map.entry("water_breathing", "Зелье подводного дыхания"),
            Map.entry("long_water_breathing", "Зелье подводного дыхания"),
            Map.entry("invisibility", "Зелье невидимости"),
            Map.entry("long_invisibility", "Зелье невидимости"),
            Map.entry("night_vision", "Зелье ночного зрения"),
            Map.entry("long_night_vision", "Зелье ночного зрения"),
            Map.entry("leaping", "Зелье прыгучести"),
            Map.entry("long_leaping", "Зелье прыгучести"),
            Map.entry("strong_leaping", "Зелье прыгучести"),
            Map.entry("slow_falling", "Зелье плавного падения"),
            Map.entry("long_slow_falling", "Зелье плавного падения"),
            Map.entry("swiftness", "Зелье скорости"),
            Map.entry("long_swiftness", "Зелье скорости"),
            Map.entry("strong_swiftness", "Зелье скорости"),
            Map.entry("turtle_master", "Зелье черепашьей мощи"),
            Map.entry("long_turtle_master", "Зелье черепашьей мощи"),
            Map.entry("strong_turtle_master", "Зелье черепашьей мощи"),
            Map.entry("weakness", "Зелье слабости"),
            Map.entry("long_weakness", "Зелье слабости"),
            Map.entry("luck", "Зелье удачи"),
            Map.entry("wind_charged", "Зелье ветра"),
            Map.entry("weaving", "Зелье паутины"),
            Map.entry("oozing", "Зелье слизи"),
            Map.entry("infested", "Зелье заражения")
    );

    // Русские названия эффектов (на случай если нет базового типа)
    private static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("speed", "скорости"),
            Map.entry("slowness", "замедления"),
            Map.entry("haste", "спешки"),
            Map.entry("mining_fatigue", "утомления"),
            Map.entry("strength", "силы"),
            Map.entry("instant_health", "лечения"),
            Map.entry("instant_damage", "урона"),
            Map.entry("jump_boost", "прыгучести"),
            Map.entry("nausea", "тошноты"),
            Map.entry("regeneration", "регенерации"),
            Map.entry("resistance", "сопротивления"),
            Map.entry("fire_resistance", "огнестойкости"),
            Map.entry("water_breathing", "подводного дыхания"),
            Map.entry("invisibility", "невидимости"),
            Map.entry("blindness", "слепоты"),
            Map.entry("night_vision", "ночного зрения"),
            Map.entry("hunger", "голода"),
            Map.entry("weakness", "слабости"),
            Map.entry("poison", "отравления"),
            Map.entry("wither", "иссушения"),
            Map.entry("health_boost", "здоровья"),
            Map.entry("absorption", "поглощения"),
            Map.entry("saturation", "насыщения"),
            Map.entry("glowing", "свечения"),
            Map.entry("levitation", "левитации"),
            Map.entry("luck", "удачи"),
            Map.entry("unluck", "неудачи"),
            Map.entry("slow_falling", "плавного падения"),
            Map.entry("conduit_power", "силы потока"),
            Map.entry("dolphins_grace", "грации дельфина"),
            Map.entry("darkness", "тьмы"),
            Map.entry("wind_charged", "ветра"),
            Map.entry("weaving", "паутины"),
            Map.entry("oozing", "слизи"),
            Map.entry("infested", "заражения")
    );
}
