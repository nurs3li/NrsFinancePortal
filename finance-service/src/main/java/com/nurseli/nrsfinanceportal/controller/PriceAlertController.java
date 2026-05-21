package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertCreateRequest;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertDto;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertPageResponse;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertUpdateRequest;
import com.nurseli.nrsfinanceportal.service.pricealert.PriceAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/price-alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "all") String filter) {
        if (page != null) {
            int resolvedSize = size != null ? size : 10;
            return ApiResponse.success(priceAlertService.listPageForCurrentUser(page, resolvedSize, filter));
        }
        return ApiResponse.success(priceAlertService.listForCurrentUser());
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    public ApiResponse<PriceAlertDto> create(@RequestBody PriceAlertCreateRequest request) {
        return ApiResponse.success(priceAlertService.create(request));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<PriceAlertDto> update(@PathVariable Long id, @RequestBody PriceAlertUpdateRequest request) {
        return ApiResponse.success(priceAlertService.update(id, request));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        priceAlertService.delete(id);
        return ApiResponse.success(null);
    }
}
