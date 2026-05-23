package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.dto.ViopBondCombinedSummaryDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertStatus;
import com.nurseli.nrsfinanceportal.api.dto.bond.BondPositionSummaryDto;
import com.nurseli.nrsfinanceportal.api.dto.viop.ViopPositionSummaryDto;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PriceAlertRepository;
import com.nurseli.nrsfinanceportal.application.bond.ManualBondPositionService;
import com.nurseli.nrsfinanceportal.application.viop.ManualViopPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * finance-service VIOP-bond birleşik analiz servisi — VIOP ve bond özetlerini tek dashboard KPI'sında birleştirir.
 */
@RequiredArgsConstructor
@Service

public class ViopBondAnalysisSummaryService {

    private final ManualViopPositionService viopPositionService;
    private final ManualBondPositionService bondPositionService;
    private final CurrentUserResolver currentUserResolver;
    private final PriceAlertRepository priceAlertRepository;

    /**
     * {@code combinedSummary} — VIOP net finansal etki, bond değeri, risk maruziyeti, vadesi yaklaşan pozisyon ve aktif price alert sayısını döner.
     */
    @Transactional(readOnly = true)
    public ViopBondCombinedSummaryDto combinedSummary() {
        var user = currentUserResolver.getOrCreateCurrentUser();
        ViopPositionSummaryDto viop = viopPositionService.summaryMine();
        BondPositionSummaryDto bond = bondPositionService.summaryMine();

        BigDecimal bondValue = nz(bond.totalCurrentValue());
        BigDecimal viopMargin = nz(viop.totalInitialMargin());
        BigDecimal viopPnl = nz(viop.totalUnrealizedPnl());
        BigDecimal viopNet = viop.netFinancialEffect() != null ? viop.netFinancialEffect() : viopMargin.add(viopPnl);
        BigDecimal totalEffect = bondValue.add(viopNet).setScale(6, RoundingMode.HALF_UP);

        int expiring = viop.expiringSoonCount() + bond.expiringSoonCount();
        int alertCount = (int) priceAlertRepository.countByUser_IdAndStatusAndAssetTypeIn(
                user.getId(),
                PriceAlertStatus.ACTIVE,
                java.util.List.of(AssetType.VIOP, AssetType.BOND)
        );

        return new ViopBondCombinedSummaryDto(
                totalEffect,
                nz(viop.totalRiskExposure()),
                expiring,
                alertCount,
                null,
                bondValue,
                viopNet.setScale(6, RoundingMode.HALF_UP),
                viopMargin,
                viopPnl
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
