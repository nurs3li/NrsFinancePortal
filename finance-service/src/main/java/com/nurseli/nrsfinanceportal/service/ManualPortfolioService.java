package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCloseRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.common.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.common.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.InvestmentPositionEventPublisher;
import com.nurseli.nrsfinanceportal.repository.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.service.portfolio.HistoricalManualPriceResolverService;
import com.nurseli.nrsfinanceportal.service.portfolio.HistoricalManualPriceResolverService.ManualChartPoint;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPortfolioNominalAnalysisCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPortfolioService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final ManualPortfolioPositionRepository manualRepo;
    private final CurrentUserResolver currentUserResolver;
    private final PortfolioSnapshotRecorder portfolioSnapshotRecorder;
    private final InvestmentPositionEventPublisher investmentPositionEventPublisher;
    private final HistoricalManualPriceResolverService priceResolver;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    @Transactional(readOnly = true)
    public ManualPriceResolveDto resolvePrice(AssetType type, String symbol, LocalDate date) {
        String norm = SymbolNormalizer.normalize(type, symbol.trim().toUpperCase());
        return priceResolver.resolve(type, norm, date);
    }

    @Transactional
    public ManualPortfolioPosition create(ManualPortfolioCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        LocalDate today = LocalDate.now(TZ);
        validateBuyDate(request.getBuyDate(), today);

        ManualPositionStatus status = request.getStatus() != null ? request.getStatus() : ManualPositionStatus.OPEN;
        if (status == ManualPositionStatus.OPEN && hasSellSide(request)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.OPEN_POSITION_CANNOT_HAVE_SELL_FIELDS,
                    "Açık pozisyonda satış alanları gönderilemez.");
        }
        if (status == ManualPositionStatus.SOLD && request.getSellDate() == null) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                    "Satılmış pozisyon için satış tarihi zorunludur.");
        }
        if (status == ManualPositionStatus.SOLD && request.getSellDate() != null
                && request.getSellDate().isBefore(request.getBuyDate())) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_SELL_DATE,
                    "Satış tarihi alış tarihinden önce olamaz.");
        }
        if (status == ManualPositionStatus.SOLD && request.getSellDate() != null) {
            validateBuyDate(request.getSellDate(), today);
        }

        String normalized = SymbolNormalizer.normalize(request.getType(), request.getSymbol().trim().toUpperCase());
        BigDecimal buyFee = feeOrZero(request.getBuyFee());
        if (buyFee.signum() < 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Alış masrafı negatif olamaz.");
        }

        BuyResolved buy = resolveBuy(request.getType(), normalized, request.getBuyDate(), request.getBuyPrice(), today);

        SellResolved sell = null;
        if (status == ManualPositionStatus.SOLD) {
            BigDecimal sellFee = feeOrZero(request.getSellFee());
            if (sellFee.signum() < 0) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Satış masrafı negatif olamaz.");
            }
            sell = resolveSell(request.getType(), normalized, request.getSellDate(), request.getSellPrice(), today, sellFee);
        }

        ManualPortfolioPosition p = ManualPortfolioPosition.createNew(
                user,
                request.getType(),
                normalized,
                request.getQuantity(),
                request.getBuyDate(),
                buy.price(),
                buy.source(),
                buy.resolvedDate(),
                buy.override(),
                buyFee,
                status,
                sell != null ? sell.sellDate() : null,
                sell != null ? sell.sellPrice() : null,
                sell != null ? sell.source() : null,
                sell != null ? sell.resolvedDate() : null,
                sell != null && sell.override(),
                sell != null ? sell.sellFee() : null,
                request.getNote()
        );
        validateCore(p);

        ManualPortfolioPosition saved = manualRepo.save(p);
        recordManualSnapshot(user.getId());
        investmentPositionEventPublisher.publishCreated(saved);
        return saved;
    }

    @Transactional
    public ManualPortfolioPosition update(Long id, ManualPortfolioCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));

        if (p.getStatus() == ManualPositionStatus.SOLD && request.getStatus() == ManualPositionStatus.OPEN) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.SOLD_POSITION_CANNOT_BE_REOPENED,
                    "Satılmış pozisyon tekrar açılamaz.");
        }

        LocalDate today = LocalDate.now(TZ);
        validateBuyDate(request.getBuyDate(), today);

        ManualPositionStatus targetStatus = request.getStatus() != null ? request.getStatus() : p.getStatus();
        if (targetStatus == ManualPositionStatus.OPEN && hasSellSide(request)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.OPEN_POSITION_CANNOT_HAVE_SELL_FIELDS,
                    "Açık pozisyonda satış alanları gönderilemez.");
        }
        if (targetStatus == ManualPositionStatus.SOLD && request.getSellDate() == null && p.getStatus() == ManualPositionStatus.OPEN) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                    "Satılmış duruma geçişte satış tarihi zorunludur.");
        }
        if (targetStatus == ManualPositionStatus.SOLD) {
            LocalDate sd = request.getSellDate() != null ? request.getSellDate() : p.getSellDate();
            if (sd != null && sd.isBefore(request.getBuyDate())) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_SELL_DATE,
                        "Satış tarihi alış tarihinden önce olamaz.");
            }
            if (request.getSellDate() != null) {
                validateBuyDate(request.getSellDate(), today);
            }
        }

        String normalized = SymbolNormalizer.normalize(request.getType(), request.getSymbol().trim().toUpperCase());
        BigDecimal buyFee = feeOrZero(request.getBuyFee());
        if (buyFee.signum() < 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Alış masrafı negatif olamaz.");
        }

        BuyResolved buy = resolveBuy(request.getType(), normalized, request.getBuyDate(), request.getBuyPrice(), today);

        SellResolved sell = null;
        if (targetStatus == ManualPositionStatus.SOLD) {
            LocalDate sellDate = request.getSellDate() != null ? request.getSellDate() : p.getSellDate();
            BigDecimal sellPriceInput = request.getSellPrice() != null ? request.getSellPrice() : p.getSellPrice();
            BigDecimal sellFee = feeOrZero(request.getSellFee() != null ? request.getSellFee() : p.getSellFee());
            if (sellFee.signum() < 0) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Satış masrafı negatif olamaz.");
            }
            sell = resolveSell(request.getType(), normalized, sellDate, sellPriceInput, today, sellFee);
        }

        p.applyFullUpdate(
                request.getType(),
                normalized,
                request.getQuantity(),
                request.getBuyDate(),
                buy.price(),
                buy.source(),
                buy.resolvedDate(),
                buy.override(),
                buyFee,
                targetStatus,
                sell != null ? sell.sellDate() : null,
                sell != null ? sell.sellPrice() : null,
                sell != null ? sell.source() : null,
                sell != null ? sell.resolvedDate() : null,
                sell != null && sell.override(),
                sell != null ? sell.sellFee() : null,
                request.getNote()
        );
        validateCore(p);

        ManualPortfolioPosition saved = manualRepo.save(p);
        recordManualSnapshot(user.getId());
        investmentPositionEventPublisher.publishUpdated(saved);
        return saved;
    }

    @Transactional
    public ManualPortfolioPosition close(Long id, ManualPortfolioCloseRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));
        if (p.getStatus() == ManualPositionStatus.SOLD) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.POSITION_ALREADY_SOLD,
                    "Pozisyon zaten satılmış.");
        }
        LocalDate today = LocalDate.now(TZ);
        validateBuyDate(request.getSellDate(), today);
        if (request.getSellDate().isBefore(p.getBuyDate())) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_SELL_DATE,
                    "Satış tarihi alış tarihinden önce olamaz.");
        }
        BigDecimal sellFee = feeOrZero(request.getSellFee());
        if (sellFee.signum() < 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Satış masrafı negatif olamaz.");
        }
        SellResolved sell = resolveSell(p.getType(), p.getSymbol(), request.getSellDate(), request.getSellPrice(), today, sellFee);
        p.closeAsSold(
                sell.sellDate(),
                sell.sellPrice(),
                sell.source(),
                sell.resolvedDate(),
                sell.override(),
                sell.sellFee()
        );
        validateCore(p);
        ManualPortfolioPosition saved = manualRepo.save(p);
        recordManualSnapshot(user.getId());
        investmentPositionEventPublisher.publishClosed(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));
        investmentPositionEventPublisher.publishClosed(p);
        manualRepo.delete(p);
        recordManualSnapshot(user.getId());
    }

    @Transactional(readOnly = true)
    public List<ManualPortfolioPosition> listMine() {
        Long userId = currentUserResolver.getCurrentUserId();
        return manualRepo.findByUserIdOrderByBuyDateAsc(userId);
    }

    @Transactional(readOnly = true)
    public ManualPortfolioSummaryView summaryMine() {
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> all = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        int total = all.size();
        long openC = all.stream().filter(p -> p.getStatus() == ManualPositionStatus.OPEN).count();
        long soldC = all.stream().filter(p -> p.getStatus() == ManualPositionStatus.SOLD).count();

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentOpenValue = BigDecimal.ZERO;
        BigDecimal realizedProfit = BigDecimal.ZERO;
        BigDecimal unrealizedProfit = BigDecimal.ZERO;
        BigDecimal holdValueTodayForSold = BigDecimal.ZERO;
        BigDecimal missedProfit = BigDecimal.ZERO;

        ManualPortfolioPosition best = null;
        BigDecimal bestReturnPct = null;
        ManualPortfolioPosition biggestMiss = null;
        BigDecimal biggestMissAmt = null;

        for (ManualPortfolioPosition p : all) {
            ManualPortfolioNominalAnalysis a = nominalAnalysisCalculator.compute(p);
            totalInvested = totalInvested.add(nz(a.buyCost()));
            if (p.getStatus() == ManualPositionStatus.OPEN) {
                if (a.currentValue() != null) {
                    currentOpenValue = currentOpenValue.add(a.currentValue());
                }
                if (a.unrealizedProfit() != null) {
                    unrealizedProfit = unrealizedProfit.add(a.unrealizedProfit());
                }
            } else {
                if (a.realizedProfit() != null) {
                    realizedProfit = realizedProfit.add(a.realizedProfit());
                }
                if (a.holdValueToday() != null) {
                    holdValueTodayForSold = holdValueTodayForSold.add(a.holdValueToday());
                }
                if (a.missedProfit() != null) {
                    missedProfit = missedProfit.add(a.missedProfit());
                    if (biggestMissAmt == null || a.missedProfit().compareTo(biggestMissAmt) > 0
                            || (a.missedProfit().compareTo(biggestMissAmt) == 0
                            && (biggestMiss == null || tieBreak(p, biggestMiss) < 0))) {
                        biggestMissAmt = a.missedProfit();
                        biggestMiss = p;
                    }
                }
            }
            BigDecimal ret = a.totalReturnPct();
            if (ret != null) {
                if (bestReturnPct == null || ret.compareTo(bestReturnPct) > 0
                        || (ret.compareTo(bestReturnPct) == 0 && (best == null || tieBreak(p, best) < 0))) {
                    bestReturnPct = ret;
                    best = p;
                }
            }
        }

        BigDecimal totalNominalProfit = realizedProfit.add(unrealizedProfit);
        BigDecimal totalNominalReturnPct = pct(totalNominalProfit, totalInvested);

        return new ManualPortfolioSummaryView(
                total,
                (int) openC,
                (int) soldC,
                totalInvested,
                currentOpenValue,
                realizedProfit,
                unrealizedProfit,
                holdValueTodayForSold,
                missedProfit,
                totalNominalProfit,
                totalNominalReturnPct,
                best != null ? best.getSymbol() : null,
                bestReturnPct,
                biggestMiss != null ? biggestMiss.getSymbol() : null,
                biggestMissAmt
        );
    }

    private static int tieBreak(ManualPortfolioPosition a, ManualPortfolioPosition b) {
        int c = a.getSymbol().compareToIgnoreCase(b.getSymbol());
        if (c != 0) {
            return c;
        }
        return Long.compare(
                a.getId() != null ? a.getId() : 0L,
                b.getId() != null ? b.getId() : 0L
        );
    }

    @Transactional(readOnly = true)
    public ManualPortfolioAnalysisResponse analysis(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));

        ManualPortfolioNominalAnalysis a = nominalAnalysisCalculator.compute(p);
        ManualPortfolioView view = ManualPortfolioView.from(p, a);

        LocalDate today = LocalDate.now(TZ);
        List<ManualChartPoint> series = priceResolver.loadDailyCloseSeriesTry(p.getType(), p.getSymbol(), p.getBuyDate(), today);
        List<ManualPortfolioAnalysisResponse.ChartPoint> chart = new ArrayList<>();
        for (ManualChartPoint pt : series) {
            BigDecimal val = pt.priceTry() != null
                    ? pt.priceTry().multiply(p.getQuantity()).setScale(8, RoundingMode.HALF_UP)
                    : null;
            chart.add(new ManualPortfolioAnalysisResponse.ChartPoint(pt.date(), pt.priceTry(), val));
        }

        List<ManualPortfolioAnalysisResponse.Marker> markers = new ArrayList<>();
        markers.add(new ManualPortfolioAnalysisResponse.Marker(
                "BUY", p.getBuyDate(), p.getBuyPrice(),
                p.getBuyPrice().multiply(p.getQuantity()).setScale(8, RoundingMode.HALF_UP)));
        if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null && p.getSellPrice() != null) {
            markers.add(new ManualPortfolioAnalysisResponse.Marker(
                    "SELL", p.getSellDate(), p.getSellPrice(),
                    p.getSellPrice().multiply(p.getQuantity()).setScale(8, RoundingMode.HALF_UP)));
        }
        if (a.currentPrice() != null && a.currentPrice().signum() > 0) {
            markers.add(new ManualPortfolioAnalysisResponse.Marker(
                    "TODAY", today, a.currentPrice(),
                    a.currentPrice().multiply(p.getQuantity()).setScale(8, RoundingMode.HALF_UP)));
        }
        markers.sort(Comparator.comparing(ManualPortfolioAnalysisResponse.Marker::getDate));
        return new ManualPortfolioAnalysisResponse(view, markers, chart);
    }

    private record BuyResolved(BigDecimal price, ManualPriceSource source, LocalDate resolvedDate, boolean override) {}

    private record SellResolved(
            LocalDate sellDate,
            BigDecimal sellPrice,
            ManualPriceSource source,
            LocalDate resolvedDate,
            boolean override,
            BigDecimal sellFee
    ) {}

    private BuyResolved resolveBuy(AssetType type, String symbol, LocalDate buyDate, BigDecimal buyPriceOrNull, LocalDate today) {
        if (buyPriceOrNull != null) {
            if (buyPriceOrNull.signum() <= 0) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Alış fiyatı pozitif olmalıdır.");
            }
            return new BuyResolved(buyPriceOrNull, ManualPriceSource.USER_INPUT, buyDate, true);
        }
        ManualPriceResolveDto r = priceResolver.resolve(type, symbol, buyDate);
        if (!r.isFound() || r.getPrice() == null || r.getPrice().signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BUY_PRICE_NOT_FOUND,
                    "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
        }
        ManualPriceSource src = ManualPriceSource.valueOf(r.getSource());
        return new BuyResolved(r.getPrice(), src, r.getResolvedDate(), false);
    }

    private SellResolved resolveSell(
            AssetType type,
            String symbol,
            LocalDate sellDate,
            BigDecimal sellPriceOrNull,
            LocalDate today,
            BigDecimal sellFee
    ) {
        if (sellPriceOrNull != null) {
            if (sellPriceOrNull.signum() <= 0) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Satış fiyatı pozitif olmalıdır.");
            }
            return new SellResolved(sellDate, sellPriceOrNull, ManualPriceSource.USER_INPUT, sellDate, true, sellFee);
        }
        ManualPriceResolveDto r = priceResolver.resolve(type, symbol, sellDate);
        if (!r.isFound() || r.getPrice() == null || r.getPrice().signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.SELL_PRICE_NOT_FOUND,
                    "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
        }
        ManualPriceSource src = ManualPriceSource.valueOf(r.getSource());
        return new SellResolved(sellDate, r.getPrice(), src, r.getResolvedDate(), false, sellFee);
    }

    private static boolean hasSellSide(ManualPortfolioCreateRequest r) {
        return r.getSellDate() != null || r.getSellPrice() != null || r.getSellFee() != null;
    }

    private static void validateBuyDate(LocalDate buyDate, LocalDate today) {
        if (buyDate == null) {
            return;
        }
        if (buyDate.isAfter(today)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PRICE_DATE,
                    "Gelecek tarih kullanılamaz.");
        }
    }

    private static void validateCore(ManualPortfolioPosition p) {
        if (p.getQuantity() == null || p.getQuantity().signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Miktar pozitif olmalıdır.");
        }
        if (p.getBuyPrice() == null || p.getBuyPrice().signum() <= 0) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Alış fiyatı pozitif olmalıdır.");
        }
        if (p.getStatus() == ManualPositionStatus.SOLD) {
            if (p.getSellDate() == null || p.getSellPrice() == null || p.getSellPrice().signum() <= 0) {
                throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                        "Satılmış pozisyon için satış tarihi ve fiyatı zorunludur.");
            }
        }
    }

    private static BigDecimal feeOrZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(BigDecimal num, BigDecimal den) {
        if (num == null || den == null || den.signum() <= 0) {
            return null;
        }
        return num.divide(den, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(6, RoundingMode.HALF_UP);
    }

    private void recordManualSnapshot(Long userId) {
        try {
            portfolioSnapshotRecorder.record(userId, SnapshotTriggerType.MANUAL);
        } catch (Exception e) {
            log.warn("[PORTFOLIO_SNAPSHOT] manual snapshot failed user={}: {}", userId, e.getMessage());
        }
    }
}
