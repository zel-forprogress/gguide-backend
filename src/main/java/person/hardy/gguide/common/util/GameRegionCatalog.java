package person.hardy.gguide.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GameRegionCatalog {

    public static final String UNKNOWN = "UNKNOWN";

    private static final Map<String, Map<String, String>> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        register("CN", "中国", "China");
        register("JP", "日本", "Japan");
        register("US", "美国", "United States");
        register("CA", "加拿大", "Canada");
        register("PL", "波兰", "Poland");
        register("SE", "瑞典", "Sweden");
        register("UK", "英国", "United Kingdom");
        register("FR", "法国", "France");
        register("KR", "韩国", "South Korea");
        register("AU", "澳大利亚", "Australia");
        register("BE", "比利时", "Belgium");
        register("DK", "丹麦", "Denmark");
        register("IE", "爱尔兰", "Ireland");
        register("UNKNOWN", "暂未标注", "Unknown");
    }

    private GameRegionCatalog() {
    }

    public static String normalizeCode(String rawRegion) {
        if (rawRegion == null || rawRegion.isBlank()) {
            return UNKNOWN;
        }

        String trimmed = rawRegion.trim();
        if (LABELS.containsKey(trimmed)) {
            return trimmed;
        }

        String aliasKey = trimmed.toLowerCase();
        if (ALIASES.containsKey(aliasKey)) {
            return ALIASES.get(aliasKey);
        }

        String uppercase = trimmed.toUpperCase();
        if (LABELS.containsKey(uppercase)) {
            return uppercase;
        }

        return UNKNOWN;
    }

    public static String resolveLabel(String regionCode, String locale) {
        String normalizedCode = normalizeCode(regionCode);
        Map<String, String> localizedLabels = LABELS.getOrDefault(normalizedCode, LABELS.get(UNKNOWN));
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
