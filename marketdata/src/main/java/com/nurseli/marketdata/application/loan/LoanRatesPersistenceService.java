package com.nurseli.marketdata.application.loan;

import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.domain.loan.LoanRateWeeklyObservation;
import com.nurseli.marketdata.infrastructure.persistence.LoanRateWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanRatesPersistenceService {

    private static final String CATEGORY = "LOAN_RATE";
    private static final String SOURCE = "EVDS";
    private static final String UNIT = "PERCENT";
    private static final String FREQUENCY = "WEEKLY";

    private final LoanRateWeeklyObservationRepository repository;

    public record LoanRateObservationRow(
            String seriesCode,
            LoanRateSubtype subType,
            LocalDate observedDate,
            BigDecimal ratePercent
    ) {}

    @Transactional
    public void upsert(String seriesCode, LoanRateSubtype subType, LocalDate observedDate, BigDecimal ratePercent) {
        upsertInternal(seriesCode, subType, observedDate, ratePercent);
    }

    /** Tek transaction — HTTP okuma yolunda bağlantı havuzunu tüketmemek için. */
    @Transactional
    public void upsertAll(List<LoanRateObservationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (LoanRateObservationRow row : rows) {
            if (row == null || row.seriesCode() == null || row.subType() == null || row.observedDate() == null) {
                continue;
            }
            if (row.ratePercent() == null || row.ratePercent().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            upsertInternal(row.seriesCode(), row.subType(), row.observedDate(), row.ratePercent());
        }
    }

    private void upsertInternal(String seriesCode, LoanRateSubtype subType, LocalDate observedDate, BigDecimal ratePercent) {
        LocalDateTime now = LocalDateTime.now();
        repository.findBySeriesCodeAndObservedDate(seriesCode, observedDate).ifPresentOrElse(existing -> {
            existing.setRatePercent(ratePercent);
            existing.setUpdatedAt(now);
            repository.save(existing);
        }, () -> {
            LoanRateWeeklyObservation n = new LoanRateWeeklyObservation();
            n.setSeriesCode(seriesCode);
            n.setSubType(subType);
            n.setObservedDate(observedDate);
            n.setRatePercent(ratePercent);
            n.setFrequency(FREQUENCY);
            n.setUnit(UNIT);
            n.setSource(SOURCE);
            n.setCategory(CATEGORY);
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            repository.save(n);
        });
    }
}
