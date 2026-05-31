package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioAnalysisResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioCloseRequest;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalManualPriceResolverService;
import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalManualPriceResolverService.ManualChartPoint;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioCpiSupport;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioNominalAnalysisCalculator;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioReadCache;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioRealReturnTimeseriesBuilder;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioViewAssembler;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioPriceTreeLoader;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioWarmupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * finance-service manuel portfolio servisi — manuel pozisyon CRUD, fiyat çözümleme, nominal/reel timeseries ve özet/analiz uçlarını yönetir.
 */
@Slf4j
@Service
public class ManualPortfolioService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int MANUAL_TS_MAX_POINTS = 400;

    private final ManualPortfolioPositionRepository manualRepo;
    private final CurrentUserResolver currentUserResolver;
    private final PortfolioSnapshotRecorder portfolioSnapshotRecorder;
    private final HistoricalManualPriceResolverService priceResolver;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;
    private final ManualPortfolioViewAssembler manualPortfolioViewAssembler;
    private final ManualPortfolioReadCache manualPortfolioReadCache;
    private final ManualPortfolioCpiSupport cpiSupport;
    private final ManualPortfolioRealReturnTimeseriesBuilder realReturnTimeseriesBuilder;
    private final ManualPortfolioWarmupService manualPortfolioWarmupService;
    private final ManualPortfolioPriceTreeLoader priceTreeLoader;

    public ManualPortfolioService(
            ManualPortfolioPositionRepository manualRepo,
            CurrentUserResolver currentUserResolver,
            PortfolioSnapshotRecorder portfolioSnapshotRecorder,
            HistoricalManualPriceResolverService priceResolver,
            ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator,
            ManualPortfolioViewAssembler manualPortfolioViewAssembler,
            ManualPortfolioReadCache manualPortfolioReadCache,
            ManualPortfolioCpiSupport cpiSupport,
            ManualPortfolioRealReturnTimeseriesBuilder realReturnTimeseriesBuilder,
            @Lazy ManualPortfolioWarmupService manualPortfolioWarmupService,
            ManualPortfolioPriceTreeLoader priceTreeLoader
    ) {
        this.manualRepo = manualRepo;
        this.currentUserResolver = currentUserResolver;
        this.portfolioSnapshotRecorder = portfolioSnapshotRecorder;
        this.priceResolver = priceResolver;
        this.nominalAnalysisCalculator = nominalAnalysisCalculator;
        this.manualPortfolioViewAssembler = manualPortfolioViewAssembler;
        this.manualPortfolioReadCache = manualPortfolioReadCache;
        this.cpiSupport = cpiSupport;
        this.realReturnTimeseriesBuilder = realReturnTimeseriesBuilder;
        this.manualPortfolioWarmupService = manualPortfolioWarmupService;
        this.priceTreeLoader = priceTreeLoader;
    }

    public record ReadBundle(
            List<ManualPortfolioPosition> positions,
            ManualPortfolioSummaryView summary,
            List<ManualPortfolioView> views
    ) {}

    /**
     * {@code resolvePrice} — Belirtilen varlık türü, sembol ve tarih için tarihsel fiyat çözümlemesi yapar.
     */
    @Transactional(readOnly = true)
    public ManualPriceResolveDto resolvePrice(AssetType type, String symbol, LocalDate date) {
        String norm = SymbolNormalizer.normalize(type, symbol.trim().toUpperCase());
        return priceResolver.resolve(type, norm, date);
    }

    /**
     * {@code create} — Yeni manuel portfolio pozisyonu oluşturur, alış/satış fiyatlarını çözer ve snapshot kaydeder.
     */
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
        onPositionChanged(user.getId(), saved.getId(), com.nurseli.nrsfinanceportal.application.portfolio.materialized.WarmupTrigger.ADD);
        return saved;
    }

    /**
     * {@code update} — Mevcut pozisyonu günceller; satılmış pozisyonu yeniden açmaya izin vermez.
     */
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
        onPositionChanged(user.getId());
        return saved;
    }

    /**
     * {@code close} — Açık pozisyonu satış bilgileriyle SOLD durumuna kapatır.
     */
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
        onPositionChanged(user.getId());
        return saved;
    }

    /**
     * {@code delete} — Kullanıcının pozisyonunu siler ve snapshot tetikler.
     */
    @Transactional
    public void delete(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));
        manualRepo.delete(p);
        onPositionChanged(user.getId());
    }

    /**
     * {@code listMine} — Giriş yapan kullanıcının tüm manuel pozisyonlarını alış tarihine göre listeler.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioPosition> listMine() {
        return getReadBundle().positions();
    }

    /**
     * {@code listViewsMine} — Önbellekli pozisyon görünümleri (tek DB + tek market-data yükü).
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioView> listViewsMine() {
        return getReadBundle().views();
    }

    private ManualPortfolioReadCache.ReadBundle getReadBundle() {
        Long userId = currentUserResolver.getCurrentUserId();
        return manualPortfolioReadCache.getOrLoad(userId, () -> {
            ReadBundle bundle = buildReadBundleInternal(userId);
            return new ManualPortfolioReadCache.ReadBundle(bundle.positions(), bundle.summary(), bundle.views());
        });
    }

    private ManualPortfolioReadCache.ReadBundle buildReadBundle(Long userId) {
        ReadBundle bundle = buildReadBundleInternal(userId);
        return new ManualPortfolioReadCache.ReadBundle(bundle.positions(), bundle.summary(), bundle.views());
    }

    private void onPositionChanged(Long userId) {
        onPositionChanged(userId, null, com.nurseli.nrsfinanceportal.application.portfolio.materialized.WarmupTrigger.FULL);
    }

    private void onPositionChanged(
            Long userId,
            Long positionId,
            com.nurseli.nrsfinanceportal.application.portfolio.materialized.WarmupTrigger trigger
    ) {
        invalidateReadCache(userId);
        manualPortfolioWarmupService.scheduleWarmup(userId, trigger, positionId);
        scheduleManualSnapshot(userId);
    }

    private void invalidateReadCache(Long userId) {
        manualPortfolioReadCache.invalidate(userId);
    }

    /**
     * Seçilen tarih aralığında kümülatif açık maliyet tabanı + günlük kapanışlardan (mümkünse) portföy piyasa değeri.
     * Anlık tablo kaydı gerektirmez; pozisyon ve HistoricalManualPriceResolverService verisine dayanır.
     */
    /**
     * {@code timeseriesMine} — Tüm pozisyonlar için kümülatif maliyet ve günlük piyasa değeri timeseries üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMine(LocalDate from, LocalDate to) {
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> posList = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        return buildTimeseriesUsingDbPrices(posList, posList, from, to, userId);
    }

    /**
     * Günlük reel K/Z: açık pozisyonların piyasa değeri − TÜFE ile taşınmış maliyet (nominal K/Z grafiği ile aynı mantık).
     */
    /**
     * {@code timeseriesMineRealReturn} — Açık pozisyonlar için TÜFE ile taşınmış maliyete göre günlük reel K/Z timeseries üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineRealReturn(LocalDate from, LocalDate to) {
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> posList = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        return buildManualRealReturnTimeseries(posList, posList, from, to);
    }

    /**
     * {@code timeseriesMineRealReturnSegment} — TYPE veya SYMBOL segmentine göre reel getiri timeseries döner.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineRealReturnSegment(
            LocalDate from,
            LocalDate to,
            String mode,
            String key
    ) {
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> posList = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        if (posList.isEmpty()) {
            return List.of();
        }
        List<ManualPortfolioPosition> segment = filterTimeseriesSegment(posList, mode, key);
        if (segment.isEmpty()) {
            return List.of();
        }
        return buildManualRealReturnTimeseries(posList, segment, from, to);
    }

    private List<ManualPortfolioTimeseriesPointDto> buildManualRealReturnTimeseries(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to
    ) {
        CpiIndexLookup cpi = cpiSupport.loadForPositions(axisScope);
        return realReturnTimeseriesBuilder.build(axisScope, valueScope, cpi, from, to);
    }

    /**
     * {@code timeseriesMineSegment} — TYPE veya SYMBOL segmentine göre nominal timeseries döner.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineSegment(LocalDate from, LocalDate to, String mode, String key) {
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> posList = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        if (posList.isEmpty()) {
            return List.of();
        }
        List<ManualPortfolioPosition> segment = filterTimeseriesSegment(posList, mode, key);
        if (segment.isEmpty()) {
            return List.of();
        }
        return buildManualTimeseries(posList, segment, from, to, userId, true, true);
    }

    /**
     * Yalnızca açık pozisyonlar: günlük gerçekleşmemiş nominal K/Z (piyasa değeri − açık maliyet).
     * Gerçekleşmemiş K/Z KPI grafiği için.
     */
    /**
     * {@code timeseriesMineOpenUnrealizedPnl} — Yalnızca açık pozisyonların günlük gerçekleşmemiş nominal K/Z serisini üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineOpenUnrealizedPnl(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> open = listOpenPositions(manualRepo.findByUserIdOrderByBuyDateAsc(userId));
        if (open.isEmpty()) {
            return List.of();
        }
        return buildManualTimeseries(open, open, from, to, userId, true, true);
    }

    /**
     * {@link #timeseriesMineOpenUnrealizedPnl} ile aynı tarih ızgarası; seçilen tür veya sembol (açık pozisyonlar).
     */
    /**
     * {@code timeseriesMineOpenUnrealizedPnlSegment} — Segment filtreli açık pozisyon gerçekleşmemiş K/Z timeseries üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineOpenUnrealizedPnlSegment(
            LocalDate from,
            LocalDate to,
            String mode,
            String key
    ) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> open = listOpenPositions(manualRepo.findByUserIdOrderByBuyDateAsc(userId));
        if (open.isEmpty()) {
            return List.of();
        }
        List<ManualPortfolioPosition> segment = filterTimeseriesSegment(open, mode, key);
        if (segment.isEmpty()) {
            return List.of();
        }
        return buildManualTimeseries(open, segment, from, to, userId, true, true);
    }

    private static List<ManualPortfolioPosition> listOpenPositions(List<ManualPortfolioPosition> all) {
        List<ManualPortfolioPosition> open = new ArrayList<>();
        for (ManualPortfolioPosition p : all) {
            if (p.getStatus() != ManualPositionStatus.SOLD) {
                open.add(p);
            }
        }
        return open;
    }

    private static List<ManualPortfolioPosition> filterTimeseriesSegment(
            List<ManualPortfolioPosition> all,
            String mode,
            String key
    ) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if ("TYPE".equals(m)) {
            String k = key.trim().toUpperCase(Locale.ROOT);
            List<ManualPortfolioPosition> out = new ArrayList<>();
            for (ManualPortfolioPosition p : all) {
                if (p.getType() != null && p.getType().name().equalsIgnoreCase(k)) {
                    out.add(p);
                }
            }
            return out;
        }
        if ("SYMBOL".equals(m)) {
            String k = key.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            List<ManualPortfolioPosition> out = new ArrayList<>();
            for (ManualPortfolioPosition p : all) {
                if (p.getSymbol() == null || p.getSymbol().isBlank() || p.getType() == null) {
                    continue;
                }
                String normalized = SymbolNormalizer.normalize(p.getType(), p.getSymbol().trim().toUpperCase(Locale.ROOT));
                if (normalized.equalsIgnoreCase(k)) {
                    out.add(p);
                    continue;
                }
                String raw = p.getSymbol().trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
                if (raw.equalsIgnoreCase(k)) {
                    out.add(p);
                }
            }
            return out;
        }
        throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Geçersiz segment modu. TYPE veya SYMBOL kullanın.");
    }

    /**
     * @param axisScope   tarih ızgarası (step) ve effectiveFrom için tüm pozisyonlar (ana portföy)
     * @param valueScope  maliyet olayları, fiyat ağaçları ve piyasa değeri bu alt kümeden hesaplanır
     */
    /**
     * {@code buildManualTimeseries} — Maliyet olayları ve günlük kapanış fiyat ağaçlarından örneklenmiş portfolio timeseries noktaları oluşturur.
     */
    private List<ManualPortfolioTimeseriesPointDto> buildManualTimeseries(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to,
            Long userId,
            boolean preferDbPrices,
            boolean allowMdsFallback
    ) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        if (axisScope.isEmpty() || valueScope.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to.isAfter(today) ? today : to;
        LocalDate earliestBuy = axisScope.stream()
                .map(ManualPortfolioPosition::getBuyDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(end);
        LocalDate effectiveFrom = from.isBefore(earliestBuy) ? earliestBuy : from;
        if (effectiveFrom.isAfter(end)) {
            return List.of();
        }

        record CostEv(LocalDate d, int phase, BigDecimal delta) {}
        List<CostEv> cev = new ArrayList<>(valueScope.size() * 2);
        for (ManualPortfolioPosition p : valueScope) {
            if (p.getBuyDate() == null) {
                log.warn("[MANUAL_TS] position id={} skipped (buyDate null)", p.getId());
                continue;
            }
            BigDecimal buyCost = buyCostBasisFromPosition(p);
            cev.add(new CostEv(p.getBuyDate(), 0, buyCost));
            if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
                cev.add(new CostEv(p.getSellDate(), 1, buyCost.negate()));
            }
        }
        cev.sort(Comparator.comparing(CostEv::d).thenComparing(CostEv::phase));

        record SymKey(AssetType type, String symbol) {}
        Set<SymKey> keys = new HashSet<>();
        for (ManualPortfolioPosition p : valueScope) {
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (overlapsTimeseriesWindow(p, effectiveFrom, end)) {
                keys.add(new SymKey(p.getType(), p.getSymbol().trim()));
            }
        }
        LocalDate histFrom = effectiveFrom.minusDays(14);
        Set<ManualPortfolioPriceTreeLoader.SymbolKey> loaderKeys = keys.stream()
                .map(k -> new ManualPortfolioPriceTreeLoader.SymbolKey(k.type(), k.symbol()))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees =
                loadPriceTreesForKeys(userId, loaderKeys, histFrom, end, preferDbPrices, true);

        long spanDays = ChronoUnit.DAYS.between(effectiveFrom, end) + 1;
        int step = (int) Math.max(1, Math.ceil(spanDays / (double) MANUAL_TS_MAX_POINTS));

        int evi = 0;
        BigDecimal costRun = BigDecimal.ZERO;
        while (evi < cev.size() && cev.get(evi).d().isBefore(effectiveFrom)) {
            costRun = costRun.add(cev.get(evi).delta());
            evi++;
        }

        List<ManualPortfolioTimeseriesPointDto> out = new ArrayList<>();
        LocalDate d = effectiveFrom;
        while (!d.isAfter(end)) {
            while (evi < cev.size() && !cev.get(evi).d().isAfter(d)) {
                costRun = costRun.add(cev.get(evi).delta());
                evi++;
            }
            BigDecimal mval = portfolioMarketValueTry(valueScope, priceTrees, d);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    d,
                    costRun.setScale(8, RoundingMode.HALF_UP),
                    mval != null ? mval.setScale(8, RoundingMode.HALF_UP) : null
            ));
            d = d.plusDays(step);
        }

        LocalDate lastSample = out.isEmpty() ? null : out.get(out.size() - 1).date();
        if (lastSample != null && lastSample.isBefore(end)) {
            while (evi < cev.size() && !cev.get(evi).d().isAfter(end)) {
                costRun = costRun.add(cev.get(evi).delta());
                evi++;
            }
            BigDecimal mval = portfolioMarketValueTry(valueScope, priceTrees, end);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    end,
                    costRun.setScale(8, RoundingMode.HALF_UP),
                    mval != null ? mval.setScale(8, RoundingMode.HALF_UP) : null
            ));
        }
        return out;
    }

    /**
     * Satılmış pozisyonlar için: satış tarihinden itibaren satılan miktarla tutulsaydı günlük TRY piyasa değeri toplamı
     * (tut ve gör senaryosu). Kaçırılan fırsat / satış sonrası değişim grafiği için.
     */
    /**
     * {@code timeseriesMineSoldHoldHypothetical} — Satılmış pozisyonlar için tutulsaydı günlük TRY piyasa değeri hipotez serisini üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineSoldHoldHypothetical(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> sold = listSoldWithSellDate(userId);
        if (sold.isEmpty()) {
            return List.of();
        }
        return buildSoldHoldHypotheticalTimeseries(sold, sold, from, to, userId);
    }

    /**
     * {@link #timeseriesMineSoldHoldHypothetical} ile aynı tarih ızgarası; yalnızca seçilen tür veya sembole ait
     * satılmış pozisyonların tutulsaydı toplamı (grafik üstüne karşılaştırma çizgisi).
     */
    /**
     * {@code timeseriesMineSoldHoldHypotheticalSegment} — Segment filtreli satılmış pozisyon tutulsaydı serisini üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineSoldHoldHypotheticalSegment(
            LocalDate from,
            LocalDate to,
            String mode,
            String key
    ) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> sold = listSoldWithSellDate(userId);
        if (sold.isEmpty()) {
            return List.of();
        }
        List<ManualPortfolioPosition> segment = filterTimeseriesSegment(sold, mode, key);
        if (segment.isEmpty()) {
            return List.of();
        }
        return buildSoldHoldHypotheticalTimeseries(sold, segment, from, to, userId);
    }

    /**
     * Satılmış pozisyonlar için birleşik seri: alım gününden satış gününe kadar (dahil) günlük nominal K/Z
     * (kapanış × miktar − alış maliyeti); satıştan sonraki günlerde aynı satılan miktar için günlük piyasa değeri
     * (kapanış × miktar, TRY). Gerçekleşmiş K/Z kartı grafiği için.
     */
    /**
     * {@code timeseriesMineSoldLifecyclePnl} — Satılmış pozisyonlar için alımdan satışa K/Z, sonrasında tutulsaydı değer birleşik serisini üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineSoldLifecyclePnl(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> sold = listSoldWithSellDate(userId);
        if (sold.isEmpty()) {
            return List.of();
        }
        return buildSoldLifecyclePnlTimeseries(sold, sold, from, to, userId);
    }

    /**
     * {@link #timeseriesMineSoldLifecyclePnl} ile aynı tarih ızgarası; yalnızca seçilen tür veya sembole ait
     * satılmış pozisyonların günlük serisi (K/Z satışa kadar, sonrasında tutulsaydı TRY değeri).
     */
    /**
     * {@code timeseriesMineSoldLifecyclePnlSegment} — Segment filtreli satılmış pozisyon yaşam döngüsü K/Z serisini üretir.
     */
    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> timeseriesMineSoldLifecyclePnlSegment(
            LocalDate from,
            LocalDate to,
            String mode,
            String key
    ) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Long userId = currentUserResolver.getCurrentUserId();
        List<ManualPortfolioPosition> sold = listSoldWithSellDate(userId);
        if (sold.isEmpty()) {
            return List.of();
        }
        List<ManualPortfolioPosition> segment = filterTimeseriesSegment(sold, mode, key);
        if (segment.isEmpty()) {
            return List.of();
        }
        return buildSoldLifecyclePnlTimeseries(sold, segment, from, to, userId);
    }

    private List<ManualPortfolioPosition> listSoldWithSellDate(Long userId) {
        List<ManualPortfolioPosition> all = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        List<ManualPortfolioPosition> sold = new ArrayList<>();
        for (ManualPortfolioPosition p : all) {
            if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
                sold.add(p);
            }
        }
        return sold;
    }

    /**
     * @param axisSold  Tarih penceresi (min satış) için tüm satılmış pozisyonlar — ana grafik ile aynı örneklem
     * @param sumSold   Değer toplamı ve fiyat anahtarları bu alt kümeden
     */
    /**
     * {@code buildSoldHoldHypotheticalTimeseries} — Satış tarihinden itibaren satılan miktarın günlük hipotetik TRY değer serisini hesaplar.
     */
    private List<ManualPortfolioTimeseriesPointDto> buildSoldHoldHypotheticalTimeseries(
            List<ManualPortfolioPosition> axisSold,
            List<ManualPortfolioPosition> sumSold,
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        if (axisSold.isEmpty() || sumSold.isEmpty()) {
            return List.of();
        }
        LocalDate minSell = axisSold.stream()
                .map(ManualPortfolioPosition::getSellDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (minSell == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to.isAfter(today) ? today : to;
        LocalDate effectiveFrom = from.isBefore(minSell) ? minSell : from;
        if (effectiveFrom.isAfter(end)) {
            return List.of();
        }

        record SymKey(AssetType type, String symbol) {}
        Set<SymKey> keys = new HashSet<>();
        for (ManualPortfolioPosition p : sumSold) {
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (soldPositionNeedsPriceSeries(p, end)) {
                keys.add(new SymKey(p.getType(), p.getSymbol().trim()));
            }
        }
        LocalDate histFrom = effectiveFrom.minusDays(14);
        Set<ManualPortfolioPriceTreeLoader.SymbolKey> loaderKeys = keys.stream()
                .map(k -> new ManualPortfolioPriceTreeLoader.SymbolKey(k.type(), k.symbol()))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees =
                loadPriceTreesForKeys(userId, loaderKeys, histFrom, end, true, true);

        long spanDays = ChronoUnit.DAYS.between(effectiveFrom, end) + 1;
        int step = (int) Math.max(1, Math.ceil(spanDays / (double) MANUAL_TS_MAX_POINTS));

        BigDecimal zeroCost = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        List<ManualPortfolioTimeseriesPointDto> out = new ArrayList<>();
        LocalDate d = effectiveFrom;
        while (!d.isAfter(end)) {
            BigDecimal mval = soldHypotheticalHoldValueTry(sumSold, priceTrees, d);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    d,
                    zeroCost,
                    mval != null ? mval.setScale(8, RoundingMode.HALF_UP) : null
            ));
            d = d.plusDays(step);
        }

        LocalDate lastSample = out.isEmpty() ? null : out.get(out.size() - 1).date();
        if (lastSample != null && lastSample.isBefore(end)) {
            BigDecimal mval = soldHypotheticalHoldValueTry(sumSold, priceTrees, end);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    end,
                    zeroCost,
                    mval != null ? mval.setScale(8, RoundingMode.HALF_UP) : null
            ));
        }
        return out;
    }

    /**
     * @param axisSold tüm satılmışlar — tarih aralığı (min alım … bugün) için
     * @param sumSold  günlük değer bu alt kümeden (satışa kadar K/Z, sonra tutulsaydı TRY değeri)
     */
    /**
     * {@code buildSoldLifecyclePnlTimeseries} — Satış öncesi nominal K/Z ve satış sonrası tutulsaydı TRY değerini birleşik günlük seride üretir.
     */
    private List<ManualPortfolioTimeseriesPointDto> buildSoldLifecyclePnlTimeseries(
            List<ManualPortfolioPosition> axisSold,
            List<ManualPortfolioPosition> sumSold,
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        if (axisSold.isEmpty() || sumSold.isEmpty()) {
            return List.of();
        }
        LocalDate minBuy = axisSold.stream()
                .map(ManualPortfolioPosition::getBuyDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (minBuy == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to;
        if (end.isAfter(today)) {
            end = today;
        }
        LocalDate effectiveFrom = from.isBefore(minBuy) ? minBuy : from;
        if (effectiveFrom.isAfter(end)) {
            return List.of();
        }

        record SymKey(AssetType type, String symbol) {}
        Set<SymKey> keys = new HashSet<>();
        for (ManualPortfolioPosition p : sumSold) {
            if (p.getBuyDate() == null || p.getSellDate() == null) {
                continue;
            }
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (p.getBuyDate().isAfter(end)) {
                continue;
            }
            keys.add(new SymKey(p.getType(), p.getSymbol().trim()));
        }
        LocalDate histFrom = effectiveFrom.minusDays(14);
        Set<ManualPortfolioPriceTreeLoader.SymbolKey> loaderKeys = keys.stream()
                .map(k -> new ManualPortfolioPriceTreeLoader.SymbolKey(k.type(), k.symbol()))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees =
                loadPriceTreesForKeys(userId, loaderKeys, histFrom, end, true, true);

        long spanDays = ChronoUnit.DAYS.between(effectiveFrom, end) + 1;
        int step = (int) Math.max(1, Math.ceil(spanDays / (double) MANUAL_TS_MAX_POINTS));

        BigDecimal zeroCost = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        List<ManualPortfolioTimeseriesPointDto> out = new ArrayList<>();
        LocalDate d = effectiveFrom;
        while (!d.isAfter(end)) {
            BigDecimal v = soldLifecyclePnlThenHypotheticalHoldValueDailyTry(sumSold, priceTrees, d);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    d,
                    zeroCost,
                    v != null ? v.setScale(8, RoundingMode.HALF_UP) : null
            ));
            d = d.plusDays(step);
        }

        LocalDate lastSample = out.isEmpty() ? null : out.get(out.size() - 1).date();
        if (lastSample != null && lastSample.isBefore(end)) {
            BigDecimal v = soldLifecyclePnlThenHypotheticalHoldValueDailyTry(sumSold, priceTrees, end);
            out.add(new ManualPortfolioTimeseriesPointDto(
                    end,
                    zeroCost,
                    v != null ? v.setScale(8, RoundingMode.HALF_UP) : null
            ));
        }
        return out;
    }

    /**
     * Satış günü (dahil): nominal K/Z (mtm − maliyet). Satıştan sonraki günler: satılan miktarda günlük TRY değeri (mtm).
     */
    private static BigDecimal soldLifecyclePnlThenHypotheticalHoldValueDailyTry(
            List<ManualPortfolioPosition> positions,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees,
            LocalDate d
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (ManualPortfolioPosition p : positions) {
            if (p.getStatus() != ManualPositionStatus.SOLD || p.getSellDate() == null || p.getBuyDate() == null) {
                continue;
            }
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (d.isBefore(p.getBuyDate())) {
                continue;
            }
            any = true;
            NavigableMap<LocalDate, BigDecimal> tree = priceTrees.get(priceTreeKey(p.getType(), p.getSymbol().trim()));
            if (tree == null || tree.isEmpty()) {
                return null;
            }
            Map.Entry<LocalDate, BigDecimal> e = tree.floorEntry(d);
            if (e == null || e.getValue() == null || e.getValue().signum() <= 0) {
                return null;
            }
            BigDecimal q = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
            BigDecimal mtm = e.getValue().multiply(q);
            if (!d.isAfter(p.getSellDate())) {
                sum = sum.add(mtm.subtract(buyCostBasisFromPosition(p)));
            } else {
                sum = sum.add(mtm);
            }
        }
        return any ? sum : BigDecimal.ZERO;
    }

    private static boolean soldPositionNeedsPriceSeries(ManualPortfolioPosition p, LocalDate to) {
        return p.getSellDate() != null && !p.getSellDate().isAfter(to);
    }

    /**
     * Satış tarihi {@code d} veya öncesi olan satılmış pozisyonlar için: o günkü kapanış × satılan miktar toplamı.
     */
    private static BigDecimal soldHypotheticalHoldValueTry(
            List<ManualPortfolioPosition> soldPositions,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees,
            LocalDate d
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (ManualPortfolioPosition p : soldPositions) {
            if (p.getSellDate() == null || d.isBefore(p.getSellDate())) {
                continue;
            }
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            any = true;
            NavigableMap<LocalDate, BigDecimal> tree = priceTrees.get(priceTreeKey(p.getType(), p.getSymbol().trim()));
            if (tree == null || tree.isEmpty()) {
                return null;
            }
            Map.Entry<LocalDate, BigDecimal> e = tree.floorEntry(d);
            if (e == null || e.getValue() == null || e.getValue().signum() <= 0) {
                return null;
            }
            BigDecimal q = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
            sum = sum.add(e.getValue().multiply(q));
        }
        return any ? sum : BigDecimal.ZERO;
    }

    /**
     * Piyasa çağrısı olmadan alış maliyeti (nominal analizdeki buyCost ile aynı).
     */
    private static BigDecimal buyCostBasisFromPosition(ManualPortfolioPosition p) {
        BigDecimal qty = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
        BigDecimal buyPx = p.getBuyPrice() == null ? BigDecimal.ZERO : p.getBuyPrice();
        BigDecimal buyFee = p.getBuyFee();
        if (buyFee == null || buyFee.signum() < 0) {
            buyFee = BigDecimal.ZERO;
        }
        return buyPx.multiply(qty).add(buyFee).setScale(8, RoundingMode.HALF_UP);
    }

    private static boolean overlapsTimeseriesWindow(ManualPortfolioPosition p, LocalDate from, LocalDate to) {
        if (p.getBuyDate() == null || p.getBuyDate().isAfter(to)) {
            return false;
        }
        return p.getStatus() != ManualPositionStatus.SOLD
                || p.getSellDate() == null
                || !p.getSellDate().isBefore(from);
    }

    private static boolean isOpenOnDateForTs(ManualPortfolioPosition p, LocalDate d) {
        if (p.getBuyDate() == null || d.isBefore(p.getBuyDate())) {
            return false;
        }
        if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
            return d.isBefore(p.getSellDate());
        }
        return true;
    }

    private static String priceTreeKey(AssetType type, String symbol) {
        return type.name() + "|" + (symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT));
    }

    public ReadBundle buildReadBundleForUser(Long userId) {
        ManualPortfolioReadCache.ReadBundle cached = manualPortfolioReadCache.getOrLoad(userId, () -> {
            ReadBundle bundle = buildReadBundleInternal(userId);
            return new ManualPortfolioReadCache.ReadBundle(bundle.positions(), bundle.summary(), bundle.views());
        });
        return new ReadBundle(cached.positions(), cached.summary(), cached.views());
    }

    public ManualPortfolioSummaryView emptySummary() {
        return computeSummaryFromPositions(List.of(), null);
    }

    public ManualPortfolioSummaryView computeSummaryFor(
            List<ManualPortfolioPosition> all,
            LatestPricingSnapshot pricing
    ) {
        return computeSummaryFromPositions(all, pricing);
    }

    public Set<ManualPortfolioPriceTreeLoader.SymbolKey> collectSymbolKeysForTimeseries(
            List<ManualPortfolioPosition> positions,
            LocalDate effectiveFrom,
            LocalDate end
    ) {
        Set<ManualPortfolioPriceTreeLoader.SymbolKey> keys = new HashSet<>();
        for (ManualPortfolioPosition p : positions) {
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (overlapsTimeseriesWindow(p, effectiveFrom, end)) {
                keys.add(new ManualPortfolioPriceTreeLoader.SymbolKey(p.getType(), p.getSymbol().trim()));
            }
        }
        return keys;
    }

    public List<ManualPortfolioTimeseriesPointDto> buildTimeseriesUsingDbPrices(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        return buildManualTimeseries(axisScope, valueScope, from, to, userId, true, true);
    }

    /**
     * Artımlı timeseries: valueScope yalnızca değişen pozisyon(lar); axisScope portföy penceresi için.
     */
    public List<ManualPortfolioTimeseriesPointDto> buildTimeseriesForPositionsSubset(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        return buildManualTimeseries(axisScope, valueScope, from, to, userId, true, true);
    }

    public List<ManualPortfolioTimeseriesPointDto> buildTimeseriesDbOnlyRead(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to,
            Long userId
    ) {
        return buildManualTimeseries(axisScope, valueScope, from, to, userId, true, false);
    }

    public List<ManualPortfolioTimeseriesPointDto> timeseriesForPositions(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            LocalDate from,
            LocalDate to,
            Long userId,
            boolean preferDbPrices
    ) {
        return buildManualTimeseries(axisScope, valueScope, from, to, userId, preferDbPrices, true);
    }

    private Map<String, NavigableMap<LocalDate, BigDecimal>> loadPriceTreesForKeys(
            Long userId,
            Set<ManualPortfolioPriceTreeLoader.SymbolKey> keys,
            LocalDate histFrom,
            LocalDate end,
            boolean preferDb,
            boolean allowMdsFallback
    ) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, NavigableMap<LocalDate, BigDecimal>> byCacheKey;
        if (preferDb && userId != null) {
            byCacheKey = priceTreeLoader.loadFromDb(userId, keys, histFrom, end);
            Map<String, NavigableMap<LocalDate, BigDecimal>> dbTrees = byCacheKey;
            boolean complete = keys.stream().allMatch(k -> {
                NavigableMap<LocalDate, BigDecimal> tree = dbTrees.get(k.cacheKey());
                return tree != null && !tree.isEmpty();
            });
            if (!complete && allowMdsFallback) {
                LatestPricingSnapshot snap = nominalAnalysisCalculator.loadLatestPricingSnapshot();
                byCacheKey = priceTreeLoader.loadFromMdsParallel(userId, keys, histFrom, end, snap, true);
            }
        } else if (allowMdsFallback) {
            LatestPricingSnapshot snap = nominalAnalysisCalculator.loadLatestPricingSnapshot();
            byCacheKey = priceTreeLoader.loadFromMdsParallel(userId, keys, histFrom, end, snap, userId != null);
        } else {
            byCacheKey = Map.of();
        }
        Map<String, NavigableMap<LocalDate, BigDecimal>> out = new HashMap<>();
        for (ManualPortfolioPriceTreeLoader.SymbolKey k : keys) {
            NavigableMap<LocalDate, BigDecimal> tree = byCacheKey.get(k.cacheKey());
            if (tree != null) {
                out.put(priceTreeKey(k.type(), k.symbol()), tree);
            }
        }
        return out;
    }

    private ReadBundle buildReadBundleInternal(Long userId) {
        List<ManualPortfolioPosition> all = manualRepo.findByUserIdOrderByBuyDateAsc(userId);
        ManualPortfolioSummaryView summary = computeSummaryFromPositions(all, null);
        List<ManualPortfolioView> views = manualPortfolioViewAssembler.toViews(all);
        return new ReadBundle(List.copyOf(all), summary, views);
    }

    /**
     * {@code portfolioMarketValueTry} — Belirli bir tarihte açık pozisyonların günlük kapanış fiyatlarıyla TRY piyasa değeri toplamını hesaplar.
     */
    private static BigDecimal portfolioMarketValueTry(
            List<ManualPortfolioPosition> positions,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees,
            LocalDate d
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean anyOpen = false;
        for (ManualPortfolioPosition p : positions) {
            if (!isOpenOnDateForTs(p, d)) {
                continue;
            }
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            anyOpen = true;
            NavigableMap<LocalDate, BigDecimal> tree = priceTrees.get(priceTreeKey(p.getType(), p.getSymbol().trim()));
            if (tree == null || tree.isEmpty()) {
                return null;
            }
            Map.Entry<LocalDate, BigDecimal> e = tree.floorEntry(d);
            if (e == null || e.getValue() == null || e.getValue().signum() <= 0) {
                return null;
            }
            BigDecimal q = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
            sum = sum.add(e.getValue().multiply(q));
        }
        return anyOpen ? sum : BigDecimal.ZERO;
    }

    /**
     * {@code summaryMine} — Kullanıcının manuel portfolio KPI özetini (yatırım, K/Z, en iyi/kötü performans) hesaplar.
     */
    @Transactional(readOnly = true)
    public ManualPortfolioSummaryView summaryMine() {
        return getReadBundle().summary();
    }

    private ManualPortfolioSummaryView computeSummaryFromPositions(
            List<ManualPortfolioPosition> all,
            LatestPricingSnapshot pricingOverride
    ) {
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

        LatestPricingSnapshot pricing = pricingOverride != null
                ? pricingOverride
                : nominalAnalysisCalculator.loadLatestPricingSnapshot();
        for (ManualPortfolioPosition p : all) {
            ManualPortfolioNominalAnalysis a = nominalAnalysisCalculator.compute(p, pricing);
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

    /**
     * {@code analysis} — Tek pozisyon için fiyat grafiği, alış/satış/bugün marker'ları ve görünüm DTO'su döner.
     */
    @Transactional(readOnly = true)
    public ManualPortfolioAnalysisResponse analysis(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));

        ManualPortfolioView view = manualPortfolioViewAssembler.toView(p);

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
        if (view.getCurrentPrice() != null && view.getCurrentPrice().signum() > 0) {
            markers.add(new ManualPortfolioAnalysisResponse.Marker(
                    "TODAY", today, view.getCurrentPrice(),
                    view.getCurrentPrice().multiply(p.getQuantity()).setScale(8, RoundingMode.HALF_UP)));
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

    /** Snapshot tüm portföy + market-data çeker; HTTP yanıtını bekletmemek için arka planda. */
    private void scheduleManualSnapshot(Long userId) {
        CompletableFuture.runAsync(() -> recordManualSnapshot(userId));
    }
}
