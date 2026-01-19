package com.nurseli.nrsfinanceportal.integration;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.service.FundsWithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class BalanceConcurrencyTest {

    @Autowired
    private FundsWithdrawalService withdrawalService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void concurrentWithdraw_shouldNotCreateNegativeBalance() throws Exception {

        // ⚠️ DB’de var olan bir account ID
        Account account = accountRepository.findById(7L)
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        Runnable task = () -> {
            try {
                ready.countDown();
                start.await(); //  AYNI ANDA BAŞLASINLAR
                withdrawalService.withdraw(
                        account,
                        BigDecimal.valueOf(700)
                );
                System.out.println("SUCCESS");
            } catch (Exception e) {
                System.out.println("FAILED: " + e.getMessage());
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
    }
}
