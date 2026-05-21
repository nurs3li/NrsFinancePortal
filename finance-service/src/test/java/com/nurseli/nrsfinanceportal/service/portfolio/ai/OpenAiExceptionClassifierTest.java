package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiExceptionClassifierTest {

    @Test
    void classifiesTimeout() {
        assertThat(OpenAiExceptionClassifier.classify(new TimeoutException("x")))
                .isEqualTo(OpenAiFailureReason.TIMEOUT);
    }

    @Test
    void classifiesReactiveTimeoutMessage() {
        assertThat(OpenAiExceptionClassifier.classify(new RuntimeException("Connection timed out")))
                .isEqualTo(OpenAiFailureReason.TIMEOUT);
    }
}
