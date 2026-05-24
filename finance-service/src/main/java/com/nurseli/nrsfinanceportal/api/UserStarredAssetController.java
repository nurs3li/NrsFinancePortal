package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.StarredAssetsResponse;
import com.nurseli.nrsfinanceportal.api.dto.StarredAssetsUpdateRequest;
import com.nurseli.nrsfinanceportal.application.UserStarredAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Kullanıcının terminalde yıldızladığı varlık seçimlerini okuma ve güncelleme endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/me/starred-assets", "/api/me/starred-assets"})
@RequiredArgsConstructor
public class UserStarredAssetController {

    private final UserStarredAssetService userStarredAssetService;

    /**
     * {@code getMyStarredAssets} — Oturum açmış kullanıcının yıldızlı varlık listesini döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public StarredAssetsResponse getMyStarredAssets() {
        return userStarredAssetService.getCurrentUserStarredAssets();
    }

    /**
     * {@code updateMyStarredAssets} — Yıldızlı varlık seçimini request gövdesiyle değiştirir.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PutMapping
    public StarredAssetsResponse updateMyStarredAssets(@RequestBody StarredAssetsUpdateRequest request) {
        return userStarredAssetService.updateCurrentUserStarredAssets(request);
    }
}
