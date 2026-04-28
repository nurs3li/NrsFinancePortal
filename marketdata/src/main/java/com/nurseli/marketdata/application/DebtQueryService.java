package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import com.nurseli.marketdata.repository.DebtSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtQueryService {
    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;

    public List<DebtInstrumentResponse> catalog() {
        return debtInstrumentRepository.findAll().stream()
                .map(i -> new DebtInstrumentResponse(i.getIsin(), i.getName(), i.getIssuer(), i.getMaturityDate()))
                .toList();
    }

    public List<DebtSnapshotResponse> latest() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<DebtSnapshotResponse> rows = debtSnapshotRepository.findAll().stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toResponse)
                .toList();
        if (rows.isEmpty()) {
            log.info("[DEBT] latest() returned empty list (no fresh snapshots)");
        }
        return rows;
    }

    public List<DebtSnapshotResponse> history(String isin, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return debtSnapshotRepository.findByIsinOrderByAsOfAsc(isin).stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toResponse)
                .toList();
    }

    private DebtSnapshotResponse toResponse(com.nurseli.marketdata.domain.debt.DebtSnapshot s) {
        String source = (s.getSource() == null || s.getSource().isBlank()) ? "DEBT_MVP" : s.getSource();
        LocalDateTime asOf = s.getAsOf() == null ? LocalDateTime.now() : s.getAsOf();
        return new DebtSnapshotResponse(s.getIsin(), s.getDirtyPrice(), s.getYieldPct(), source, asOf);
    }
}
