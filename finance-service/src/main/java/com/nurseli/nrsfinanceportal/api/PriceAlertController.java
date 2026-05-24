package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.api.dto.pricealert.PriceAlertCreateRequest;
import com.nurseli.nrsfinanceportal.api.dto.pricealert.PriceAlertDto;
import com.nurseli.nrsfinanceportal.api.dto.pricealert.PriceAlertPageResponse;
import com.nurseli.nrsfinanceportal.api.dto.pricealert.PriceAlertUpdateRequest;
import com.nurseli.nrsfinanceportal.application.pricealert.PriceAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kullanıcı fiyat alarmı CRUD ve sayfalı listeleme endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/me/price-alerts", "/api/me/price-alerts"})
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    /**
     * {@code list} — Sayfa parametresi varsa sayfalı, yoksa tam liste response döner.
     */
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

    /**
     * {@code create} — Yeni fiyat alarmı kaydı oluşturur.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    public ApiResponse<PriceAlertDto> create(@RequestBody PriceAlertCreateRequest request) {
        return ApiResponse.success(priceAlertService.create(request));
    }

    /**
     * {@code update} — Mevcut fiyat alarmını günceller.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<PriceAlertDto> update(@PathVariable Long id, @RequestBody PriceAlertUpdateRequest request) {
        return ApiResponse.success(priceAlertService.update(id, request));
    }

    /**
     * {@code delete} — Fiyat alarmını kalıcı olarak siler.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        priceAlertService.delete(id);
        return ApiResponse.success(null);
    }
}
