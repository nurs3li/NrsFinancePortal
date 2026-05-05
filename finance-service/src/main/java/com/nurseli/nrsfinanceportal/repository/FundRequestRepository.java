package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FundRequestRepository extends JpaRepository<FundRequest, Long> {

    List<FundRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FundRequest> findByStatusOrderByCreatedAtAsc(FundRequestStatus status);

    List<FundRequest> findByStatusAndAssignedFmKeycloakIdIsNullOrderByCreatedAtAsc(FundRequestStatus status);

    List<FundRequest> findByStatusAndAssignedFmKeycloakIdOrderByCreatedAtAsc(FundRequestStatus status, String assignedFmKeycloakId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FundRequest r SET r.assignedFmKeycloakId = :fmSub, r.claimedAt = :now, r.updatedAt = :now
            WHERE r.id = :id AND r.status = :pending
              AND (r.assignedFmKeycloakId IS NULL OR r.assignedFmKeycloakId = :fmSub)
            """)
    int tryClaimByFm(
            @Param("id") Long id,
            @Param("fmSub") String fmSub,
            @Param("now") Instant now,
            @Param("pending") FundRequestStatus pending
    );
}