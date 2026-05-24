package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.tefas.TefasFundProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TefasFundProfileRepository extends JpaRepository<TefasFundProfile, String> {

    List<TefasFundProfile> findByCodeIn(Collection<String> codes);
}
