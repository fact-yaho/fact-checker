package com.yaho.factchecker.domain.claim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
@Table(name = "claim_country")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimCountry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "claim_country_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", columnDefinition = "uuid", nullable = false)
    private Claim claim;

    @Column(name = "country_name", length = 100, nullable = false)
    private String name;

    @Column(name = "country_code", length = 10)
    private String code;

    @Builder
    private ClaimCountry(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public void assignClaim(Claim claim) {
        this.claim = claim;
    }
}
