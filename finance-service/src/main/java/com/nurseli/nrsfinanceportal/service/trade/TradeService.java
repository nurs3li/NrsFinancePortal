package com.nurseli.nrsfinanceportal.service.trade;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAsset;
import com.nurseli.nrsfinanceportal.domain.trade.Trade;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.domain.pricing.PriceLookupService;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.event.TradeCreatedEvent;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.PortfolioAssetRepository;
import com.nurseli.nrsfinanceportal.repository.TradeRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.TransactionService;
import com.nurseli.nrsfinanceportal.service.TimelineCacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TradeService {

    private static final BigDecimal DEMO_INITIAL_BALANCE =
            new BigDecimal("1000000");

    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final TradeRepository tradeRepository;
    private final PriceLookupService priceLookupService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;

    // 🔥 EKLENEN SERVIS
    private final TimelineCacheInvalidationService timelineCacheInvalidationService;

    /**
     * 🔥 TEK GERÇEK MUTATION NOKTASI
     */
    @Transactional
    public TradeResponse execute(TradeRequest request) {

        // 1️⃣ Current User
        User user = currentUserResolver.getOrCreateCurrentUser();
        if (accountRepository.existsByUser_IdAndStatus(user.getId(), AccountStatus.FROZEN)) {
            throw new IllegalStateException("Hesap askıya alınmış. İşlem yapılamaz.");
        }
        // 2️⃣ DEMO Account
        Account demoAccount = ensureDemoAccount(user);
        if (demoAccount.isFrozen()) {
            throw new IllegalStateException("Hesap askıya alınmış. İşlem yapılamaz.");
        }

        // 3️⃣ Balance (FOR UPDATE)
        Balance balance = balanceRepository
                .findByAccountForUpdate(demoAccount)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        // 4️⃣ TRY fiyat
        BigDecimal tryPrice = priceLookupService.getTryPrice(
                request.assetType(),
                request.symbol()
        );

        BigDecimal totalTry = tryPrice.multiply(request.quantity());

        // 5️⃣ Portfolio Asset
        PortfolioAsset asset = portfolioAssetRepository
                .findByUserAndTypeAndSymbol(
                        user,
                        request.assetType(),
                        request.symbol()
                )
                .orElse(null);

        /* ======================
           BUY
           ====================== */
        if (request.tradeType() == TradeType.BUY) {

            BigDecimal balanceAfter = balance.decrease(totalTry);

            Transaction tx = transactionService.record(
                    demoAccount,
                    totalTry,
                    TransactionType.WITHDRAW,
                    balanceAfter
            );

            Trade trade = Trade.create(
                    user,
                    request.tradeType(),
                    request.assetType(),
                    request.symbol(),
                    request.quantity(),
                    tx.getId()
            );
            tradeRepository.save(trade);

            if (asset == null) {
                asset = PortfolioAsset.create(
                        user,
                        request.assetType(),
                        request.symbol(),
                        request.quantity()
                );
            } else {
                asset.increase(request.quantity());
            }

            portfolioAssetRepository.save(asset);

// 🔥 TIMELINE CACHE INVALIDATE
            timelineCacheInvalidationService.invalidateUserTimeline(user.getId());

// ✅ Event'te sembol her zaman normalize (BTC -> BTCUSDT) metriklerde tek görünsün
            String normalizedSymbol = SymbolNormalizer.normalize(request.assetType(), request.symbol());
            eventPublisher.publishEvent(
                    new TradeCreatedEvent(
                            trade.getId(),
                            user.getId(),
                            request.tradeType(),
                            request.assetType(),
                            normalizedSymbol,
                            request.quantity(),
                            tryPrice,
                            totalTry,
                            Instant.now()
                    )
            );

            return response(request, tryPrice, totalTry, balanceAfter);


        }

        /* ======================
           SELL
           ====================== */
        if (asset == null || asset.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalStateException("Insufficient asset quantity");
        }

        asset.decrease(request.quantity());

        BigDecimal balanceAfter = balance.increase(totalTry);

        Transaction tx = transactionService.record(
                demoAccount,
                totalTry,
                TransactionType.DEPOSIT,
                balanceAfter
        );

        Trade trade = Trade.create(
                user,
                request.tradeType(),
                request.assetType(),
                request.symbol(),
                request.quantity(),
                tx.getId()
        );
        tradeRepository.save(trade);

        if (asset.getQuantity().signum() == 0) {
            portfolioAssetRepository.delete(asset);
        } else {
            portfolioAssetRepository.save(asset);
        }

        // 🔥 TIMELINE CACHE INVALIDATE
        timelineCacheInvalidationService.invalidateUserTimeline(user.getId());

        return response(request, tryPrice, totalTry, balanceAfter);
    }

    /* ======================
       DEMO ACCOUNT
       ====================== */
    private Account ensureDemoAccount(User user) {

        return accountRepository
                .findByUserAndType(user, AccountType.DEMO)
                .orElseGet(() -> {

                    Account demo = accountRepository.save(
                            Account.create(AccountType.DEMO, user)
                    );

                    Balance balance = new Balance(demo);
                    balance.increase(DEMO_INITIAL_BALANCE);
                    balanceRepository.save(balance);

                    return demo;
                });
    }

    /* ======================
       RESPONSE
       ====================== */
    private TradeResponse response(
            TradeRequest request,
            BigDecimal tryPrice,
            BigDecimal totalTry,
            BigDecimal balanceAfter
    ) {
        return new TradeResponse(
                request.symbol(),
                request.quantity(),
                tryPrice,
                totalTry,
                balanceAfter,
                LocalDateTime.now()
        );
    }
}
