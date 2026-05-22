package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.bankfx.BankFxLatest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BankFxLatestRepository extends JpaRepository<BankFxLatest, Long> {

    Optional<BankFxLatest> findBySourceAndBankCodeAndCurrency(String source, String bankCode, String currency);

    List<BankFxLatest> findBySourceAndCurrencyOrderByBankNameAsc(String source, String currency);

    @Query("select max(b.fetchedAt) from BankFxLatest b where b.source = :source")
    Optional<Instant> findMaxFetchedAtBySource(@Param("source") String source);
}
