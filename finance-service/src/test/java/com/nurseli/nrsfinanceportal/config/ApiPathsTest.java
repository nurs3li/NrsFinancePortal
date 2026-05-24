package com.nurseli.nrsfinanceportal.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPathsTest {

    @Test
    void v1WithLegacy_buildsVersionedAndLegacyPaths() {
        assertArrayEquals(
                new String[]{"/api/v1/dashboard", "/api/dashboard"},
                ApiPaths.v1WithLegacy("/dashboard"));
    }

    @Test
    void legacyFromRequest_normalizesVersionedPaths() {
        assertEquals("/api/dashboard/summary", ApiPaths.legacyFromRequest("/api/v1/dashboard/summary"));
        assertEquals("/api/public/login", ApiPaths.legacyFromRequest("/api/v1/public/login"));
    }

    @Test
    void matchesLegacyOrV1_acceptsBothForms() {
        assertTrue(ApiPaths.matchesLegacyOrV1("/api/v1/users/me", "/api/users/me"));
        assertTrue(ApiPaths.matchesLegacyOrV1("/api/users/me", "/api/users/me"));
        assertFalse(ApiPaths.matchesLegacyOrV1("/api/v2/users/me", "/api/users/me"));
    }

    @Test
    void startsWithLegacyOrV1_acceptsBothForms() {
        assertTrue(ApiPaths.startsWithLegacyOrV1("/api/v1/portfolio/ai/analyses", "/api/portfolio/ai/"));
        assertTrue(ApiPaths.startsWithLegacyOrV1("/api/portfolio/ai/analyses", "/api/portfolio/ai/"));
    }
}
