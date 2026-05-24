package com.nurseli.nrsfinanceportal.config;

/**
 * Public REST API URI versioning: {@code /api/v1/...} (canonical) with legacy {@code /api/...} aliases.
 */
public final class ApiPaths {

    public static final String V1_PREFIX = "/api/v1";
    public static final String LEGACY_PREFIX = "/api";

    private ApiPaths() {
    }

  /** Maps a path suffix to versioned and legacy controller base paths. */
    public static String[] v1WithLegacy(String suffix) {
        String normalized = suffix.startsWith("/") ? suffix : "/" + suffix;
        return new String[]{V1_PREFIX + normalized, LEGACY_PREFIX + normalized};
    }

  /** Normalizes a request path to the legacy {@code /api/...} form for filters and matchers. */
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

  /** Converts a legacy public API path to its {@code /api/v1/...} equivalent. */
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
