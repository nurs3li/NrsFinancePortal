package com.nurseli.notificationservice.application.contact;

import com.nurseli.notificationservice.config.NotificationEmailProperties;
import com.nurseli.notificationservice.infrastructure.finance.FinanceUserClient;
import com.nurseli.notificationservice.infrastructure.finance.FinanceUserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEmailResolverTest {

    @Mock
    private FinanceUserClient financeUserClient;

    private NotificationEmailProperties properties;
    private UserEmailResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new NotificationEmailProperties();
        resolver = new UserEmailResolver(financeUserClient, properties);
    }

    @Test
    void blankSub_returnsNull() {
        assertThat(resolver.resolveEmail("  ")).isNull();
    }

    @Test
    void verifiedRequired_unverified_returnsNull() {
        properties.setRequireFinanceEmailVerified(true);
        when(financeUserClient.getBySub("sub-1"))
                .thenReturn(new FinanceUserInfoResponse("sub-1", "u@test.com", false));

        assertThat(resolver.resolveEmail("sub-1")).isNull();
    }

    @Test
    void verifiedNotRequired_unverifiedStillReturnsEmail() {
        properties.setRequireFinanceEmailVerified(false);
        when(financeUserClient.getBySub("sub-1"))
                .thenReturn(new FinanceUserInfoResponse("sub-1", "u@test.com", false));

        assertThat(resolver.resolveEmail("sub-1")).isEqualTo("u@test.com");
    }

    @Test
    void financeClientThrows_returnsNull() {
        when(financeUserClient.getBySub("sub-1")).thenThrow(new RuntimeException("network"));

        assertThat(resolver.resolveEmail("sub-1")).isNull();
    }

    @Test
    void happyPath_returnsEmail() {
        when(financeUserClient.getBySub("sub-1"))
                .thenReturn(new FinanceUserInfoResponse("sub-1", "ok@test.com", true));

        assertThat(resolver.resolveEmail("sub-1")).isEqualTo("ok@test.com");
    }
}
