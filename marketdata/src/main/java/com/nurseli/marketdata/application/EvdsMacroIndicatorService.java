package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.api.dto.PolicyRateTrResponse;
import com.nurseli.marketdata.api.dto.TcmbWeightedFundingCostResponse;
import com.nurseli.marketdata.api.dto.TurkeyApproxRealRateResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvdsMacroIndicatorService {

    private static final String DISPLAY_TCMB_WEIGHTED_FUNDING = "TCMB Ortalama Fonlama Maliyeti";
    private static final String DESC_TCMB_WEIGHTED_FUNDING =
            "TCMB'nin piyasayı fiilen hangi ortalama maliyetle fonladığını gösterir; politika faizi değildir.";

    private static final String DISPLAY_POLICY_RATE = "TCMB Politika Faizi";

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final EvdsCpiTrService evdsCpiTrService;

    /**
     * Aylık politika faizi — EVDS aralığı ay bazlı geniş tutulur (günlük/haftalık frekansa zorlanmaz).
     */
    public Optional<PolicyRateTrResponse> latestPolicyRateTr() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        String code = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.POLICY_RATE_TR);
        if (code == null) {
            return Optional.empty();
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(84);
        return lastPoint(code, start, end)
                .map(p -> {
                    YearMonth ym = YearMonth.from(p.asOf().toLocalDate());
                    return new PolicyRateTrResponse(
                            EvdsSeriesLogicalNames.POLICY_RATE_TR,
                            code,
                            p.value(),
                            ym.toString(),
                            "MONTHLY",
                            "PERCENT",
                            DISPLAY_POLICY_RATE
                    );
                });
    }

    public Optional<TcmbWeightedFundingCostResponse> latestTcmbWeightedFundingCost() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        String code = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR);
        if (code == null) {
            return Optional.empty();
        }
        return lastPoint(code, LocalDate.now().minusWeeks(80), LocalDate.now())
                .map(p -> new TcmbWeightedFundingCostResponse(
                        EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR,
                        code,
                        p.value(),
                        p.asOf(),
                        "WEEKLY",
                        "PERCENT",
                        DISPLAY_TCMB_WEIGHTED_FUNDING,
                        DESC_TCMB_WEIGHTED_FUNDING
                ));
    }

    /**
     * Nominal − yıllık TÜFE: nominal için önce {@code POLICY_RATE_TR}, yoksa
     * {@code TCMB_WEIGHTED_AVG_FUNDING_COST_TR} kullanılır ({@code sourceIndicatorCode}).
     */
    public Optional<TurkeyApproxRealRateResponse> approxTurkeyRealRate() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        Optional<CpiTrMacroResponse> cpiOpt = evdsCpiTrService.latestCpiTr();
        if (cpiOpt.isEmpty()) {
            return Optional.empty();
        }
        CpiTrMacroResponse cpi = cpiOpt.get();
        if (cpi.cpiTrAnnual() == null) {
            return Optional.empty();
        }
        NominalPick pick = resolveNominalForRealRate();
        if (pick == null) {
            return Optional.empty();
        }
        BigDecimal real = pick.nominal()
                .subtract(cpi.cpiTrAnnual())
                .setScale(4, RoundingMode.HALF_UP);
        String note = "Kabaca reel oran = nominal yüzde (EVDS) − TÜFE yıllık yüzde (CPI_TR_INDEX serisinden). Fisher denklemi değildir.";
        if (EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR.equals(pick.sourceIndicator())) {
            note += " Nominal gösterge: TCMB ağırlıklı ortalama fonlama maliyeti (politika faizi serisinde kullanılabilir gözlem yok veya EVDS verisi boş).";
        }
        return Optional.of(new TurkeyApproxRealRateResponse(
                pick.sourceIndicator(),
                pick.evdsSeries(),
                pick.nominal(),
                pick.asOf(),
                cpi.cpiTrAnnual(),
                cpi.indexMonth(),
                real,
                note
        ));
    }

    private NominalPick resolveNominalForRealRate() {
        String policyCode = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.POLICY_RATE_TR);
        if (policyCode != null) {
            LocalDate end = LocalDate.now();
            Optional<EvdsSeriesPoint> pt = lastPoint(policyCode, end.minusMonths(84), end);
            if (pt.isPresent()) {
                EvdsSeriesPoint p = pt.get();
                return new NominalPick(EvdsSeriesLogicalNames.POLICY_RATE_TR, policyCode, p.value(), p.asOf());
            }
        }
        String fundingCode = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR);
        if (fundingCode != null) {
            Optional<EvdsSeriesPoint> pt = lastPoint(fundingCode, LocalDate.now().minusWeeks(80), LocalDate.now());
            if (pt.isPresent()) {
                EvdsSeriesPoint p = pt.get();
                return new NominalPick(
                        EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR,
                        fundingCode,
                        p.value(),
                        p.asOf()
                );
            }
        }
        return null;
    }

    private Optional<EvdsSeriesPoint> lastPoint(String evdsSeriesCode, LocalDate startInclusive, LocalDate endInclusive) {
        List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(evdsSeriesCode, startInclusive, endInclusive);
        if (pts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pts.get(pts.size() - 1));
    }

    private record NominalPick(String sourceIndicator, String evdsSeries, BigDecimal nominal, LocalDateTime asOf) {}
}
