package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import com.nurseli.nrsfinanceportal.api.dto.viop.*;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualViopPositionRepository;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * finance-service manuel VIOP pozisyon servisi — vadeli pozisyon CRUD, özet ve piyasa fiyatı destekli metrik DTO dönüşümünü yönetir.
 */
@Slf4j
@RequiredArgsConstructor
@Service

public class ManualViopPositionService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int EXPIRING_SOON_DAYS = 14;

    private final ManualViopPositionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final ViopMarketPriceResolver priceResolver;
    private final ViopPositionMetricsCalculator metricsCalculator;
    private final MarketDataClient marketDataClient;

    /**
     * {@code listMine} — Kullanıcının silinmemiş VIOP pozisyonlarını güncel fiyat ve metriklerle listeler.
     */
    @Transactional(readOnly = true)
    public List<ManualViopPositionDto> listMine() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        Map<String, BigDecimal> prices = priceResolver.loadLatestPricesBySymbol();
        LocalDate today = LocalDate.now(TZ);
        return repository.findByUser_IdAndStatusNotOrderByEntryDateDesc(user.getId(), ViopPositionStatus.DELETED)
                .stream()
                .map(p -> toDto(p, prices, today))
                .toList();
    }

    /**
     * {@code summaryMine} — Giriş yapan kullanıcı için açık VIOP pozisyon özetini döner.
     */
    @Transactional(readOnly = true)
    public ViopPositionSummaryDto summaryMine() {
        return summaryForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    /**
     * {@code summaryForUser} — Belirtilen kullanıcı için VIOP özet KPI'larını hesaplar.
     */
    @Transactional(readOnly = true)
    public ViopPositionSummaryDto summaryForUser(User user) {
        return summaryForUser(user, null);
    }

    /**
     * {@code summaryForUser} — Dashboard özeti için paylaşılan FX snapshot ile VIOP KPI hesaplar.
     */
    @Transactional(readOnly = true)
    public ViopPositionSummaryDto summaryForUser(User user, MarketDataClient.LatestPricingSnapshot fxPricing) {
        LocalDate today = LocalDate.now(TZ);
        List<ManualViopPosition> open = repository.findByUser_IdAndStatusOrderByEntryDateDesc(
                user.getId(), ViopPositionStatus.OPEN);
        if (open.isEmpty()) {
            return emptyViopSummary();
        }

        Map<String, BigDecimal> prices = priceResolver.loadLatestPricesBySymbol();
        ViopFxRates fxRates = loadFxRates(fxPricing);

        BigDecimal totalMargin = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal totalRisk = BigDecimal.ZERO;
        int longCount = 0;
        int shortCount = 0;
        int expiringSoon = 0;
        int incomplete = 0;
        boolean hasMissingFx = false;

        for (ManualViopPosition p : open) {
            BigDecimal market = resolvePrice(p, prices);
            ViopPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, market, today, fxRates);
            if (m.marginTry() != null) {
                totalMargin = totalMargin.add(m.marginTry());
            } else if (p.getInitialMargin() != null) {
                totalMargin = totalMargin.add(p.getInitialMargin());
            }
            if (m.unrealizedPnlTry() != null) {
                totalPnl = totalPnl.add(m.unrealizedPnlTry());
            } else if (market == null && p.getCurrentPrice() == null) {
                incomplete++;
            }
            if (m.riskExposureTry() != null) {
                totalRisk = totalRisk.add(m.riskExposureTry());
            }
            if (m.missingFxRate()) {
                hasMissingFx = true;
            }
            if (p.getDirection() == ViopDirection.LONG) {
                longCount++;
            } else {
                shortCount++;
            }
            if (m.daysToExpiry() != null && m.daysToExpiry() >= 0 && m.daysToExpiry() <= EXPIRING_SOON_DAYS) {
                expiringSoon++;
            }
        }

        BigDecimal netEffect = totalMargin.add(totalPnl).setScale(6, RoundingMode.HALF_UP);
        BigDecimal portfolioLeverage =
                totalMargin.signum() > 0
                        ? totalRisk.divide(totalMargin, 6, RoundingMode.HALF_UP)
                        : null;
        BigDecimal marginRatio =
                totalRisk.signum() > 0
                        ? totalMargin.divide(totalRisk, 6, RoundingMode.HALF_UP)
                        : null;
        BigDecimal pnlToMargin =
                totalMargin.signum() > 0
                        ? totalPnl.divide(totalMargin, 6, RoundingMode.HALF_UP)
                        : null;

        return new ViopPositionSummaryDto(
                open.size(),
                totalMargin.setScale(6, RoundingMode.HALF_UP),
                totalPnl.setScale(6, RoundingMode.HALF_UP),
                totalRisk.setScale(6, RoundingMode.HALF_UP),
                longCount,
                shortCount,
                expiringSoon,
                netEffect,
                incomplete,
                portfolioLeverage,
                marginRatio,
                pnlToMargin,
                hasMissingFx
        );
    }

    private static ViopPositionSummaryDto emptyViopSummary() {
        return new ViopPositionSummaryDto(
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                BigDecimal.ZERO,
                0,
                null,
                null,
                null,
                false
        );
    }

    private ViopFxRates loadFxRates() {
        return loadFxRates(null);
    }

    private ViopFxRates loadFxRates(MarketDataClient.LatestPricingSnapshot fxPricing) {
        try {
            var snap = fxPricing != null ? fxPricing : marketDataClient.loadLatestPricing();
            BigDecimal usdTry = marketDataClient.getPriceTry(AssetType.FX, "USDTRY", snap);
            BigDecimal eurTry = marketDataClient.getPriceTry(AssetType.FX, "EURTRY", snap);
            return new ViopFxRates(usdTry, eurTry);
        } catch (Exception e) {
            log.warn("VİOP FX rates unavailable: {}", e.getMessage());
            return ViopFxRates.empty();
        }
    }

    /**
     * {@code create} — Yeni manuel VIOP pozisyonu oluşturur; giriş fiyatını piyasadan veya manuel çözer.
     */
    @Transactional
    public ManualViopPositionDto create(ManualViopPositionCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        validateMargin(request.getInitialMargin());
        if (request.getExpiryDate() != null && request.getExpiryDate().isBefore(LocalDate.now(TZ))) {
            log.warn("VİOP position created with past expiry: {}", request.getExpiryDate());
        }
        String symbol = ViopMarketPriceResolver.normalize(request.getSymbol());
        ManualViopPosition p = ManualViopPosition.createNew(
                user,
                symbol,
                trimOrNull(request.getDisplayName()),
                request.getViopCategory(),
                trimOrNull(request.getUnderlyingSymbol()),
                request.getDirection(),
                request.getContractCount(),
                request.getEntryPrice(),
                request.getEntryDate(),
                request.getCurrentPrice(),
                request.getContractMultiplier(),
                request.getInitialMargin(),
                request.getExpiryDate(),
                trimOrNull(request.getNote())
        );
        p = repository.save(p);
        return toDto(p, priceResolver.loadLatestPricesBySymbol(), LocalDate.now(TZ));
    }

    /**
     * {@code update} — Mevcut VIOP pozisyonunu günceller.
     */
    @Transactional
    public ManualViopPositionDto update(Long id, ManualViopPositionUpdateRequest request) {
        ManualViopPosition p = requireOwnedOpen(id);
        validateMargin(request.getInitialMargin());
        applyUpdate(p, request);
        p = repository.save(p);
        return toDto(p, priceResolver.loadLatestPricesBySymbol(), LocalDate.now(TZ));
    }

    /**
     * {@code close} — Açık VIOP pozisyonunu kapanış fiyatı ve masraflarla kapatır.
     */
    @Transactional
    public ManualViopPositionDto close(Long id, ManualViopPositionCloseRequest request) {
        ManualViopPosition p = requireOwnedOpen(id);
        ViopPositionCloseCalculator.Result calc = ViopPositionCloseCalculator.compute(
                p, request.getClosePrice(), request.getFee());
        p.setClosePrice(request.getClosePrice());
        p.setCloseDate(request.getCloseDate());
        p.setCurrentPrice(request.getClosePrice());
        p.setCloseFee(request.getFee());
        p.setCloseReason(request.getCloseReason());
        p.setRealizedPnl(calc.netPnl());
        p.setRealizedReturnPercent(calc.returnPercent());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            p.setNote(request.getNote().trim());
        }
        p.setStatus(ViopPositionStatus.CLOSED);
        p = repository.save(p);
        return toDto(p, priceResolver.loadLatestPricesBySymbol(), LocalDate.now(TZ));
    }

    /**
     * {@code delete} — VIOP pozisyonunu soft-delete (DELETED) olarak işaretler.
     */
    @Transactional
    public void delete(Long id) {
        ManualViopPosition p = requireOwned(id);
        p.setStatus(ViopPositionStatus.DELETED);
        repository.save(p);
    }

    private ManualViopPosition requireOwned(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        return repository.findByIdAndUser_Id(id, user.getId())
                .filter(p -> p.getStatus() != ViopPositionStatus.DELETED)
                .orElseThrow(() -> new ApiBusinessException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Pozisyon bulunamadı."));
    }

    private ManualViopPosition requireOwnedOpen(Long id) {
        ManualViopPosition p = requireOwned(id);
        if (p.getStatus() != ViopPositionStatus.OPEN) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Yalnızca açık pozisyon güncellenebilir.");
        }
        return p;
    }

    private void applyUpdate(ManualViopPosition p, ManualViopPositionUpdateRequest request) {
        p.setSymbol(ViopMarketPriceResolver.normalize(request.getSymbol()));
        p.setDisplayName(trimOrNull(request.getDisplayName()));
        p.setViopCategory(request.getViopCategory());
        p.setUnderlyingSymbol(trimOrNull(request.getUnderlyingSymbol()));
        p.setDirection(request.getDirection());
        p.setContractCount(request.getContractCount());
        p.setEntryPrice(request.getEntryPrice());
        p.setEntryDate(request.getEntryDate());
        p.setCurrentPrice(request.getCurrentPrice());
        p.setContractMultiplier(request.getContractMultiplier());
        p.setInitialMargin(request.getInitialMargin());
        p.setExpiryDate(request.getExpiryDate());
        p.setNote(trimOrNull(request.getNote()));
    }

    private BigDecimal resolvePrice(ManualViopPosition p, Map<String, BigDecimal> prices) {
        BigDecimal market = priceResolver.resolve(p.getSymbol(), prices);
        return market != null ? market : p.getCurrentPrice();
    }

    private ManualViopPositionDto toDto(ManualViopPosition p, Map<String, BigDecimal> prices, LocalDate today) {
        BigDecimal resolved = resolvePrice(p, prices);
        ViopFxRates fxRates = loadFxRates();
        ViopPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, resolved, today, fxRates);
        return new ManualViopPositionDto(
                p.getId(),
                p.getSymbol(),
                p.getDisplayName(),
                p.getViopCategory(),
                p.getUnderlyingSymbol(),
                p.getDirection(),
                p.getContractCount(),
                p.getEntryPrice(),
                p.getEntryDate(),
                m.effectiveCurrentPrice(),
                p.getContractMultiplier(),
                p.getInitialMargin(),
                p.getExpiryDate(),
                p.getStatus(),
                p.getClosePrice(),
                p.getCloseDate(),
                p.getCloseFee(),
                p.getCloseReason(),
                p.getStatus() == ViopPositionStatus.CLOSED ? p.getRealizedPnl() : null,
                p.getStatus() == ViopPositionStatus.CLOSED ? p.getRealizedReturnPercent() : null,
                p.getStatus() == ViopPositionStatus.OPEN ? m.unrealizedPnlTry() : p.getRealizedPnl(),
                m.riskExposureTry(),
                m.netFinancialEffect(),
                m.daysToExpiry(),
                p.getNote(),
                m.quoteCurrency().name(),
                m.riskExposureNative(),
                m.unrealizedPnlNative(),
                m.leverage(),
                m.marginRatio(),
                m.pnlToMarginRatio(),
                m.missingFxRate()
        );
    }

    private static void validateMargin(BigDecimal margin) {
        if (margin != null && margin.signum() < 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Teminat negatif olamaz.");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
