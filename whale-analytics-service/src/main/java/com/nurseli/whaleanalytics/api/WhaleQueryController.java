package com.nurseli.whaleanalytics.api;

import com.nurseli.whaleanalytics.application.WhaleQueryService;
import com.nurseli.whaleanalytics.domain.WhaleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whales")
@RequiredArgsConstructor
public class WhaleQueryController {

    private final WhaleQueryService whaleQueryService;

    @GetMapping("/{userId}")
    public WhaleResult getWhaleStatus(@PathVariable Long userId) {
        return whaleQueryService.getWhaleStatus(userId);
    }
}
