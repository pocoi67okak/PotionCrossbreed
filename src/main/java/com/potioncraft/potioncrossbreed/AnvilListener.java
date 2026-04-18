package com.potioncraft.potioncrossbreed;

import org.bukkit.ChatColor;
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
            resultMeta.getPersistentDataContainer().set(CROSSBREED_KEY, PersistentDataType.BYTE, (byte) 1);
            resultMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Скрещенное зелье");
        } else {
            // Одинаковые зелья — добавляем уровень к названию
            String baseName = getPotionBaseName(firstMeta, firstEffects);
            resultMeta.setDisplayName(ChatColor.AQUA + baseName + " " + toRoman(newLevel));
        }

        // Добавляем лор с информацией
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Уровень: " + toRoman(newLevel));
        if (isCrossbreed) {
            lore.add(ChatColor.DARK_PURPLE + "Скрещенное");
        }
        resultMeta.setLore(lore);

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
            player.sendMessage(ChatColor.RED + "✖ Недостаточно опыта! Нужно " + ChatColor.GOLD + EXP_COST + " уровней" + ChatColor.RED + ".");
            return;
        }

        // Забираем опыт
        player.setLevel(player.getLevel() - EXP_COST);
        player.sendMessage(ChatColor.GREEN + "✔ Зелья успешно скрещены! " + ChatColor.GOLD + "-" + EXP_COST + " уровней");
    }

    /**
     * Получает красивое имя зелья на основе эффектов
     */
    private String getPotionBaseName(PotionMeta meta, List<PotionEffect> effects) {
        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            return formatPotionTypeName(baseType);
        }

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
    private static final Map<String, String> POTION_NAMES = new HashMap<>();
    static {
        POTION_NAMES.put("speed", "Зелье скорости");
        POTION_NAMES.put("long_speed", "Зелье скорости");
        POTION_NAMES.put("strong_speed", "Зелье скорости");
        POTION_NAMES.put("slowness", "Зелье замедления");
        POTION_NAMES.put("long_slowness", "Зелье замедления");
        POTION_NAMES.put("strong_slowness", "Зелье замедления");
        POTION_NAMES.put("strength", "Зелье силы");
        POTION_NAMES.put("long_strength", "Зелье силы");
        POTION_NAMES.put("strong_strength", "Зелье силы");
        POTION_NAMES.put("healing", "Зелье лечения");
        POTION_NAMES.put("strong_healing", "Зелье лечения");
        POTION_NAMES.put("harming", "Зелье урона");
        POTION_NAMES.put("strong_harming", "Зелье урона");
        POTION_NAMES.put("poison", "Зелье отравления");
        POTION_NAMES.put("long_poison", "Зелье отравления");
        POTION_NAMES.put("strong_poison", "Зелье отравления");
        POTION_NAMES.put("regeneration", "Зелье регенерации");
        POTION_NAMES.put("long_regeneration", "Зелье регенерации");
        POTION_NAMES.put("strong_regeneration", "Зелье регенерации");
        POTION_NAMES.put("fire_resistance", "Зелье огнестойкости");
        POTION_NAMES.put("long_fire_resistance", "Зелье огнестойкости");
        POTION_NAMES.put("water_breathing", "Зелье подводного дыхания");
        POTION_NAMES.put("long_water_breathing", "Зелье подводного дыхания");
        POTION_NAMES.put("invisibility", "Зелье невидимости");
        POTION_NAMES.put("long_invisibility", "Зелье невидимости");
        POTION_NAMES.put("night_vision", "Зелье ночного зрения");
        POTION_NAMES.put("long_night_vision", "Зелье ночного зрения");
        POTION_NAMES.put("leaping", "Зелье прыгучести");
        POTION_NAMES.put("long_leaping", "Зелье прыгучести");
        POTION_NAMES.put("strong_leaping", "Зелье прыгучести");
        POTION_NAMES.put("slow_falling", "Зелье плавного падения");
        POTION_NAMES.put("long_slow_falling", "Зелье плавного падения");
        POTION_NAMES.put("swiftness", "Зелье скорости");
        POTION_NAMES.put("long_swiftness", "Зелье скорости");
        POTION_NAMES.put("strong_swiftness", "Зелье скорости");
        POTION_NAMES.put("turtle_master", "Зелье черепашьей мощи");
        POTION_NAMES.put("long_turtle_master", "Зелье черепашьей мощи");
        POTION_NAMES.put("strong_turtle_master", "Зелье черепашьей мощи");
        POTION_NAMES.put("weakness", "Зелье слабости");
        POTION_NAMES.put("long_weakness", "Зелье слабости");
        POTION_NAMES.put("luck", "Зелье удачи");
        POTION_NAMES.put("wind_charged", "Зелье ветра");
        POTION_NAMES.put("weaving", "Зелье паутины");
        POTION_NAMES.put("oozing", "Зелье слизи");
        POTION_NAMES.put("infested", "Зелье заражения");
    }

    // Русские названия эффектов (на случай если нет базового типа)
    private static final Map<String, String> EFFECT_NAMES = new HashMap<>();
    static {
        EFFECT_NAMES.put("speed", "скорости");
        EFFECT_NAMES.put("slowness", "замедления");
        EFFECT_NAMES.put("haste", "спешки");
        EFFECT_NAMES.put("mining_fatigue", "утомления");
        EFFECT_NAMES.put("strength", "силы");
        EFFECT_NAMES.put("instant_health", "лечения");
        EFFECT_NAMES.put("instant_damage", "урона");
        EFFECT_NAMES.put("jump_boost", "прыгучести");
        EFFECT_NAMES.put("nausea", "тошноты");
        EFFECT_NAMES.put("regeneration", "регенерации");
        EFFECT_NAMES.put("resistance", "сопротивления");
        EFFECT_NAMES.put("fire_resistance", "огнестойкости");
        EFFECT_NAMES.put("water_breathing", "подводного дыхания");
        EFFECT_NAMES.put("invisibility", "невидимости");
        EFFECT_NAMES.put("blindness", "слепоты");
        EFFECT_NAMES.put("night_vision", "ночного зрения");
        EFFECT_NAMES.put("hunger", "голода");
        EFFECT_NAMES.put("weakness", "слабости");
        EFFECT_NAMES.put("poison", "отравления");
        EFFECT_NAMES.put("wither", "иссушения");
        EFFECT_NAMES.put("health_boost", "здоровья");
        EFFECT_NAMES.put("absorption", "поглощения");
        EFFECT_NAMES.put("saturation", "насыщения");
        EFFECT_NAMES.put("glowing", "свечения");
        EFFECT_NAMES.put("levitation", "левитации");
        EFFECT_NAMES.put("luck", "удачи");
        EFFECT_NAMES.put("unluck", "неудачи");
        EFFECT_NAMES.put("slow_falling", "плавного падения");
        EFFECT_NAMES.put("conduit_power", "силы потока");
        EFFECT_NAMES.put("dolphins_grace", "грации дельфина");
        EFFECT_NAMES.put("darkness", "тьмы");
        EFFECT_NAMES.put("wind_charged", "ветра");
        EFFECT_NAMES.put("weaving", "паутины");
        EFFECT_NAMES.put("oozing", "слизи");
        EFFECT_NAMES.put("infested", "заражения");
    }
}
