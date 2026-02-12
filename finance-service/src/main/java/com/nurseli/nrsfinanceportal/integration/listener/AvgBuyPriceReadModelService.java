package com.nurseli.nrsfinanceportal.integration.listener;

import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAsset;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics;
import com.nurseli.nrsfinanceportal.integration.kafka.event.TradeCreatedEvent;
import com.nurseli.nrsfinanceportal.repository.PortfolioAssetRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvgBuyPriceReadModelService {

    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;

    @KafkaListener(
            topics = KafkaTopics.TRADE_CREATED,
            groupId = "avg-buy-price-calculator-v2",
            containerFactory = "tradeCreatedKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTradeCreated(TradeCreatedEvent event) {

        // 1️⃣ User yükle
        User user = userRepository.findById(event.userId())
                .orElseThrow(() ->
                        new IllegalStateException("User not found id=" + event.userId())
                );

        // 2️⃣ İlgili asset
        PortfolioAsset asset = portfolioAssetRepository
                .findByUserAndTypeAndSymbol(
                        user,
                        event.assetType(),
                        event.symbol()
                )
                .orElse(null);

        if (asset == null) {
            log.warn("PortfolioAsset not found for tradeId={} user={} symbol={}",
                    event.tradeId(), event.userId(), event.symbol());
            return;
        }

        if (event.tradeType() == TradeType.BUY) {
            handleBuy(event, asset);
        } else if (event.tradeType() == TradeType.SELL) {
            handleSell(event, asset);
        }

        portfolioAssetRepository.save(asset);
    }

    private void handleBuy(TradeCreatedEvent event, PortfolioAsset asset) {

        BigDecimal totalQtyAfterTrade = asset.getQuantity();   // trade sonrası toplam
        BigDecimal newQty = event.quantity();                  // bu trade'in miktarı

        // trade öncesi miktar
        BigDecimal previousQty = totalQtyAfterTrade.subtract(newQty);

        // İlk alım (önce quantity = 0 veya avg null)
        if (asset.getAvgBuyPrice() == null
                || previousQty.compareTo(BigDecimal.ZERO) <= 0) {

            asset.setAvgBuyPrice(event.pricePerUnit());

            log.info("[AVG_BUY] First BUY set avgPrice={} user={} symbol={} qty={}",
                    event.pricePerUnit(),
                    asset.getUser().getId(),
                    asset.getSymbol(),
                    totalQtyAfterTrade
            );
            return;
        }

        BigDecimal oldAvg = asset.getAvgBuyPrice();

        BigDecimal previousTotalCost = previousQty.multiply(oldAvg);
        BigDecimal newCost = newQty.multiply(event.pricePerUnit());
        BigDecimal newTotalQty = previousQty.add(newQty); // = totalQtyAfterTrade

        if (newTotalQty.compareTo(BigDecimal.ZERO) <= 0) {
            asset.setAvgBuyPrice(null);
            log.info("[AVG_BUY] Position closed unexpectedly on BUY. user={} symbol={}",
                    asset.getUser().getId(),
                    asset.getSymbol()
            );
            return;
        }

        BigDecimal newAvg = previousTotalCost
                .add(newCost)
                .divide(newTotalQty, 8, RoundingMode.HALF_UP);

        asset.setAvgBuyPrice(newAvg);

        log.info(
                "[AVG_BUY] Updated avgPrice={} user={} symbol={} qtyAfter={} tradeQty={} tradePrice={}",
                newAvg,
                asset.getUser().getId(),
                asset.getSymbol(),
                totalQtyAfterTrade,
                newQty,
                event.pricePerUnit()
        );
    }

    private void handleSell(TradeCreatedEvent event, PortfolioAsset asset) {

        BigDecimal remainingQty = asset.getQuantity();

        if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
            // Pozisyon tamamen kapandı → avg'i temizle
            asset.setAvgBuyPrice(null);
            log.info("[AVG_BUY] Position closed, clearing avgPrice user={} symbol={}",
                    asset.getUser().getId(),
                    asset.getSymbol()
            );
        } else {
            // Kısmi satışta avg değişmez
            log.info("[AVG_BUY] SELL executed, keeping avgPrice={} user={} symbol={} remainingQty={}",
                    asset.getAvgBuyPrice(),
                    asset.getUser().getId(),
                    asset.getSymbol(),
                    remainingQty
            );
        }
    }
}