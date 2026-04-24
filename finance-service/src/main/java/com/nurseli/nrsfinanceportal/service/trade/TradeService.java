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

    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final TradeRepository tradeRepository;
    private final PriceLookupService priceLookupService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final TimelineCacheInvalidationService timelineCacheInvalidationService;

    @Transactional
    public TradeResponse execute(TradeRequest request) {

        User user = currentUserResolver.getOrCreateCurrentUser();
        if (accountRepository.existsByUser_IdAndStatus(user.getId(), AccountStatus.FROZEN)) {
            throw new IllegalStateException("Hesap askıya alınmış. İşlem yapılamaz.");
        }

        Account cashAccount = ensureCashAccount(user);
        if (cashAccount.isFrozen()) {
            throw new IllegalStateException("Hesap askıya alınmış. İşlem yapılamaz.");
        }

        Balance balance = balanceRepository
                .findByAccountForUpdate(cashAccount)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal tryPrice = priceLookupService.getTryPrice(
                request.assetType(),
                request.symbol()
        );

        BigDecimal totalTry = tryPrice.multiply(request.quantity());

        PortfolioAsset asset = portfolioAssetRepository
                .findByUserAndTypeAndSymbol(
                        user,
                        request.assetType(),
                        request.symbol()
                )
                .orElse(null);

        if (request.tradeType() == TradeType.BUY) {

            if (balance.getAmount().compareTo(totalTry) < 0) {
                throw new IllegalStateException(
                        "Yetersiz bakiye. Trade için önce para yatırma talebi oluşturun."
                );
            }

            BigDecimal balanceAfter = balance.decrease(totalTry);

            Transaction tx = transactionService.record(
                    cashAccount,
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

            timelineCacheInvalidationService.invalidateUserTimeline(user.getId());

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

        if (asset == null || asset.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalStateException("Insufficient asset quantity");
        }

        asset.decrease(request.quantity());

        BigDecimal balanceAfter = balance.increase(totalTry);

        Transaction tx = transactionService.record(
                cashAccount,
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

        timelineCacheInvalidationService.invalidateUserTimeline(user.getId());

        String normalizedSymbolSell = SymbolNormalizer.normalize(request.assetType(), request.symbol());
        eventPublisher.publishEvent(
                new TradeCreatedEvent(
                        trade.getId(),
                        user.getId(),
                        request.tradeType(),
                        request.assetType(),
                        normalizedSymbolSell,
                        request.quantity(),
                        tryPrice,
                        totalTry,
                        Instant.now()
                )
        );

        return response(request, tryPrice, totalTry, balanceAfter);
    }

    private Account ensureCashAccount(User user) {
        return accountRepository
                .findByUserAndType(user, AccountType.CASH)
                .orElseThrow(() -> new IllegalStateException("Cash account not found"));
    }

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