package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.FundRequestReviewRequest;
import com.nurseli.nrsfinanceportal.common.dto.FundRequestView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.FundRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fund-requests")
@RequiredArgsConstructor
public class AdminFundRequestController {

    private final FundRequestService fundRequestService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ApiResponse<List<FundRequestView>> pending() {
        return ApiResponse.success(
                fundRequestService.pendingRequests()
                        .stream()
                        .map(FundRequestView::from)
                        .toList()
        );
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ApiResponse<FundRequestView> claim(@PathVariable Long id) {
        return ApiResponse.success(
                FundRequestView.from(fundRequestService.claimPending(id))
        );
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ApiResponse<FundRequestView> approve(
            @PathVariable Long id,
            @RequestBody(required = false) FundRequestReviewRequest request
    ) {
        String reviewNote = request != null ? request.getReviewNote() : null;
        return ApiResponse.success(
                FundRequestView.from(fundRequestService.approve(id, reviewNote))
        );
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ApiResponse<FundRequestView> reject(
            @PathVariable Long id,
            @RequestBody(required = false) FundRequestReviewRequest request
    ) {
        String reviewNote = request != null ? request.getReviewNote() : null;
        return ApiResponse.success(
                FundRequestView.from(fundRequestService.reject(id, reviewNote))
        );
    }
}