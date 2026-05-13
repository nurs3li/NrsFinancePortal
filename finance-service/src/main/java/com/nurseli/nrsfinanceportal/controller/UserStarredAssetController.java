package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.StarredAssetsResponse;
import com.nurseli.nrsfinanceportal.dto.StarredAssetsUpdateRequest;
import com.nurseli.nrsfinanceportal.service.UserStarredAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/starred-assets")
@RequiredArgsConstructor
public class UserStarredAssetController {

    private final UserStarredAssetService userStarredAssetService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public StarredAssetsResponse getMyStarredAssets() {
        return userStarredAssetService.getCurrentUserStarredAssets();
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PutMapping
    public StarredAssetsResponse updateMyStarredAssets(@RequestBody StarredAssetsUpdateRequest request) {
        return userStarredAssetService.updateCurrentUserStarredAssets(request);
    }
}
