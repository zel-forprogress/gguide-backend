package person.hardy.gguide.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameCategoryCatalog {

    public static final String UNCATEGORIZED = "UNCATEGORIZED";

    private static final Map<String, Map<String, String>> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        register("ACTION", "动作", "Action");
        register("ADVENTURE", "冒险", "Adventure");
        register("RPG", "角色扮演", "RPG");
        register("SHOOTER", "射击", "Shooter");
        register("STRATEGY", "策略", "Strategy");
        register("SIMULATION", "模拟", "Simulation");
        register("SPORTS", "体育", "Sports");
        register("RACING", "竞速", "Racing");
        register("INDIE", "独立游戏", "Indie");
        register("CASUAL", "休闲", "Casual");
        register("SURVIVAL", "生存", "Survival");
        register("HORROR", "恐怖", "Horror");
        register("PUZZLE", "解谜", "Puzzle");
        register("TURN_BASED", "回合制", "Turn-Based");
        register("SANDBOX", "沙盒", "Sandbox");
        register("OPEN_WORLD", "开放世界", "Open World");
        register("SINGLE_PLAYER", "单人", "Single Player");
        register("MULTIPLAYER", "多人在线", "Multiplayer");
        register("COOP", "合作", "Co-op");
        register(UNCATEGORIZED, "未分类", "Uncategorized");
    }

    private GameCategoryCatalog() {
    }

    public static List<String> normalizeCodes(List<String> rawCategories) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawCategories != null) {
            for (String rawCategory : rawCategories) {
                String code = normalizeCode(rawCategory);
                if (code != null) {
                    normalized.add(code);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add(UNCATEGORIZED);
        }

        return new ArrayList<>(normalized);
    }

    public static List<String> resolveLabels(List<String> categories, String locale) {
        List<String> labels = new ArrayList<>();
        for (String code : normalizeCodes(categories)) {
            labels.add(resolveLabel(code, locale));
        }
        return labels;
    }

    public static String normalizeCode(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }

        String trimmed = rawCategory.trim();
        if (LABELS.containsKey(trimmed)) {
            return trimmed;
        }

        String aliasKey = trimmed.toLowerCase();
        if (ALIASES.containsKey(aliasKey)) {
            return ALIASES.get(aliasKey);
        }

        String codeLike = trimmed.toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        if (LABELS.containsKey(codeLike)) {
            return codeLike;
        }

        return null;
    }

    public static String resolveLabel(String code, String locale) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null) {
            normalizedCode = UNCATEGORIZED;
        }

        Map<String, String> localizedLabels = LABELS.getOrDefault(normalizedCode, LABELS.get(UNCATEGORIZED));
        return LocaleUtil.LOCALE_EN_US.equals(locale)
                ? localizedLabels.get(LocaleUtil.LOCALE_EN_US)
                : localizedLabels.get(LocaleUtil.LOCALE_ZH_CN);
    }

    private static void register(String code, String zhLabel, String enLabel) {
        Map<String, String> localizedLabels = new LinkedHashMap<>();
        localizedLabels.put(LocaleUtil.LOCALE_ZH_CN, zhLabel);
        localizedLabels.put(LocaleUtil.LOCALE_EN_US, enLabel);
        LABELS.put(code, localizedLabels);

        ALIASES.put(code.toLowerCase(), code);
        ALIASES.put(zhLabel.toLowerCase(), code);
        ALIASES.put(enLabel.toLowerCase(), code);
    }
}
