package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makro panel JSON'undan OpenAI context için küçük özet.
 */
public final class PortfolioAiMacroCompact {

    private PortfolioAiMacroCompact() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compact(Map<String, Object> panel) {
        if (panel == null || panel.isEmpty()) {
            return Map.of("macroAvailability", "NOT_AVAILABLE");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Object generatedAt = panel.get("generatedAt");
        if (generatedAt != null) {
            out.put("macroDate", generatedAt);
        }
        Object derivedObj = panel.get("derived");
        if (derivedObj instanceof Map<?, ?> derived) {
            putIfPresent(out, "cpiYoY", derived.get("cpiYoY"));
            putIfPresent(out, "cpiMoM", derived.get("cpiMoM"));
            putIfPresent(out, "realPolicyRate", derived.get("realPolicyRate"));
            putIfPresent(out, "depositRealSpread", derived.get("realDepositRate"));
        }
        Double policyRate = extractPolicyRate(panel);
        if (policyRate != null) {
            out.put("policyRate", policyRate);
        }
        String availability = out.size() <= 1 ? "NOT_AVAILABLE" : (policyRate == null ? "PARTIAL" : "AVAILABLE");
        out.put("macroAvailability", availability);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Double extractPolicyRate(Map<String, Object> panel) {
        Object seriesObj = panel.get("series");
        if (!(seriesObj instanceof List<?> series)) {
            return null;
        }
        for (Object item : series) {
            if (!(item instanceof Map<?, ?> seriesMap)) {
                continue;
            }
            String code = stringVal(seriesMap.get("code"));
            String logicalKey = stringVal(seriesMap.get("logicalKey"));
            if (isPolicySeries(code, logicalKey)) {
                Double latest = latestObservationValue(seriesMap.get("observations"));
                if (latest != null) {
                    return latest;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Double latestObservationValue(Object observationsObj) {
        if (!(observationsObj instanceof List<?> observations) || observations.isEmpty()) {
            return null;
        }
        Object last = observations.get(observations.size() - 1);
        if (last instanceof Map<?, ?> obs) {
            Object v = obs.get("value");
            if (v instanceof Number n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    private static boolean isPolicySeries(String code, String logicalKey) {
        String c = code != null ? code.toUpperCase() : "";
        String lk = logicalKey != null ? logicalKey.toUpperCase() : "";
        return c.contains("POLICY") || lk.contains("POLICY_RATE") || lk.contains("POLICYRATE");
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }

    private static void putIfPresent(Map<String, Object> out, String key, Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }
}
