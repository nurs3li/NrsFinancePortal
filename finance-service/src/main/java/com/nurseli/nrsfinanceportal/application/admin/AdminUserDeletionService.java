package com.nurseli.nrsfinanceportal.application.admin;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserDeletionClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualBondPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualViopPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioAiAnalysisRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioAiEmailDeliveryRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioValueSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PriceAlertRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserStarredAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * finance-service admin kullanıcı silme servisi — portal verisini temizler ve ardından Keycloak kullanıcısını siler.
 */
@RequiredArgsConstructor
@Service

public class AdminUserDeletionService {

    private final UserRepository userRepository;
    private final KeycloakUserDeletionClient keycloakUserDeletionClient;
    private final UserStarredAssetRepository userStarredAssetRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final ManualPortfolioPositionRepository manualPortfolioPositionRepository;
    private final ManualBondPositionRepository manualBondPositionRepository;
    private final ManualViopPositionRepository manualViopPositionRepository;
    private final PortfolioValueSnapshotRepository portfolioValueSnapshotRepository;
    private final PortfolioAiAnalysisRepository portfolioAiAnalysisRepository;
    private final PortfolioAiEmailDeliveryRepository portfolioAiEmailDeliveryRepository;

    /**
     * {@code deleteUser} — Hedef kullanıcının portal kayıtlarını (portfolio, bond, VIOP, snapshot, price alert, AI analiz vb.) siler, Keycloak'tan kullanıcıyı kaldırır ve yerel User satırını siler; son admin ve kendi hesabı silinemez.
     */
    @Transactional
    public void deleteUser(Long userId, String adminKeycloakSub) {
        if (adminKeycloakSub == null || adminKeycloakSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT subject bulunamadı");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı: " + userId));

        if (adminKeycloakSub.equals(target.getKeycloakUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi hesabınızı silemezsiniz");
        }

        if (target.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Son yönetici hesabı silinemez");
            }
        }

        if (!keycloakUserDeletionClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin client yapılandırılmadı (KEYCLOAK_ADMIN_*).");
        }

        String keycloakUserId = target.getKeycloakUserId();
        purgePortalData(userId);

        try {
            keycloakUserDeletionClient.deleteUser(keycloakUserId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak kullanıcı silme başarısız: " + e.getMessage(), e);
        }

        userRepository.delete(target);
    }

    /**
     * {@code purgePortalData} — Kullanıcıya bağlı tüm portal tablolarındaki veriyi sırayla siler.
     */
    private void purgePortalData(Long userId) {
        userStarredAssetRepository.deleteByUserId(userId);
        priceAlertRepository.deleteByUser_Id(userId);
        manualPortfolioPositionRepository.deleteByUser_Id(userId);
        manualBondPositionRepository.deleteByUser_Id(userId);
        manualViopPositionRepository.deleteByUser_Id(userId);
    portfolioValueSnapshotRepository.deleteByUser_Id(userId);
        portfolioAiEmailDeliveryRepository.deleteByUser_Id(userId);
        portfolioAiAnalysisRepository.deleteByUser_Id(userId);
    }
}
