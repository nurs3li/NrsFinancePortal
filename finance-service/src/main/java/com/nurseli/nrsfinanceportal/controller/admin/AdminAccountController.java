package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.AdminAccountView;
import com.nurseli.nrsfinanceportal.common.dto.FreezeRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountRepository accountRepository;
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

    @PostMapping("/{id}/freeze")
    public ResponseEntity<ApiResponse<String>> freeze(
            @PathVariable Long id,
            @RequestBody(required = false) FreezeRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + id));
        User user = account.getUser();
        String reason = request != null && request.reason() != null ? request.reason() : "Admin freeze";
        Instant now = Instant.now();
        account.freeze(now, reason);
        accountRepository.save(account);

        String sub = user.getKeycloakUserId();
        String freezeBody = """
                Merhaba,

                Yönetici aksiyonu ile hesabınız güvenlik kapsamında geçici olarak dondurulmuştur.

                Açıklama: %s

                İnceleme süreci tamamlanıncaya kadar işlem yapılamayabilir.

                NRS Finance Portal
                """.formatted(reason);
        notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                sub,
                "Hesabınız güvenlik nedeniyle donduruldu",
                freezeBody,
                "ACCOUNT_FROZEN",
                "user",
                user.getId()
        ));

        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<ApiResponse<String>> unfreeze(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + id));
        User user = account.getUser();
        account.unfreeze();
        accountRepository.save(account);

        String sub = user.getKeycloakUserId();
        notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                sub,
                "Hesabınız tekrar kullanıma açıldı",
                """
                        Merhaba,

                        Hesabınız üzerindeki inceleme tamamlanmış ve hesabınız yeniden aktifleştirilmiştir.

                        İyi günler dileriz,
                        NRS Finance Portal
                        """,
                "ACCOUNT_UNFROZEN",
                "user",
                user.getId()
        ));

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
        List<Account> accounts = freezeAllAccounts(userId, Instant.now(), reason);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            String freezeAllBody = """
                    Merhaba,

                    Yönetici tarafından kullanıcı hesabınıza bağlı tüm hesaplar güvenlik kapsamında dondurulmuştur.

                    Açıklama: %s

                    NRS Finance Portal
                    """.formatted(reason);
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    user.getKeycloakUserId(),
                    "Hesaplarınız güvenlik nedeniyle donduruldu",
                    freezeAllBody,
                    "ACCOUNT_FROZEN",
                    "user",
                    userId
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(accounts.size()));
    }

    @PostMapping("/by-user/{userId}/unfreeze-all")
    public ResponseEntity<ApiResponse<Integer>> unfreezeAllAccountsForUser(@PathVariable Long userId) {
        List<Account> accounts = unfreezeAllAccounts(userId);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    user.getKeycloakUserId(),
                    "Hesaplarınız tekrar kullanıma açıldı",
                    """
                            Merhaba,

                            Kullanıcı hesabınıza bağlı tüm hesaplar yönetici tarafından yeniden aktifleştirilmiştir.

                            İyi günler dileriz,
                            NRS Finance Portal
                            """,
                    "ACCOUNT_UNFROZEN",
                    "user",
                    userId
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(accounts.size()));
    }

    private List<Account> freezeAllAccounts(Long userId, Instant at, String reason) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        for (Account a : accounts) {
            a.freeze(at, reason);
        }
        return accountRepository.saveAll(accounts);
    }

    private List<Account> unfreezeAllAccounts(Long userId) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        accounts.forEach(Account::unfreeze);
        return accountRepository.saveAll(accounts);
    }
}