package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.MarketTerminalListPageResponse;
import com.nurseli.nrsfinanceportal.service.MarketTerminalListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketTerminalListController {

    private final MarketTerminalListService marketTerminalListService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/api/market/terminal/list")
    public MarketTerminalListPageResponse list(
            @RequestParam String category,
            @RequestParam(required = false) String equitySubmarket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String search) {
        return marketTerminalListService.list(category, equitySubmarket, page, size, filter, sort, dir, search);
    }
}
