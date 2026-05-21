package com.nurseli.nrsfinanceportal.service.viop;

import com.nurseli.nrsfinanceportal.common.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.common.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import com.nurseli.nrsfinanceportal.dto.viop.*;
import com.nurseli.nrsfinanceportal.repository.ManualViopPositionRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualViopPositionService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int EXPIRING_SOON_DAYS = 14;

    private final ManualViopPositionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final ViopMarketPriceResolver priceResolver;
    private final ViopPositionMetricsCalculator metricsCalculator;

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

    @Transactional(readOnly = true)
    public ViopPositionSummaryDto summaryMine() {
        return summaryForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    @Transactional(readOnly = true)
    public ViopPositionSummaryDto summaryForUser(User user) {
        Map<String, BigDecimal> prices = priceResolver.loadLatestPricesBySymbol();
        LocalDate today = LocalDate.now(TZ);
        List<ManualViopPosition> open = repository.findByUser_IdAndStatusOrderByEntryDateDesc(
                user.getId(), ViopPositionStatus.OPEN);

        BigDecimal totalMargin = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal totalRisk = BigDecimal.ZERO;
        int longCount = 0;
        int shortCount = 0;
        int expiringSoon = 0;
        int incomplete = 0;

        for (ManualViopPosition p : open) {
            BigDecimal market = resolvePrice(p, prices);
            ViopPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, market, today);
            if (p.getInitialMargin() != null) {
                totalMargin = totalMargin.add(p.getInitialMargin());
            }
            if (m.unrealizedPnl() != null) {
                totalPnl = totalPnl.add(m.unrealizedPnl());
            } else if (market == null && p.getCurrentPrice() == null) {
                incomplete++;
            }
            if (m.riskExposure() != null) {
                totalRisk = totalRisk.add(m.riskExposure());
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
        return new ViopPositionSummaryDto(
                open.size(),
                totalMargin.setScale(6, RoundingMode.HALF_UP),
                totalPnl.setScale(6, RoundingMode.HALF_UP),
                totalRisk.setScale(6, RoundingMode.HALF_UP),
                longCount,
                shortCount,
                expiringSoon,
                netEffect,
                incomplete
        );
    }

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

    @Transactional
    public ManualViopPositionDto update(Long id, ManualViopPositionUpdateRequest request) {
        ManualViopPosition p = requireOwnedOpen(id);
        validateMargin(request.getInitialMargin());
        applyUpdate(p, request);
        p = repository.save(p);
        return toDto(p, priceResolver.loadLatestPricesBySymbol(), LocalDate.now(TZ));
    }

    @Transactional
    public ManualViopPositionDto close(Long id, ManualViopPositionCloseRequest request) {
        ManualViopPosition p = requireOwnedOpen(id);
        p.setClosePrice(request.getClosePrice());
        p.setCloseDate(request.getCloseDate());
        p.setCurrentPrice(request.getClosePrice());
        p.setStatus(ViopPositionStatus.CLOSED);
        p = repository.save(p);
        return toDto(p, priceResolver.loadLatestPricesBySymbol(), LocalDate.now(TZ));
    }

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
        ViopPositionMetricsCalculator.Metrics m = metricsCalculator.compute(p, resolved, today);
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
                m.unrealizedPnl(),
                m.riskExposure(),
                m.netFinancialEffect(),
                m.daysToExpiry(),
                p.getNote()
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
