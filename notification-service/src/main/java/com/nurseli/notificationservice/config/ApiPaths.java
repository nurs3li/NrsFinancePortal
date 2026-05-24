package com.nurseli.notificationservice.config;

/**
 * Public REST API URI versioning: {@code /api/v1/...} (canonical) with legacy {@code /api/...} aliases.
 */
public final class ApiPaths {

    public static final String V1_PREFIX = "/api/v1";
    public static final String LEGACY_PREFIX = "/api";

    private ApiPaths() {
    }

    public static String[] v1WithLegacy(String suffix) {
        String normalized = suffix.startsWith("/") ? suffix : "/" + suffix;
        return new String[]{V1_PREFIX + normalized, LEGACY_PREFIX + normalized};
    }

    public static String legacyFromRequest(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.startsWith(V1_PREFIX + "/")) {
            return LEGACY_PREFIX + path.substring(V1_PREFIX.length());
        }
        if (path.equals(V1_PREFIX)) {
            return LEGACY_PREFIX;
        }
        return path;
    }

    public static String v1FromLegacy(String legacyPath) {
        if (legacyPath == null || legacyPath.isBlank()) {
            return legacyPath;
        }
        if (legacyPath.startsWith(LEGACY_PREFIX + "/")) {
            return V1_PREFIX + legacyPath.substring(LEGACY_PREFIX.length());
        }
        if (legacyPath.equals(LEGACY_PREFIX)) {
            return V1_PREFIX;
        }
        return legacyPath;
    }

    public static boolean matchesLegacyOrV1(String path, String legacyPath) {
        if (path == null || legacyPath == null) {
            return false;
        }
        return path.equals(legacyPath) || path.equals(v1FromLegacy(legacyPath));
    }

    public static boolean startsWithLegacyOrV1(String path, String legacyPrefix) {
        if (path == null || legacyPrefix == null) {
            return false;
        }
        return path.startsWith(legacyPrefix) || path.startsWith(v1FromLegacy(legacyPrefix));
    }
}
