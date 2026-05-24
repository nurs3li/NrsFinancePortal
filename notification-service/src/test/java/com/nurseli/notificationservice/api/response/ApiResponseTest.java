package com.nurseli.notificationservice.api.response;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

  @Test
  void success_wrapsDataWithSuccessFlag() {
    ApiResponse<String> response = ApiResponse.success("payload");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isEqualTo("payload");
    assertThat(response.getErrors()).isNull();
    assertThat(response.getMeta()).isNull();
  }

  @Test
  void error_wrapsErrorsWithFailureFlag() {
    Map<String, Object> errors = Map.of("code", ApiErrorCode.VALIDATION_ERROR, "message", "bad");

    ApiResponse<?> response = ApiResponse.error(errors);

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getData()).isNull();
    assertThat(response.getErrors()).isEqualTo(errors);
    assertThat(response.getMeta()).isNull();
  }
}
