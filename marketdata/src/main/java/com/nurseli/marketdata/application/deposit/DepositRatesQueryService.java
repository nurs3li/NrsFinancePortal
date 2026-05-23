package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.api.dto.deposit.DepositRateHistoryRowDto;
import com.nurseli.marketdata.api.dto.deposit.DepositRateLatestRowDto;
import com.nurseli.marketdata.api.dto.deposit.DepositRateSeriesMetaDto;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import com.nurseli.marketdata.infrastructure.persistence.DepositRateObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DepositRatesQueryService {

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesSeriesResolver depositRatesSeriesResolver;
    private final DepositRateObservationRepository repository;

    public List<DepositRateLatestRowDto> latestAll() {
        if (!depositRatesProperties.isEnabled()) {
            return List.of();
        }
        return repository.findLatestPerCurrencyTerm().stream()
                .sorted(Comparator.comparing(DepositRateObservation::getCurrency).thenComparing(DepositRateObservation::getTerm))
                .map(this::toLatestDto)
                .toList();
    }

    public List<DepositRateHistoryRowDto> history(String currency, String term, LocalDate from, LocalDate to) {
        if (!depositRatesProperties.isEnabled()) {
            return List.of();
        }
        String ccy = normalizeCcy(currency);
        String tm = normalizeTerm(term);
        return repository.findByCurrencyAndTermAndObservationDateBetweenOrderByObservationDateAsc(ccy, tm, from, to).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<DepositRateSeriesMetaDto> seriesForCurrency(String currency) {
        if (!depositRatesProperties.isEnabled()) {
            return List.of();
        }
        String ccy = normalizeCcy(currency);
        return depositRatesSeriesResolver.resolved().stream()
                .filter(s -> s != null && ccy.equalsIgnoreCase(s.currency()))
                .map(s -> new DepositRateSeriesMetaDto(
                        s.seriesCode(),
                        s.logicalIndicatorCode(),
                        CATEGORY,
                        SOURCE,
                        depositRatesProperties.getFrequency(),
                        UNIT,
                        FLOW,
                        s.currency().toUpperCase(),
                        s.term()
                ))
                .toList();
    }

    /**
     * Portföy simülasyonu / “geçmişte mevduata koysaydım” senaryoları için: {@code targetDate}
     * tarihine eşit veya önceki en son haftalık gözlemin faiz oranını döndürür.
     */
    public Optional<DepositRateObservation> findNearestPreviousRate(String currency, String term, LocalDate targetDate) {
        if (!depositRatesProperties.isEnabled() || targetDate == null) {
            return Optional.empty();
        }
        return repository.findFirstByCurrencyAndTermAndObservationDateLessThanEqualAndRateValueIsNotNullOrderByObservationDateDesc(
                normalizeCcy(currency),
                normalizeTerm(term),
                targetDate
        );
    }

    private static String normalizeCcy(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase();
    }

    private static String normalizeTerm(String term) {
        return term == null ? "" : term.trim().toUpperCase();
    }

    private DepositRateLatestRowDto toLatestDto(DepositRateObservation e) {
        String logical = resolveLogicalIndicator(e);
        return new DepositRateLatestRowDto(
                e.getSeriesCode(),
                logical,
                e.getCategory(),
                e.getSource(),
                e.getFrequency(),
                e.getUnit(),
                e.getFlowType(),
                e.getCurrency(),
                e.getTerm(),
                e.getObservationDate(),
                e.getRateValue()
        );
    }

    private String resolveLogicalIndicator(DepositRateObservation e) {
        if (e == null || e.getSeriesCode() == null) {
            return null;
        }
        String sc = e.getSeriesCode().trim();
        String ccy = normalizeCcy(e.getCurrency());
        String term = normalizeTerm(e.getTerm());
        Optional<DepositRatesSeriesResolver.ResolvedDepositSeries> hit = depositRatesSeriesResolver.resolved().stream()
                .filter(r -> r != null && sc.equals(r.seriesCode()))
                .filter(r -> ccy.equalsIgnoreCase(r.currency()))
                .filter(r -> term.equals(normalizeTerm(r.term())))
                .findFirst();
        return hit.map(DepositRatesSeriesResolver.ResolvedDepositSeries::logicalIndicatorCode).orElse(null);
    }

    private DepositRateHistoryRowDto toHistoryDto(DepositRateObservation e) {
        return new DepositRateHistoryRowDto(
                e.getSeriesCode(),
                e.getCurrency(),
                e.getTerm(),
                e.getObservationDate(),
                e.getRateValue()
        );
    }

    private static final String CATEGORY = "DEPOSIT_RATE";
    private static final String SOURCE = "EVDS";
    private static final String UNIT = "PERCENT";
    private static final String FLOW = "AKIM";
}
