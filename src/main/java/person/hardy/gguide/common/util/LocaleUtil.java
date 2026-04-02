package person.hardy.gguide.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocaleUtil {

    public static final String LOCALE_ZH_CN = "zh-CN";
    public static final String LOCALE_EN_US = "en-US";

    private LocaleUtil() {
    }

    public static String resolveRequestedLocale(String lang, String acceptLanguage) {
        String preferred = firstNonBlank(lang, acceptLanguage);
        if (preferred == null) {
            return LOCALE_ZH_CN;
        }

        String normalized = preferred.trim().toLowerCase();
        if (normalized.contains(",")) {
            normalized = normalized.substring(0, normalized.indexOf(','));
        }

        if (normalized.startsWith("zh")) {
            return LOCALE_ZH_CN;
        }

        return LOCALE_EN_US;
    }

    public static String resolveText(Map<String, String> values, String locale) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        String preferred = values.get(locale);
        if (hasText(preferred)) {
            return preferred.trim();
        }

        String zhText = values.get(LOCALE_ZH_CN);
        if (hasText(zhText)) {
            return zhText.trim();
        }

        String enText = values.get(LOCALE_EN_US);
        if (hasText(enText)) {
            return enText.trim();
        }

        return values.values().stream()
                .filter(LocaleUtil::hasText)
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    public static Map<String, String> normalizeTranslations(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return normalized;
        }

        putIfHasText(normalized, LOCALE_ZH_CN, values.get(LOCALE_ZH_CN));
        putIfHasText(normalized, LOCALE_EN_US, values.get(LOCALE_EN_US));

        values.forEach((key, value) -> {
            if (!hasText(key) || !hasText(value)) {
                return;
            }

            String localeKey = normalizeLocaleKey(key);
            normalized.putIfAbsent(localeKey, value.trim());
        });

        return normalized;
    }

    public static Map<String, String> normalizeTranslations(Map<String, String> values, String fallbackValue) {
        Map<String, String> normalized = normalizeTranslations(values);
        if (!hasText(fallbackValue)) {
            return normalized;
        }

        String trimmed = fallbackValue.trim();
        normalized.putIfAbsent(LOCALE_ZH_CN, trimmed);
        normalized.putIfAbsent(LOCALE_EN_US, trimmed);
        return normalized;
    }

    public static List<String> supportedLocales() {
        List<String> locales = new ArrayList<>();
        locales.add(LOCALE_ZH_CN);
        locales.add(LOCALE_EN_US);
        return locales;
    }

    private static void putIfHasText(Map<String, String> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private static String normalizeLocaleKey(String locale) {
        String normalized = locale.trim().replace('_', '-');
        if (normalized.equalsIgnoreCase("zh") || normalized.equalsIgnoreCase("zh-cn")) {
            return LOCALE_ZH_CN;
        }
        if (normalized.equalsIgnoreCase("en") || normalized.equalsIgnoreCase("en-us")) {
            return LOCALE_EN_US;
        }
        return normalized;
    }

    private static String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        if (hasText(second)) {
            return second;
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
