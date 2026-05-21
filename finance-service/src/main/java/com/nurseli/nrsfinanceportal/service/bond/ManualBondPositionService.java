package com.nurseli.nrsfinanceportal.service.bond;

import com.nurseli.nrsfinanceportal.common.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.common.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.dto.bond.*;
import com.nurseli.nrsfinanceportal.repository.ManualBondPositionRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
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

@Service
@RequiredArgsConstructor
public class ManualBondPositionService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int EXPIRING_SOON_DAYS = 30;

    private final ManualBondPositionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final BondPositionMetricsCalculator metricsCalculator;

    @Transactional(readOnly = true)
    public List<ManualBondPositionDto> listMine() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        LocalDate today = LocalDate.now(TZ);
        return repository.findByUser_IdAndStatusNotOrderByBuyDateDesc(user.getId(), BondPositionStatus.DELETED)
                .stream()
                .map(p -> toDto(p, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public BondPositionSummaryDto summaryMine() {
        return summaryForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    @Transactional(readOnly = true)
    public BondPositionSummaryDto summaryForUser(User user) {
        LocalDate today = LocalDate.now(TZ);
        List<ManualBondPosition> open = repository.findByUser_IdAndStatusOrderByBuyDateDesc(
                user.getId(), BondPositionStatus.OPEN);

        BigDecimal totalNominal = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal totalCoupon = BigDecimal.ZERO;
        BigDecimal returnSum = BigDecimal.ZERO;
        int returnCount = 0;
        int expiringSoon = 0;
        int incomplete = 0;
        Map<String, BigDecimal> currencyBreakdown = new HashMap<>();

        for (ManualBondPosition p : open) {
            BondPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, today);
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
            if (m.pnl() != null) {
                totalPnl = totalPnl.add(m.pnl());
            }
            if (m.returnPct() != null) {
                returnSum = returnSum.add(m.returnPct());
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

        return new BondPositionSummaryDto(
                open.size(),
                totalNominal.setScale(6, RoundingMode.HALF_UP),
                totalCurrent.setScale(6, RoundingMode.HALF_UP),
                totalPnl.setScale(6, RoundingMode.HALF_UP),
                avgReturn,
                totalCoupon.signum() > 0 ? totalCoupon.setScale(6, RoundingMode.HALF_UP) : null,
                expiringSoon,
                currencyBreakdown,
                incomplete
        );
    }

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
                request.getCouponFrequency() != null ? request.getCouponFrequency() : CouponFrequency.NONE,
                trimOrNull(request.getNote())
        );
        p = repository.save(p);
        return toDto(p, LocalDate.now(TZ));
    }

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
        p.setCouponFrequency(request.getCouponFrequency() != null ? request.getCouponFrequency() : CouponFrequency.NONE);
        p.setNote(trimOrNull(request.getNote()));
        p = repository.save(p);
        return toDto(p, LocalDate.now(TZ));
    }

    @Transactional
    public ManualBondPositionDto sell(Long id, ManualBondPositionSellRequest request) {
        ManualBondPosition p = requireOwnedOpen(id);
        p.setSellPrice(request.getSellPrice());
        p.setSellDate(request.getSellDate());
        p.setCurrentPrice(request.getSellPrice());
        p.setStatus(BondPositionStatus.SOLD);
        p = repository.save(p);
        return toDto(p, LocalDate.now(TZ));
    }

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

    private ManualBondPositionDto toDto(ManualBondPosition p, LocalDate today) {
        BondPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, today);
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
                m.buyValue(),
                m.currentValue(),
                m.pnl(),
                m.returnPct(),
                m.annualCoupon(),
                m.daysToMaturity(),
                p.getNote()
        );
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
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
