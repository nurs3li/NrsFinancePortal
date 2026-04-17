package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundRequestRepository extends JpaRepository<FundRequest, Long> {

    List<FundRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FundRequest> findByStatusOrderByCreatedAtAsc(FundRequestStatus status);
}