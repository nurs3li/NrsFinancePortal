package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.viop.ViopPriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ViopPriceHistoryRepository extends JpaRepository<ViopPriceHistoryEntity, Long> {

    List<ViopPriceHistoryEntity> findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
            String contractCode, LocalDateTime from, LocalDateTime to);

    List<ViopPriceHistoryEntity> findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
            String contractCode, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    Optional<ViopPriceHistoryEntity> findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(
            String contractCode, LocalDateTime exclusiveUpper);

    Optional<ViopPriceHistoryEntity> findTopByContractCodeAndPriceTimeLessThanEqualOrderByPriceTimeDesc(
            String contractCode, LocalDateTime atOrBefore);

    Optional<ViopPriceHistoryEntity> findTopByContractCodeOrderByPriceTimeDesc(String contractCode);

    long countByContractCodeAndPriceTimeBetween(String contractCode, LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO mds_viop_price_history
                    (contract_code, underlying, asset_class, segment, price_time, price, period_minutes, source, provider_timestamp, created_at)
                    VALUES (:contractCode, :underlying, :assetClass, :segment, :priceTime, :price, :periodMinutes, :source, :providerTimestamp, NOW())
                    ON CONFLICT (contract_code, price_time, period_minutes, source) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIgnore(
            @Param("contractCode") String contractCode,
            @Param("underlying") String underlying,
            @Param("assetClass") String assetClass,
            @Param("segment") String segment,
            @Param("priceTime") LocalDateTime priceTime,
            @Param("price") BigDecimal price,
            @Param("periodMinutes") int periodMinutes,
            @Param("source") String source,
            @Param("providerTimestamp") LocalDateTime providerTimestamp);
}
