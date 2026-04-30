package com.nurseli.nrsfinanceportal.integration;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.FundsWithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BalanceConcurrencyTest {

    @Autowired
    private FundsWithdrawalService withdrawalService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Test
    void concurrentWithdraw_shouldNotCreateNegativeBalance() throws Exception {
        User user = userRepository.save(User.createFromIdentity(
                "test-kc-" + System.nanoTime(),
                "concurrency@example.com",
                "concurrency-user"
        ));
        Account account = accountRepository.save(Account.create(AccountType.CASH, user));
        balanceRepository.save(Balance.of(account, BigDecimal.valueOf(1000)));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                ready.countDown();
                start.await(); //  AYNI ANDA BAŞLASINLAR
                withdrawalService.withdraw(
                        account,
                        BigDecimal.valueOf(700)
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                // expected for one of concurrent requests when balance becomes insufficient
            } finally {
                done.countDown();
            }
        };

        executor.submit(task);
        executor.submit(task);

        ready.await();
        start.countDown();
        done.await();

        executor.shutdown();

        Balance finalBalance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance missing after test"));

        assertTrue(finalBalance.getAmount().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(1, successCount.get(), "Only one withdrawal should succeed for 1000 balance with 700x2 race");
    }
}
