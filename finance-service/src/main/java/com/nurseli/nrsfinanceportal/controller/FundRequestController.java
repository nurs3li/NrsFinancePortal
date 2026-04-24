package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.FundRequestCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.FundRequestView;
import com.nurseli.nrsfinanceportal.common.dto.ReceiptUploadResponseDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.FundReceiptStorageService;
import com.nurseli.nrsfinanceportal.service.FundRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/fund-requests")
@RequiredArgsConstructor
public class FundRequestController {

    private final FundRequestService fundRequestService;
    private final FundReceiptStorageService fundReceiptStorageService;

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

    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ReceiptUploadResponseDto> uploadReceipt(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fundReceiptStorageService.store(file));
    }

    @GetMapping("/receipts/{receiptId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Resource> getReceipt(@PathVariable String receiptId) {
        FundReceiptStorageService.StoredReceipt receipt = fundReceiptStorageService.load(receiptId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(receipt.originalFileName())
                        .build()
                        .toString())
                .contentType(receipt.mediaType())
                .body(receipt.resource());
    }
}