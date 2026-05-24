package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.api.dto.eurobond.*;
import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.domain.eurobond.EurobondInstrument;
import com.nurseli.marketdata.domain.eurobond.EurobondPriceSnapshot;
import com.nurseli.marketdata.infrastructure.persistence.EurobondInstrumentRepository;
import com.nurseli.marketdata.infrastructure.persistence.EurobondPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@ConditionalOnEurobondEvds
@RequiredArgsConstructor
public class EurobondInstrumentQueryService {

    private final EurobondEvdsProperties properties;
    private final EurobondInstrumentRegistry registry;
    private final EurobondInstrumentRepository instrumentRepository;
    private final EurobondPriceSnapshotRepository snapshotRepository;

    public EurobondInstrumentListResponse catalog() {
        if (!enabled()) {
            return new EurobondInstrumentListResponse(false, List.of());
        }
        List<EurobondInstrumentItemDto> items;
        try {
            items = instrumentRepository.findByActiveTrueOrderByMaturityDateAsc().stream()
                    .map(this::toItem)
                    .toList();
        } catch (Exception ex) {
            items = List.of();
        }
        if (items.isEmpty()) {
            items = registry.all().stream().map(this::toItemFromSpec).toList();
        }
        return new EurobondInstrumentListResponse(!items.isEmpty(), items);
    }

    public EurobondInstrumentLatestResponse latest() {
        if (!enabled()) {
            return new EurobondInstrumentLatestResponse(false, List.of());
        }
        List<EurobondInstrumentLatestRowDto> rows;
        try {
            rows = instrumentRepository.findByActiveTrueOrderByMaturityDateAsc().stream()
                    .map(ins -> snapshotRepository.findTopByIsinOrderByAsOfDateDesc(ins.getIsin())
                            .map(snap -> toLatestRow(ins, snap))
                            .orElse(toLatestRow(ins, null)))
                    .filter(r -> r.cleanPrice() != null)
                    .toList();
        } catch (Exception ex) {
            rows = List.of();
        }
        if (rows.isEmpty()) {
            rows = registry.all().stream()
                    .map(spec -> new EurobondInstrumentLatestRowDto(
                            spec.isin(),
                            spec.name(),
                            spec.currency(),
                            spec.couponPct(),
                            spec.maturityDate(),
                            spec.referenceCleanPrice(),
                            spec.referenceYieldPct() != null ? spec.referenceYieldPct() : spec.couponPct(),
                            null,
                            "CURATED_MID"))
                    .toList();
        }
        return new EurobondInstrumentLatestResponse(!rows.isEmpty(), rows);
    }

    public EurobondInstrumentHistoryResponse history(String isin, int days) {
        if (!enabled() || isin == null || isin.isBlank()) {
            return emptyHistory(isin);
        }
        String key = isin.trim().toUpperCase(Locale.ROOT);
        Optional<EurobondInstrument> ins = instrumentRepository.findByIsin(key);
        if (ins.isEmpty()) {
            return emptyHistory(key);
        }
        int safeDays = Math.max(7, Math.min(days, 3650));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(safeDays);
        List<EurobondInstrumentHistoryPointDto> points = snapshotRepository
                .findByIsinAndAsOfDateBetweenOrderByAsOfDateAsc(key, from, to)
                .stream()
                .map(this::toHistoryPoint)
                .toList();
        String source = snapshotRepository.findTopByIsinOrderByAsOfDateDesc(key)
                .map(EurobondPriceSnapshot::getSource)
                .orElse(null);
        return new EurobondInstrumentHistoryResponse(
                !points.isEmpty(),
                key,
                ins.get().getName(),
                ins.get().getCurrency(),
                "Haftalık",
                "Fiyat (100 üzerinden)",
                source,
                points);
    }

    private EurobondInstrumentItemDto toItem(EurobondInstrument ins) {
        Long minLot = registry.findByIsin(ins.getIsin()).map(EurobondInstrumentRegistry.ResolvedInstrument::minLotUsd).orElse(200_000L);
        return new EurobondInstrumentItemDto(
                ins.getIsin(),
                ins.getName(),
                ins.getIssuer(),
                ins.getCurrency(),
                ins.getCouponPct(),
                ins.getMaturityDate(),
                minLot);
    }

    private EurobondInstrumentItemDto toItemFromSpec(EurobondInstrumentRegistry.ResolvedInstrument spec) {
        return new EurobondInstrumentItemDto(
                spec.isin(),
                spec.name(),
                spec.issuer(),
                spec.currency(),
                spec.couponPct(),
                spec.maturityDate(),
                spec.minLotUsd());
    }

    private EurobondInstrumentLatestRowDto toLatestRow(EurobondInstrument ins, EurobondPriceSnapshot snap) {
        return new EurobondInstrumentLatestRowDto(
                ins.getIsin(),
                ins.getName(),
                ins.getCurrency(),
                ins.getCouponPct(),
                ins.getMaturityDate(),
                snap != null ? snap.getCleanPrice() : null,
                snap != null ? snap.getYieldPct() : null,
                snap != null ? snap.getAsOfDate() : null,
                snap != null ? snap.getSource() : null);
    }

    private EurobondInstrumentHistoryPointDto toHistoryPoint(EurobondPriceSnapshot s) {
        return new EurobondInstrumentHistoryPointDto(
                s.getAsOfDate().toString(),
                s.getCleanPrice(),
                s.getYieldPct());
    }

    private EurobondInstrumentHistoryResponse emptyHistory(String isin) {
        return new EurobondInstrumentHistoryResponse(false, isin, null, null, null, null, null, List.of());
    }

    private boolean enabled() {
        return properties.isEnabled() && properties.getInstruments().isEnabled();
    }
}
