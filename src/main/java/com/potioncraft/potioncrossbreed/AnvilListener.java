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
    private final NamespacedKey CUSTOM_POTION_KEY;

    // Максимальный уровень эффекта (amplifier 2 = уровень 3)
    private static final int MAX_AMPLIFIER = 2;
    // Стоимость в уровнях опыта
    private static final int EXP_COST = 5;

    public AnvilListener(PotionCrossbreed plugin) {
        this.plugin = plugin;
        this.CROSSBREED_KEY = new NamespacedKey(plugin, "crossbreed");
        this.CUSTOM_POTION_KEY = new NamespacedKey(plugin, "custom_potion");
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
     * Проверяет, является ли зелье нашим кастомным
     */
    private boolean isCustomPotion(PotionMeta meta) {
        return meta.getPersistentDataContainer().has(CUSTOM_POTION_KEY, PersistentDataType.BYTE);
    }

    /**
     * Проверяет, является ли зелье скрещенным (несколько разных базовых типов)
     */
    private boolean isCrossbreedPotion(PotionMeta meta) {
        return meta.getPersistentDataContainer().has(CROSSBREED_KEY, PersistentDataType.BYTE);
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

        // Собираем эффекты обоих зелий
        List<PotionEffect> firstEffects = getAllEffects(firstMeta);
        List<PotionEffect> secondEffects = getAllEffects(secondMeta);

        if (firstEffects.isEmpty() && secondEffects.isEmpty()) return;

        // Объединяем эффекты
        Map<PotionEffectType, PotionEffect> mergedEffects = new LinkedHashMap<>();
        boolean hasChange = false;

        // Сначала добавляем все эффекты первого зелья
        for (PotionEffect effect : firstEffects) {
            mergedEffects.put(effect.getType(), effect);
        }

        // Затем обрабатываем эффекты второго зелья
        for (PotionEffect effect : secondEffects) {
            if (mergedEffects.containsKey(effect.getType())) {
                PotionEffect existing = mergedEffects.get(effect.getType());
                // Совпадающий эффект — увеличиваем amplifier на 1
                int newAmplifier = Math.max(existing.getAmplifier(), effect.getAmplifier()) + 1;
                // Ограничиваем максимальным уровнем
                if (newAmplifier > MAX_AMPLIFIER) {
                    newAmplifier = MAX_AMPLIFIER;
                }
                // Проверяем, есть ли реальное улучшение
                if (newAmplifier > existing.getAmplifier() || newAmplifier > effect.getAmplifier()) {
                    hasChange = true;
                }
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
                // Новый эффект — добавляем
                mergedEffects.put(effect.getType(), effect);
                hasChange = true;
            }
        }

        // Если ничего не изменилось (все эффекты уже на максимуме) — не показываем результат
        if (!hasChange) return;

        // Определяем, является ли результат скрещенным
        boolean firstIsCross = isCrossbreedPotion(firstMeta);
        boolean secondIsCross = isCrossbreedPotion(secondMeta);
        Set<PotionEffectType> firstTypes = getEffectTypes(firstEffects);
        Set<PotionEffectType> secondTypes = getEffectTypes(secondEffects);
        boolean isCrossbreed = firstIsCross || secondIsCross || !firstTypes.equals(secondTypes);

        // Создаём результат
        ItemStack result = new ItemStack(first.getType());
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();

        // Убираем базовый тип, чтобы использовать только кастомные эффекты
        resultMeta.setBasePotionType(null);

        // Добавляем все объединённые эффекты
        for (PotionEffect effect : mergedEffects.values()) {
            resultMeta.addCustomEffect(effect, true);
        }

        // Помечаем как наше кастомное зелье
        resultMeta.getPersistentDataContainer().set(CUSTOM_POTION_KEY, PersistentDataType.BYTE, (byte) 1);

        // Устанавливаем название и лор
        if (isCrossbreed) {
            resultMeta.getPersistentDataContainer().set(CROSSBREED_KEY, PersistentDataType.BYTE, (byte) 1);
            resultMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Скрещенное зелье");
        } else {
            // Одинаковые зелья — название с уровнем по максимальному эффекту
            String baseName = getPotionBaseName(firstMeta, firstEffects);
            int maxAmp = 0;
            for (PotionEffect effect : mergedEffects.values()) {
                maxAmp = Math.max(maxAmp, effect.getAmplifier());
            }
            resultMeta.setDisplayName(ChatColor.AQUA + baseName + " " + toRoman(maxAmp + 1));
        }

        // Лор — список эффектов с уровнями
        List<String> lore = new ArrayList<>();
        if (isCrossbreed) {
            lore.add(ChatColor.DARK_PURPLE + "Скрещенное");
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Эффекты:");
        for (PotionEffect effect : mergedEffects.values()) {
            String effectName = formatEffectName(effect.getType());
            String level = toRoman(effect.getAmplifier() + 1);
            String durationStr = formatDuration(effect.getDuration());
            lore.add(ChatColor.WHITE + " " + effectName + " " + level + ChatColor.GRAY + " (" + durationStr + ")");
        }
        resultMeta.setLore(lore);

        result.setItemMeta(resultMeta);

        // Устанавливаем результат и стоимость
        inv.setRepairCost(EXP_COST);
        event.setResult(result);
    }

    /**
     * Обработка клика по результату наковальни — вручную выдаём предмет
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvilInv)) return;
        if (event.getRawSlot() != 2) return; // Слот результата

        ItemStack result = anvilInv.getItem(2);
        if (result == null || !isPotion(result)) return;

        PotionMeta meta = (PotionMeta) result.getItemMeta();
        if (meta == null) return;

        // Проверяем, что это наш результат
        if (!meta.getPersistentDataContainer().has(CUSTOM_POTION_KEY, PersistentDataType.BYTE)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Проверяем уровень опыта
        if (player.getLevel() < EXP_COST) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✖ Недостаточно опыта! Нужно " + ChatColor.GOLD + EXP_COST + " уровней" + ChatColor.RED + ".");
            return;
        }

        // Отменяем стандартное поведение — Spigot не даёт забрать кастомный результат
        event.setCancelled(true);

        // Копируем результат до очистки
        ItemStack resultCopy = result.clone();

        // Очищаем слоты наковальни
        anvilInv.setItem(0, null);
        anvilInv.setItem(1, null);
        anvilInv.setItem(2, null);

        // Забираем опыт
        player.setLevel(player.getLevel() - EXP_COST);

        // Выдаём результат на курсор игрока
        player.setItemOnCursor(resultCopy);

        player.sendMessage(ChatColor.GREEN + "✔ Зелья успешно скрещены! " + ChatColor.GOLD + "-" + EXP_COST + " уровней");
    }

    /**
     * Форматирует длительность из тиков в мм:сс
     */
    private String formatDuration(int ticks) {
        if (ticks < 0) return "∞";
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Получает красивое имя зелья на основе эффектов
     */
    private String getPotionBaseName(PotionMeta meta, List<PotionEffect> effects) {
        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            return formatPotionTypeName(baseType);
        }

        // Для кастомных зелий — берём имя по первому эффекту
        if (meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty()) {
            String effectName = formatEffectName(meta.getCustomEffects().get(0).getType());
            return "Зелье " + effectName;
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

    // Русские названия эффектов
    private static final Map<String, String> EFFECT_NAMES = new HashMap<>();
    static {
        EFFECT_NAMES.put("speed", "Скорость");
        EFFECT_NAMES.put("slowness", "Замедление");
        EFFECT_NAMES.put("haste", "Спешка");
        EFFECT_NAMES.put("mining_fatigue", "Утомление");
        EFFECT_NAMES.put("strength", "Сила");
        EFFECT_NAMES.put("instant_health", "Лечение");
        EFFECT_NAMES.put("instant_damage", "Урон");
        EFFECT_NAMES.put("jump_boost", "Прыгучесть");
        EFFECT_NAMES.put("nausea", "Тошнота");
        EFFECT_NAMES.put("regeneration", "Регенерация");
        EFFECT_NAMES.put("resistance", "Сопротивление");
        EFFECT_NAMES.put("fire_resistance", "Огнестойкость");
        EFFECT_NAMES.put("water_breathing", "Подводное дыхание");
        EFFECT_NAMES.put("invisibility", "Невидимость");
        EFFECT_NAMES.put("blindness", "Слепота");
        EFFECT_NAMES.put("night_vision", "Ночное зрение");
        EFFECT_NAMES.put("hunger", "Голод");
        EFFECT_NAMES.put("weakness", "Слабость");
        EFFECT_NAMES.put("poison", "Отравление");
        EFFECT_NAMES.put("wither", "Иссушение");
        EFFECT_NAMES.put("health_boost", "Доп. здоровье");
        EFFECT_NAMES.put("absorption", "Поглощение");
        EFFECT_NAMES.put("saturation", "Насыщение");
        EFFECT_NAMES.put("glowing", "Свечение");
        EFFECT_NAMES.put("levitation", "Левитация");
        EFFECT_NAMES.put("luck", "Удача");
        EFFECT_NAMES.put("unluck", "Неудача");
        EFFECT_NAMES.put("slow_falling", "Плавное падение");
        EFFECT_NAMES.put("conduit_power", "Сила потока");
        EFFECT_NAMES.put("dolphins_grace", "Грация дельфина");
        EFFECT_NAMES.put("darkness", "Тьма");
        EFFECT_NAMES.put("wind_charged", "Заряд ветра");
        EFFECT_NAMES.put("weaving", "Паутина");
        EFFECT_NAMES.put("oozing", "Слизь");
        EFFECT_NAMES.put("infested", "Заражение");
    }
}
