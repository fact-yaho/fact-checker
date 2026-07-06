package com.yaho.factchecker.domain.claim.repository;

import com.yaho.factchecker.domain.claim.entity.Claim;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

}
