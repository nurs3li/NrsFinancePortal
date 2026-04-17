package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.FundRequestCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.FundRequestView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.FundRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fund-requests")
@RequiredArgsConstructor
public class FundRequestController {

    private final FundRequestService fundRequestService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<FundRequestView> create(
            @Valid @RequestBody FundRequestCreateRequest request
    ) {
        return ApiResponse.success(
                FundRequestView.from(fundRequestService.createMyRequest(request))
        );
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<FundRequestView>> myRequests() {
        return ApiResponse.success(
                fundRequestService.myRequests()
                        .stream()
                        .map(FundRequestView::from)
                        .toList()
        );
    }
}