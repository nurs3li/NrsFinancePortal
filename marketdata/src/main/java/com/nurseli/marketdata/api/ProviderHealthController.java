package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.ProviderHealthDto;
import com.nurseli.marketdata.application.query.ProviderHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/providers")
@RequiredArgsConstructor
@Profile("ops")
public class ProviderHealthController {

    private final ProviderHealthService providerHealthService;

    @GetMapping("/health")
    public List<ProviderHealthDto> health() {
        return providerHealthService.getHealth();
    }
}
