package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.api.dto.bond.*;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualBondPositionRepository;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * finance-service manuel bond pozisyon servisi — tahvil pozisyonu CRUD, özet ve EVDS destekli metrik DTO dönüşümünü yönetir.
 */
@RequiredArgsConstructor
@Service

public class ManualBondPositionService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int EXPIRING_SOON_DAYS = 30;

    private final ManualBondPositionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final BondPositionMetricsCalculator metricsCalculator;
    private final MarketDataClient marketDataClient;
    private final BondPositionCpiSupport bondPositionCpiSupport;
    private final BondPositionRealReturnCalculator bondRealReturnCalculator;

    /**
     * {@code listMine} — Kullanıcının silinmemiş tahvil pozisyonlarını metrik ve reel getiri alanlarıyla listeler.
     */
    @Transactional(readOnly = true)
    public List<ManualBondPositionDto> listMine() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        LocalDate today = LocalDate.now(TZ);
        var positions = repository.findByUser_IdAndStatusNotOrderByBuyDateDesc(user.getId(), BondPositionStatus.DELETED);
        CpiIndexLookup cpi = bondPositionCpiSupport.loadForPositions(positions);
        Map<String, MarketDataClient.DebtLatestRow> evdsByIsin = loadEvdsByIsin();
        return positions.stream()
                .map(p -> toDto(p, today, evdsByIsin.get(normalizeSymbol(p.getSymbol())), cpi))
                .toList();
    }

    /**
     * {@code summaryMine} — Giriş yapan kullanıcı için açık tahvil pozisyon özetini döner.
     */
    @Transactional(readOnly = true)
    public BondPositionSummaryDto summaryMine() {
        return summaryForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    /**
     * {@code summaryForUser} — Belirtilen kullanıcı için açık tahvil özet KPI'larını hesaplar.
     */
    @Transactional(readOnly = true)
    public BondPositionSummaryDto summaryForUser(User user) {
        LocalDate today = LocalDate.now(TZ);
        List<ManualBondPosition> open = repository.findByUser_IdAndStatusOrderByBuyDateDesc(
                user.getId(), BondPositionStatus.OPEN);

        BigDecimal totalNominal = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalPricePnl = BigDecimal.ZERO;
        BigDecimal totalCollectedCoupon = BigDecimal.ZERO;
        BigDecimal totalCoupon = BigDecimal.ZERO;
        BigDecimal returnSum = BigDecimal.ZERO;
        int returnCount = 0;
        int expiringSoon = 0;
        int incomplete = 0;
        Map<String, BigDecimal> currencyBreakdown = new HashMap<>();

        Map<String, MarketDataClient.DebtLatestRow> evdsByIsin =
                open.isEmpty() ? Map.of() : loadEvdsByIsin();
        for (ManualBondPosition p : open) {
            BondPositionMetricsCalculator.Metrics m = metricsCalculator.compute(
                    p, today, evdsByIsin.get(normalizeSymbol(p.getSymbol())));
            if (p.getNominalValue() != null) {
                totalNominal = totalNominal.add(p.getNominalValue());
            }
            if (m.currentValue() != null) {
                totalCurrent = totalCurrent.add(m.currentValue());
                String cur = p.getCurrency() != null ? p.getCurrency().toUpperCase(Locale.ROOT) : "TRY";
                currencyBreakdown.merge(cur, m.currentValue(), BigDecimal::add);
            } else {
                incomplete++;
            }
            if (m.pricePnl() != null) {
                totalPricePnl = totalPricePnl.add(m.pricePnl());
            }
            if (m.collectedCoupon() != null) {
                totalCollectedCoupon = totalCollectedCoupon.add(m.collectedCoupon());
            }
            if (m.totalReturnPercent() != null) {
                returnSum = returnSum.add(m.totalReturnPercent());
                returnCount++;
            }
            if (m.annualCoupon() != null) {
                totalCoupon = totalCoupon.add(m.annualCoupon());
            }
            if (m.daysToMaturity() != null && m.daysToMaturity() >= 0 && m.daysToMaturity() <= EXPIRING_SOON_DAYS) {
                expiringSoon++;
            }
        }

        BigDecimal avgReturn = returnCount > 0
                ? returnSum.divide(BigDecimal.valueOf(returnCount), 4, RoundingMode.HALF_UP)
                : null;

        BigDecimal totalReturn = totalPricePnl.add(totalCollectedCoupon).setScale(6, RoundingMode.HALF_UP);

        return new BondPositionSummaryDto(
                open.size(),
                totalNominal.setScale(6, RoundingMode.HALF_UP),
                totalCurrent.setScale(6, RoundingMode.HALF_UP),
                totalReturn,
                totalPricePnl.setScale(6, RoundingMode.HALF_UP),
                totalCollectedCoupon.setScale(6, RoundingMode.HALF_UP),
                avgReturn,
                totalCoupon.signum() > 0 ? totalCoupon.setScale(6, RoundingMode.HALF_UP) : null,
                expiringSoon,
                currencyBreakdown,
                incomplete
        );
    }

    /**
     * {@code create} — Yeni manuel tahvil pozisyonu oluşturur ve doğrular.
     */
    @Transactional
    public ManualBondPositionDto create(ManualBondPositionCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        validateCoupon(request.getCouponRate());
        validateCurrentPrice(request.getCurrentPrice());
        ManualBondPosition p = ManualBondPosition.createNew(
                user,
                normalizeSymbol(request.getSymbol()),
                trimOrNull(request.getDisplayName()),
                request.getBondType(),
                request.getCurrency().trim().toUpperCase(Locale.ROOT),
                request.getNominalValue(),
                request.getBuyPrice(),
                request.getBuyDate(),
                request.getCurrentPrice(),
                request.getMaturityDate(),
                request.getCouponRate(),
                resolveCouponFrequency(request.getCouponRate(), request.getCouponFrequency()),
                trimOrNull(request.getNote())
        );
        p = repository.save(p);
        CpiIndexLookup cpi = bondPositionCpiSupport.loadForPositions(List.of(p));
        return toDto(p, LocalDate.now(TZ), loadEvdsByIsin().get(normalizeSymbol(p.getSymbol())), cpi);
    }

    /**
     * {@code update} — Mevcut tahvil pozisyonunu günceller.
     */
    @Transactional
    public ManualBondPositionDto update(Long id, ManualBondPositionUpdateRequest request) {
        ManualBondPosition p = requireOwnedOpen(id);
        validateCoupon(request.getCouponRate());
        validateCurrentPrice(request.getCurrentPrice());
        p.setSymbol(normalizeSymbol(request.getSymbol()));
        p.setDisplayName(trimOrNull(request.getDisplayName()));
        p.setBondType(request.getBondType());
        p.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        p.setNominalValue(request.getNominalValue());
        p.setBuyPrice(request.getBuyPrice());
        p.setBuyDate(request.getBuyDate());
        p.setCurrentPrice(request.getCurrentPrice());
        p.setMaturityDate(request.getMaturityDate());
        p.setCouponRate(request.getCouponRate());
        p.setCouponFrequency(resolveCouponFrequency(request.getCouponRate(), request.getCouponFrequency()));
        p.setNote(trimOrNull(request.getNote()));
        p = repository.save(p);
        CpiIndexLookup cpi = bondPositionCpiSupport.loadForPositions(List.of(p));
        return toDto(p, LocalDate.now(TZ), null, cpi);
    }

    /**
     * {@code sell} — Açık tahvil pozisyonunu satış bilgileriyle kapatır.
     */
    @Transactional
    public ManualBondPositionDto sell(Long id, ManualBondPositionSellRequest request) {
        ManualBondPosition p = requireOwnedOpen(id);
        BondPositionCloseCalculator.Result calc = BondPositionCloseCalculator.compute(
                p,
                request.getSellPrice(),
                request.getCollectedCouponAmount(),
                request.getFee());
        p.setSellPrice(request.getSellPrice());
        p.setSellDate(request.getSellDate());
        p.setCurrentPrice(request.getSellPrice());
        p.setCloseType(request.getCloseType());
        p.setCloseFee(request.getFee());
        p.setCollectedCouponAmount(request.getCollectedCouponAmount());
        p.setRealizedPnl(calc.totalPnl());
        p.setRealizedReturnPercent(calc.returnPercent());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            p.setNote(request.getNote().trim());
        }
        p.setStatus(BondPositionStatus.SOLD);
        p = repository.save(p);
        CpiIndexLookup cpi = bondPositionCpiSupport.loadForPositions(List.of(p));
        return toDto(p, LocalDate.now(TZ), null, cpi);
    }

    /**
     * {@code delete} — Tahvil pozisyonunu soft-delete (DELETED) olarak işaretler.
     */
    @Transactional
    public void delete(Long id) {
        ManualBondPosition p = requireOwned(id);
        p.setStatus(BondPositionStatus.DELETED);
        repository.save(p);
    }

    private ManualBondPosition requireOwned(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        return repository.findByIdAndUser_Id(id, user.getId())
                .filter(p -> p.getStatus() != BondPositionStatus.DELETED)
                .orElseThrow(() -> new ApiBusinessException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Pozisyon bulunamadı."));
    }

    private ManualBondPosition requireOwnedOpen(Long id) {
        ManualBondPosition p = requireOwned(id);
        if (p.getStatus() != BondPositionStatus.OPEN) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Yalnızca açık pozisyon güncellenebilir.");
        }
        return p;
    }

    private Map<String, MarketDataClient.DebtLatestRow> loadEvdsByIsin() {
        try {
            return marketDataClient.getDebtLatestRows().stream()
                    .filter(r -> r != null && r.isin() != null && !r.isin().isBlank())
                    .collect(Collectors.toMap(
                            r -> r.isin().trim().toUpperCase(Locale.ROOT),
                            r -> r,
                            (a, b) -> evdsRowIsNewer(a, b) ? a : b));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static boolean evdsRowIsNewer(MarketDataClient.DebtLatestRow a, MarketDataClient.DebtLatestRow b) {
        if (a.asOf() == null) return false;
        if (b.asOf() == null) return true;
        return a.asOf().isAfter(b.asOf());
    }

    private ManualBondPositionDto toDto(
            ManualBondPosition p,
            LocalDate today,
            MarketDataClient.DebtLatestRow evds,
            CpiIndexLookup cpi) {
        BondPositionMetricsCalculator.Metrics m = p.getStatus() == BondPositionStatus.OPEN
                ? metricsCalculator.compute(p, today, evds)
                : metricsCalculator.compute(p, today);
        BigDecimal totalRet = p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedPnl() : m.totalReturn();
        BondPositionRealReturnCalculator.RealReturnFields rr = bondRealReturnCalculator.compute(
                p, today, m.buyValue(), totalRet, cpi != null ? cpi : CpiIndexLookup.empty());
        return new ManualBondPositionDto(
                p.getId(),
                p.getSymbol(),
                p.getDisplayName(),
                p.getBondType(),
                p.getCurrency(),
                p.getNominalValue(),
                p.getBuyPrice(),
                p.getBuyDate(),
                p.getCurrentPrice(),
                p.getMaturityDate(),
                p.getCouponRate(),
                p.getCouponFrequency(),
                p.getStatus(),
                p.getSellPrice(),
                p.getSellDate(),
                p.getCloseType(),
                p.getCloseFee(),
                p.getCollectedCouponAmount(),
                p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedPnl() : null,
                p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedReturnPercent() : null,
                m.buyValue(),
                m.currentValue(),
                p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedPnl() : m.pricePnl(),
                m.pricePnl(),
                p.getStatus() == BondPositionStatus.SOLD ? null : m.returnPct(),
                m.annualCoupon(),
                m.periodicCoupon(),
                m.completedCouponPeriods(),
                m.collectedCoupon(),
                m.estimatedAccruedCoupon(),
                p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedPnl() : m.totalReturn(),
                p.getStatus() == BondPositionStatus.SOLD ? p.getRealizedReturnPercent() : m.totalReturnPercent(),
                m.daysToMaturity(),
                p.getNote(),
                rr.cpiAtPurchaseMonth(),
                rr.cpiCurrentMonth(),
                rr.cpiAtPurchaseIndex(),
                rr.cpiCurrentIndex(),
                rr.periodInflation(),
                rr.periodInflationPercent(),
                rr.periodNominalReturn(),
                rr.periodNominalReturnPercent(),
                rr.periodRealReturn(),
                rr.periodRealReturnPercent(),
                rr.available()
        );
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static CouponFrequency resolveCouponFrequency(BigDecimal rate, CouponFrequency requested) {
        if (rate != null && rate.signum() > 0) {
            if (requested != null && requested != CouponFrequency.NONE) {
                return requested;
            }
            return CouponFrequency.SEMI_ANNUAL;
        }
        if (requested != null) {
            return requested;
        }
        return CouponFrequency.NONE;
    }

    private static void validateCoupon(BigDecimal rate) {
        if (rate != null && rate.signum() < 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Kupon oranı negatif olamaz.");
        }
    }

    private static void validateCurrentPrice(BigDecimal price) {
        if (price != null && price.signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Güncel fiyat sıfırdan büyük olmalı.");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
