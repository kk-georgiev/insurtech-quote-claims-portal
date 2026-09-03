package com.motorinsurance.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity backing {@code claims} (mirrors {@code
 * V10__create_claims_table.sql} exactly - {@code ddl-auto: validate}
 * requires it), the way {@code policy.domain.Policy} mirrors {@code V9}.
 *
 * <p>{@code policyId} is a real foreign key, unlike {@code Policy}'s
 * deliberately-bare {@code quoteId} (M4-AD-5): the referent here is already
 * immutable (a policy copies rather than references, M3 AD-4), so nothing
 * underneath a claim can drift, and there is no idempotency race to guard
 * against the way policy issuance has. Only {@code policyNumber} is copied,
 * and only so a claim can be listed without a join.
 *
 * <p>{@code status} is stored, not derived (M4-AD-1) - see {@link
 * ClaimStatus}'s own javadoc. This entity carries no setter for it beyond
 * what its constructor fixes to {@link ClaimStatus#SUBMITTED}: Story 11.1
 * adds the transition operation that is meant to be its only other writer.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "claim_number", nullable = false)
    private String claimNumber;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** JPA-only. */
    protected Claim() {
    }

    public Claim(
            UUID customerId,
            UUID policyId,
            String policyNumber,
            String claimNumber,
            LocalDate incidentDate,
            String description,
            String location,
            Instant submittedAt) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.policyId = policyId;
        this.policyNumber = policyNumber;
        this.claimNumber = claimNumber;
        this.incidentDate = incidentDate;
        this.description = description;
        this.location = location;
        // Set by the backend, never accepted from the caller (M4-AD-1).
        this.status = ClaimStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
