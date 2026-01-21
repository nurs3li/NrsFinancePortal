package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.TransactionReversalRequest;
import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import com.nurseli.nrsfinanceportal.service.TransactionReversalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class TransactionAdminController {

    private final TransactionRepository transactionRepository;
    private final TransactionReversalService reversalService;

    @PostMapping("/reverse")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER')")
    public ApiResponse<TransactionView> reverse(
            @RequestBody TransactionReversalRequest request
    ) {
        Transaction reversal = reversalService.reverse(request.getTransactionId());
        return ApiResponse.success(TransactionView.from(reversal));
    }

}
