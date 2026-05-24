package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.api.dto.bond.*;
import com.nurseli.nrsfinanceportal.application.bond.ManualBondPositionService;
import com.nurseli.nrsfinanceportal.application.viopbond.PositionHistoricalPriceResolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Kullanıcı manuel tahvil pozisyonları için CRUD ve fiyat çözümleme endpoint'lerini sunar.
 */
@RestController
@RequestMapping("/api/me/bond-positions")
@RequiredArgsConstructor
public class ManualBondPositionController {

    private final ManualBondPositionService service;
    private final PositionHistoricalPriceResolverService historicalPriceResolver;

    /**
     * {@code list} — Oturum açmış kullanıcının tüm tahvil pozisyonlarını listeler.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<List<ManualBondPositionDto>> list() {
        return ApiResponse.success(service.listMine());
    }

    /**
     * {@code summary} — Tahvil portföyü özet metriklerini döner.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<BondPositionSummaryDto> summary() {
        return ApiResponse.success(service.summaryMine());
    }

    /**
     * {@code resolvePrice} — Sembol ve tarih için geçmiş tahvil fiyatını çözümler.
     */
    @GetMapping("/resolve-price")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<PositionHistoricalPriceResolveDto> resolvePrice(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(historicalPriceResolver.resolveBond(symbol, date));
    }

    /**
     * {@code create} — Yeni manuel tahvil pozisyonu açar.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> create(@Valid @RequestBody ManualBondPositionCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /**
     * {@code update} — Açık tahvil pozisyonunu günceller.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ManualBondPositionUpdateRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /**
     * {@code sell} — Tahvil pozisyonunu satış ile kapatır.
     */
    @PostMapping("/{id}/sell")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> sell(
            @PathVariable Long id,
            @Valid @RequestBody ManualBondPositionSellRequest request) {
        return ApiResponse.success(service.sell(id, request));
    }

    /**
     * {@code delete} — Tahvil pozisyonunu kalıcı olarak siler.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
