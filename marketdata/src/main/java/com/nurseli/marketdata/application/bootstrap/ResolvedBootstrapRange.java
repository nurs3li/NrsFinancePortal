package com.nurseli.marketdata.application.bootstrap;

import java.time.LocalDate;

public record ResolvedBootstrapRange(
        LocalDate from,
        LocalDate to,
        BootstrapMode mode,
        String reason
) {
    public boolean shouldFetch() {
        return mode != BootstrapMode.SKIP && from != null && to != null && !to.isBefore(from);
    }
}
