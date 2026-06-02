package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.MarketTerminalListPageResponse;
import com.nurseli.nrsfinanceportal.application.market.MarketTerminalListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Piyasa terminali varlık listesi için sayfalı sorgu endpoint'ini sunar.
 */
@RestController
@RequiredArgsConstructor
public class MarketTerminalListController {

    private final MarketTerminalListService marketTerminalListService;

    /**
     * {@code list} — Kategori, alt piyasa, filtre, sıralama ve arama ile sayfalı terminal listesi döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping({"/api/v1/market/terminal/list", "/api/market/terminal/list"})
    public MarketTerminalListPageResponse list(
            @RequestParam String category,
            @RequestParam(required = false) String equitySubmarket,
            @RequestParam(required = false) String fundSubmarket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String search) {
        return marketTerminalListService.list(category, equitySubmarket, fundSubmarket, page, size, filter, sort, dir, search);
    }
}
