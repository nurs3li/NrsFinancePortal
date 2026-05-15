package com.nurseli.marketdata.viop.domain;

public enum ViopDataQuality {
    OK,
    EMPTY_RESPONSE,
    PARTIAL,
    PROVIDER_ERROR,
    MALFORMED_RESPONSE,
    STALE_CACHE
}
