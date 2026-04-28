package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.ProviderHealthDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProviderHealthService {

    public List<ProviderHealthDto> getHealth() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new ProviderHealthDto("TCMB", now.minusMinutes(5), 0, Duration.between(now.minusMinutes(5), now).toSeconds()),
                new ProviderHealthDto("ETF", now.minusMinutes(15), 0, Duration.between(now.minusMinutes(15), now).toSeconds()),
                new ProviderHealthDto("FINHUB", now.minusMinutes(2), 0, Duration.between(now.minusMinutes(2), now).toSeconds())
        );
    }
}
