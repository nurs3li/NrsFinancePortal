package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiEmailDeliveryDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiEmailDeliveryUpsertRequest;
import com.nurseli.nrsfinanceportal.common.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.common.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryEntity;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryFrequency;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.PortfolioAiEmailDeliveryRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PortfolioAiEmailDeliveryService {

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioAiEmailDeliveryRepository repository;

    @Transactional(readOnly = true)
    public PortfolioAiEmailDeliveryDto getForCurrentUser() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        return repository.findByUser_Id(user.getId())
                .map(this::toDto)
                .orElseGet(() -> defaultDto(user));
    }

    @Transactional
    public PortfolioAiEmailDeliveryDto upsert(PortfolioAiEmailDeliveryUpsertRequest request) {
        validate(request);
        User user = currentUserResolver.getOrCreateCurrentUser();
        Instant now = Instant.now();
        String email = normalizeEmail(request.email());
        PortfolioAiEmailDeliveryFrequency frequency = request.frequency();

        PortfolioAiEmailDeliveryEntity entity = repository.findByUser_Id(user.getId())
                .orElseGet(() -> new PortfolioAiEmailDeliveryEntity(
                        user,
                        false,
                        null,
                        PortfolioAiEmailDeliveryFrequency.WEEKLY,
                        now
                ));

        entity.update(request.enabled(), email, frequency, now);
        repository.save(entity);
        return toDto(entity);
    }

    private void validate(PortfolioAiEmailDeliveryUpsertRequest request) {
        if (!request.enabled()) {
            return;
        }
        if (request.email() == null || request.email().isBlank()) {
            throw badRequest("E-posta adresi zorunludur.");
        }
        if (!isValidEmail(request.email().trim())) {
            throw badRequest("Geçerli bir e-posta adresi girin.");
        }
        if (request.frequency() == null) {
            throw badRequest("Haftalık veya aylık gönderim seçilmelidir.");
        }
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private PortfolioAiEmailDeliveryDto defaultDto(User user) {
        String email = user.getEmail() != null ? user.getEmail().trim() : "";
        return new PortfolioAiEmailDeliveryDto(
                false,
                email.isEmpty() ? null : email,
                PortfolioAiEmailDeliveryFrequency.WEEKLY,
                null
        );
    }

    private PortfolioAiEmailDeliveryDto toDto(PortfolioAiEmailDeliveryEntity entity) {
        return new PortfolioAiEmailDeliveryDto(
                entity.isEnabled(),
                entity.getEmail(),
                entity.getFrequency() != null ? entity.getFrequency() : PortfolioAiEmailDeliveryFrequency.WEEKLY,
                entity.getUpdatedAt()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private ApiBusinessException badRequest(String message) {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }
}
