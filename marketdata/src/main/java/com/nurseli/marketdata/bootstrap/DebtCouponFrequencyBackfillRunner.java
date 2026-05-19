package com.nurseli.marketdata.bootstrap;

import com.nurseli.marketdata.application.debt.DebtCouponFrequencyPersistence;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class DebtCouponFrequencyBackfillRunner implements ApplicationRunner {

    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtCouponFrequencyPersistence debtCouponFrequencyPersistence;

    @Override
    public void run(ApplicationArguments args) {
        var missing = debtInstrumentRepository.findAll().stream()
                .filter(i -> i.getCouponFrequencySource() == null || i.getCouponFrequencySource().isBlank())
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        for (var ins : missing) {
            debtCouponFrequencyPersistence.ensurePersistedIfMissing(ins);
        }
        long nowFilled = debtInstrumentRepository.findAll().stream()
                .filter(i -> i.getCouponFrequencySource() != null && !i.getCouponFrequencySource().isBlank())
                .count();
        log.info("[DEBT] coupon frequency backfill: candidates={}, totalWithFrequency={}", missing.size(), nowFilled);
    }
}
