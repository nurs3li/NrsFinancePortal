package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.ViopMarketWatchResponse;
import com.nurseli.marketdata.application.ViopQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/viop", "/api/viop"})
@RequiredArgsConstructor
public class ViopV1Controller {
    private final ViopQueryService viopQueryService;

    @GetMapping("/market-watch")
    public ViopMarketWatchResponse marketWatch() {
        return viopQueryService.marketWatch();
    }
}

