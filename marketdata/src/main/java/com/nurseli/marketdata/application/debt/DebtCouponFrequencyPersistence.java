package com.nurseli.marketdata.application.debt;

import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kupon ödeme sıklığı çıkarımını yalnızca bir kez DB'ye yazar; dolu alanlar güncellenmez.
 */
@Service
@RequiredArgsConstructor
public class DebtCouponFrequencyPersistence {

    private final DebtInstrumentRepository debtInstrumentRepository;
    private final EvdsDebtSeriesLookup evdsDebtSeriesLookup;

    @Transactional
    public void ensurePersistedIfMissing(String isin) {
        if (isin == null || isin.isBlank()) {
            return;
        }
        String key = isin.trim().toUpperCase();
        debtInstrumentRepository.findByIsin(key).ifPresent(this::ensurePersistedIfMissing);
    }

    @Transactional
    public void ensurePersistedIfMissing(DebtInstrument instrument) {
        if (instrument == null || instrument.getIsin() == null) {
            return;
        }
        if (instrument.getCouponFrequencySource() != null && !instrument.getCouponFrequencySource().isBlank()) {
            return;
        }
        String series = evdsDebtSeriesLookup.referenceSeriesCode(instrument.getIsin());
        EvdsCouponFrequencyHeuristic.Result freq = EvdsCouponFrequencyHeuristic.resolve(series);
        if (freq == null) {
            return;
        }
        instrument.setCouponFrequencyPerYear(freq.perYear());
        instrument.setCouponFrequencyLabel(freq.label());
        instrument.setCouponFrequencySource(freq.source());
        if (series != null && !series.isBlank()) {
            instrument.setEvdsSeriesReference(series.trim());
        }
        debtInstrumentRepository.save(instrument);
    }
}
