package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.AdminAccountView;
import com.nurseli.nrsfinanceportal.common.dto.FreezeRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountRepository accountRepository;

    @PostMapping("/{id}/freeze")
    public ResponseEntity<ApiResponse<String>> freeze(
            @PathVariable Long id,
            @RequestBody(required = false) FreezeRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + id));
        account.freeze(Instant.now(), request != null && request.reason() != null ? request.reason() : "Admin freeze");
        accountRepository.save(account);
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<ApiResponse<String>> unfreeze(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + id));
        account.unfreeze();
        accountRepository.save(account);
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminAccountView>>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        AccountStatus statusFilter = status != null ? AccountStatus.valueOf(status) : null;
        Page<Account> page = statusFilter != null
                ? accountRepository.findByStatus(statusFilter, pageable)
                : accountRepository.findAll(pageable);
        Page<AdminAccountView> viewPage = page.map(AdminAccountView::from);
        return ResponseEntity.ok(ApiResponse.success(viewPage));
    }
    @PostMapping("/by-user/{userId}/freeze-all")
    public ResponseEntity<ApiResponse<Integer>> freezeAllAccountsForUser(
            @PathVariable Long userId,
            @RequestBody(required = false) FreezeRequest request) {
        String reason = request != null && request.reason() != null ? request.reason() : "Admin freeze (all accounts)";
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        Instant at = Instant.now();
        for (Account a : accounts) {
            a.freeze(at, reason);
        }
        accountRepository.saveAll(accounts);
        return ResponseEntity.ok(ApiResponse.success(accounts.size()));
    }

    @PostMapping("/by-user/{userId}/unfreeze-all")
    public ResponseEntity<ApiResponse<Integer>> unfreezeAllAccountsForUser(@PathVariable Long userId) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        accounts.forEach(Account::unfreeze);
        accountRepository.saveAll(accounts);
        return ResponseEntity.ok(ApiResponse.success(accounts.size()));
    }
}