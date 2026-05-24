package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.api.dto.viop.*;
import com.nurseli.nrsfinanceportal.application.viop.ManualViopPositionService;
import com.nurseli.nrsfinanceportal.application.viopbond.PositionHistoricalPriceResolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Kullanıcı manuel VİOP pozisyonları için CRUD ve fiyat çözümleme endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/me/viop-positions", "/api/me/viop-positions"})
@RequiredArgsConstructor
public class ManualViopPositionController {

    private final ManualViopPositionService service;
    private final PositionHistoricalPriceResolverService historicalPriceResolver;

    /**
     * {@code list} — Oturum açmış kullanıcının tüm VİOP pozisyonlarını listeler.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<List<ManualViopPositionDto>> list() {
        return ApiResponse.success(service.listMine());
    }

    /**
     * {@code summary} — VİOP portföyü özet metriklerini döner.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ViopPositionSummaryDto> summary() {
        return ApiResponse.success(service.summaryMine());
    }

    /**
     * {@code resolvePrice} — Sembol ve tarih için geçmiş VİOP fiyatını çözümler.
     */
    @GetMapping("/resolve-price")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<PositionHistoricalPriceResolveDto> resolvePrice(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(historicalPriceResolver.resolveViop(symbol, date));
    }

    /**
     * {@code create} — Yeni manuel VİOP pozisyonu açar.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> create(@Valid @RequestBody ManualViopPositionCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ManualViopPositionUpdateRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /**
     * {@code close} — VİOP pozisyonunu kapanış ile sonlandırır.
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> close(
            @PathVariable Long id,
            @Valid @RequestBody ManualViopPositionCloseRequest request) {
        return ApiResponse.success(service.close(id, request));
    }

    /**
     * {@code delete} — VİOP pozisyonunu kalıcı olarak siler.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
