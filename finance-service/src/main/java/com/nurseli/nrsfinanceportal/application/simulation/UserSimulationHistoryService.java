package com.nurseli.nrsfinanceportal.application.simulation;

import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryEntryDto;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryListResponse;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistorySaveRequest;
import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.domain.simulation.UserSimulationHistory;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserSimulationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kullanıcıya özel simülasyon geçmişi kayıtlarını yönetir.
 */
@RequiredArgsConstructor
@Service
public class UserSimulationHistoryService {

    private static final int MAX_ENTRIES = 40;

    private final CurrentUserResolver currentUserResolver;
    private final UserSimulationHistoryRepository repository;
    private final SimulationHistoryMapper mapper;

    @Transactional(readOnly = true)
    public SimulationHistoryListResponse listForCurrentUser() {
        Long userId = currentUserResolver.getCurrentUserId();
        List<SimulationHistoryEntryDto> entries = repository.findByUser_IdOrderBySavedAtDesc(userId).stream()
                .map(mapper::toSummary)
                .toList();
        return new SimulationHistoryListResponse(entries);
    }

    @Transactional(readOnly = true)
    public SimulationHistoryEntryDto getByIdForCurrentUser(String id) {
        Long userId = currentUserResolver.getCurrentUserId();
        UserSimulationHistory entity = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(this::notFound);
        return mapper.toDetail(entity);
    }

    @Transactional
    public SimulationHistoryEntryDto saveForCurrentUser(SimulationHistorySaveRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        trimOldestIfNeeded(user.getId());

        String id = UUID.randomUUID().toString();
        Instant savedAt = Instant.now();
        String itemsJson = mapper.writeItems(request.items());

        UserSimulationHistory entity = UserSimulationHistory.of(
                id,
                user,
                request.label().trim(),
                savedAt,
                normalizeCurrency(request.amountCurrency()),
                itemsJson
        );
        repository.save(entity);
        return mapper.toDetail(entity);
    }

    @Transactional
    public void deleteForCurrentUser(String id) {
        Long userId = currentUserResolver.getCurrentUserId();
        if (!repository.findByIdAndUser_Id(id, userId).isPresent()) {
            throw notFound();
        }
        repository.deleteByIdAndUser_Id(id, userId);
    }

    private void trimOldestIfNeeded(Long userId) {
        long count = repository.countByUser_Id(userId);
        if (count < MAX_ENTRIES) {
            return;
        }
        List<UserSimulationHistory> rows = repository.findByUser_IdOrderBySavedAtDesc(userId);
        for (int i = MAX_ENTRIES - 1; i < rows.size(); i++) {
            repository.delete(rows.get(i));
        }
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "TRY";
        }
        String upper = currency.trim().toUpperCase();
        return "USD".equals(upper) ? "USD" : "TRY";
    }

    private ApiBusinessException notFound() {
        return new ApiBusinessException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Simülasyon kaydı bulunamadı."
        );
    }
}
