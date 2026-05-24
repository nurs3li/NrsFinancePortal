package com.nurseli.nrsfinanceportal.integration.support;

import com.jayway.jsonpath.JsonPath;

/**
 * Integration test JSON helper'ları.
 */
public final class IntegrationTestJson {

    private IntegrationTestJson() {
    }

    public static long readLongId(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
