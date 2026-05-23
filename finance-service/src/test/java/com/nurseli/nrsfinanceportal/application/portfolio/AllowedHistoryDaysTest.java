package com.nurseli.nrsfinanceportal.application.portfolio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedHistoryDaysTest {

    @Test
    void mapsExamples() {
        assertThat(AllowedHistoryDays.smallestCovering(3)).isEqualTo(5);
        assertThat(AllowedHistoryDays.smallestCovering(8)).isEqualTo(30);
        assertThat(AllowedHistoryDays.smallestCovering(100)).isEqualTo(180);
        assertThat(AllowedHistoryDays.smallestCovering(400)).isEqualTo(730);
        assertThat(AllowedHistoryDays.smallestCovering(900)).isEqualTo(1095);
        assertThat(AllowedHistoryDays.smallestCovering(3500)).isEqualTo(3650);
        assertThat(AllowedHistoryDays.smallestCovering(9000)).isEqualTo(8000);
    }
}
