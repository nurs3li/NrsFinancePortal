package com.nurseli.marketdata.application.inflation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InflationMacroCacheService {

    @CacheEvict(cacheNames = "market:macro:inflation", allEntries = true)
    public void evictInflationCaches() {
        log.info("[INFLATION] evicted Redis cache namespace market:macro:inflation");
    }
}
