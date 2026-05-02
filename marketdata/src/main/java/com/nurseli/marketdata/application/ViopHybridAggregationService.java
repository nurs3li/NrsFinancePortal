package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.ViopHybridProperties;
import com.nurseli.marketdata.infrastructure.bist.BistViopBulletinClient;
import com.nurseli.marketdata.infrastructure.bist.BistViopClient;
import com.nurseli.marketdata.infrastructure.bist.ViopPriceFallbackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopHybridAggregationService {
    private final ViopHybridProperties properties;
    private final BistViopClient viopClient;
    private final BistViopBulletinClient bulletinClient;
    private final ViopPriceFallbackClient priceFallbackClient;
    private final ViopCalculationService calculationService;
    private final ViopContractParser contractParser;

    public List<HybridViopRow> fetchLatest() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<BistViopClient.ViopExternalRow> baseRows = viopClient.fetchLatest();
        List<BistViopBulletinClient.ViopBulletinRow> bulletinRows = bulletinClient.fetchRows();
        List<ViopPriceFallbackClient.ViopFallbackPriceRow> fallbackRows = priceFallbackClient.fetchRows();

        Map<String, BistViopClient.ViopExternalRow> baseMap = new HashMap<>();
        for (BistViopClient.ViopExternalRow row : baseRows) {
            if (row == null || row.contractCode() == null || row.contractCode().isBlank()) continue;
            baseMap.put(contractParser.normalizeContractCode(row.contractCode()), row);
        }
        Map<String, BistViopBulletinClient.ViopBulletinRow> bulletinMap = new HashMap<>();
        for (BistViopBulletinClient.ViopBulletinRow row : bulletinRows) {
            if (row == null || row.contractCode() == null || row.contractCode().isBlank()) continue;
            bulletinMap.put(contractParser.normalizeContractCode(row.contractCode()), row);
        }
        Map<String, ViopPriceFallbackClient.ViopFallbackPriceRow> fallbackMap = new HashMap<>();
        for (ViopPriceFallbackClient.ViopFallbackPriceRow row : fallbackRows) {
            if (row == null || row.contractCode() == null || row.contractCode().isBlank()) continue;
            fallbackMap.put(contractParser.normalizeContractCode(row.contractCode()), row);
        }

        Set<String> contracts = new LinkedHashSet<>();
        contracts.addAll(baseMap.keySet());
        contracts.addAll(bulletinMap.keySet());
        contracts.addAll(fallbackMap.keySet());

        List<HybridViopRow> out = new ArrayList<>();
        for (String contractCode : contracts) {
            BistViopClient.ViopExternalRow base = baseMap.get(contractCode);
            BistViopBulletinClient.ViopBulletinRow bulletin = bulletinMap.get(contractCode);
            ViopPriceFallbackClient.ViopFallbackPriceRow fallback = fallbackMap.get(contractCode);

            BigDecimal basePrice = base != null ? base.price() : null;
            BigDecimal settlement = bulletin != null ? bulletin.settlementPrice() : null;
            BigDecimal selectedPrice = fallback != null && fallback.price() != null
                    ? fallback.price()
                    : (basePrice != null ? basePrice : settlement);
            BigDecimal spot = base != null ? base.spot() : null;
            BigDecimal basis = calculationService.calculateBasis(selectedPrice, spot);
            BigDecimal basisPct = calculationService.basisPercent(basis, spot);
            String quality = calculationService.qualityFlag(basisPct);
            String expiry = contractParser.inferExpiryFromContractCode(contractCode, base != null ? base.expiry() : null);
            Integer daysToExpiry = contractParser.calculateDaysToExpiry(expiry);
            BigDecimal maintenanceMargin = calculationService.maintenanceMargin(selectedPrice);
            Long openInterest = bulletin != null && bulletin.openInterest() != null
                    ? bulletin.openInterest()
                    : (base != null ? base.openInterest() : 0L);
            Long dailyVolume = bulletin != null ? bulletin.dailyVolume() : null;
            LocalDateTime asOf = maxAsOf(
                    base != null ? base.asOf() : null,
                    bulletin != null ? bulletin.asOf() : null,
                    fallback != null ? fallback.asOf() : null
            );
            String source = "VIOP_HYBRID";
            String priceSource = fallback != null && fallback.price() != null
                    ? (fallback.source() == null || fallback.source().isBlank() ? "PRICE_FALLBACK" : fallback.source())
                    : (base != null && base.price() != null ? "VIOP_PROVIDER" : "BULLETIN");
            Long latencyMs = fallback != null ? fallback.latencyMs() : null;
            out.add(new HybridViopRow(
                    contractCode,
                    base != null ? base.underlying() : inferUnderlying(contractCode),
                    expiry,
                    base != null ? base.type() : "FUTURES",
                    selectedPrice == null ? BigDecimal.ZERO : selectedPrice,
                    spot == null ? BigDecimal.ZERO : spot,
                    basis == null ? BigDecimal.ZERO : basis,
                    maintenanceMargin,
                    daysToExpiry,
                    openInterest == null ? 0L : openInterest,
                    dailyVolume,
                    quality,
                    priceSource,
                    latencyMs,
                    asOf,
                    source
            ));
        }

        log.info("[VIOP_HYBRID] baseRows={}, bulletinRows={}, fallbackRows={}, mergedContracts={}",
                baseRows.size(), bulletinRows.size(), fallbackRows.size(), out.size());
        return out;
    }

    private LocalDateTime maxAsOf(LocalDateTime... values) {
        LocalDateTime max = LocalDateTime.now();
        for (LocalDateTime value : values) {
            if (value != null && value.isAfter(max)) {
                max = value;
            }
        }
        return max;
    }

    private String inferUnderlying(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) return "UNKNOWN";
        String trimmed = contractCode.trim().toUpperCase(Locale.ROOT);
        if (trimmed.endsWith("0626") || trimmed.endsWith("0726") || trimmed.endsWith("0826")) {
            return trimmed.substring(0, Math.max(0, trimmed.length() - 4));
        }
        return trimmed;
    }

    public record HybridViopRow(
            String contractCode,
            String underlying,
            String expiry,
            String type,
            BigDecimal price,
            BigDecimal spot,
            BigDecimal basis,
            BigDecimal maintenanceMargin,
            Integer daysToExpiry,
            Long openInterest,
            Long dailyVolume,
            String quality,
            String priceSource,
            Long priceLatencyMs,
            LocalDateTime asOf,
            String source
    ) {}
}

