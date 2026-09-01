package com.motorinsurance.policy.application;

import com.motorinsurance.policy.domain.CoveragePeriod;
import com.motorinsurance.policy.domain.Policy;
import com.motorinsurance.policy.domain.PolicyNumber;
import com.motorinsurance.policy.persistence.PolicyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code policy} module's only entry point (Architecture Spine AD-1,
 * M3): {@link #issue} creates a policy from a fully-formed command, and
 * {@link #findByQuoteId} reads back the one a quote was already accepted
 * into. Nothing here reads {@code quotes} - this module imports no
 * {@code quote} type and holds no reference to that table, so it is
 * reusable unchanged by a future caller (an AGENT issuing on a client's
 * behalf) that is not the client-acceptance path.
 *
 * <p>{@code clock} is the shared business-zone clock ({@code
 * shared.config.ClockConfig}, AD-6): both {@code issuedAt} and the year in
 * the policy number come from it, never from {@code Instant.now()} or the
 * JVM default zone.
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    /**
     * The constraint that guarantees one policy per quote (AD-5). Matched
     * by name below so that some other integrity failure - a bad
     * {@code customer_id}, a violated vehicle-identity check - is never
     * silently reported as "already issued".
     */
    private static final String QUOTE_ID_UNIQUE_CONSTRAINT = "uq_policies_quote_id";

    private final PolicyRepository policyRepository;
    private final Clock clock;
    private final int coverageMonths;

    public PolicyService(
            PolicyRepository policyRepository, Clock clock, @Value("${policy.coverage-months}") int coverageMonths) {
        if (coverageMonths < 1) {
            // Fails startup rather than every acceptance: a non-positive
            // period would put coverage_end before coverage_start and be
            // caught only by ck_policies_coverage_period, as a 500 at the
            // first acceptance. Same fail-fast posture as
            // auth.config.DeploymentSecretsGuard.
            throw new IllegalArgumentException("policy.coverage-months must be at least 1, but was " + coverageMonths);
        }
        this.policyRepository = policyRepository;
        this.clock = clock;
        this.coverageMonths = coverageMonths;
    }

    /**
     * Issues one policy. Runs inside the caller's transaction (the
     * acceptance sequence is one unit of work, AD-5), so a failure here
     * leaves neither a policy nor an accepted quote.
     *
     * <p>The coverage period is this module's own rule (see {@link
     * CoveragePeriod}), with its length read from
     * {@code policy.coverage-months} rather than written as a literal at a
     * call site, and both ends are inclusive (AD-6) - hence the
     * {@code minusDays(1)}.
     *
     * @throws PolicyAlreadyIssuedException if this insert lost a concurrent
     *     race for the same quote - see that class for why it is thrown
     *     rather than recovered from here.
     */
    @Transactional
    public PolicyView issue(IssuePolicyCommand command) {
        // The caller's single clock read, not a second one of our own: see
        // IssuePolicyCommand#issuedAt for the midnight boundary that would
        // otherwise open up between validation and issuance. The clock is
        // still needed for its zone - the year must be the business zone's,
        // not UTC's.
        Instant issuedAt = command.issuedAt();
        int issuanceYear = LocalDate.ofInstant(issuedAt, clock.getZone()).getYear();
        String policyNumber = PolicyNumber.format(issuanceYear, policyRepository.nextPolicyNumberValue());
        LocalDate coverageEnd = CoveragePeriod.endFor(command.coverageStart(), coverageMonths);

        Policy policy = new Policy(
                command.customerId(),
                command.quoteId(),
                policyNumber,
                command.holderName(),
                command.vehicleRegistration(),
                command.vehicleVin(),
                command.coverageStart(),
                coverageEnd,
                issuedAt,
                command.driverAge(),
                command.regionCode(),
                command.engineCc(),
                command.zoneId(),
                command.zoneName(),
                command.basePremium(),
                command.ageSurcharge(),
                command.bonusMalusClass(),
                command.bonusMalusFactor(),
                command.oneTimePremium(),
                command.installments(),
                command.installmentFee(),
                command.totalPremium(),
                command.installmentAmount(),
                command.currency());

        try {
            // saveAndFlush, not save: forces the INSERT now, inside this
            // try/catch, rather than at the surrounding transaction's commit
            // where the violation would surface outside any catch block -
            // the same pattern QuoteService.calculate already uses.
            return toView(policyRepository.saveAndFlush(policy));
        } catch (DataIntegrityViolationException ex) {
            if (!violatesQuoteIdUniqueness(ex)) {
                throw ex;
            }
            log.info("Policy insert for quote {} lost the unique-constraint race; caller will re-read", command.quoteId());
            throw new PolicyAlreadyIssuedException(command.quoteId(), ex);
        }
    }

    /** Owner-scoped read of the policy a quote was accepted into (AD-10). */
    @Transactional(readOnly = true)
    public Optional<PolicyView> findByQuoteId(UUID quoteId, UUID customerId) {
        return policyRepository.findByQuoteIdAndCustomerId(quoteId, customerId).map(this::toView);
    }

    /**
     * Owner-scoped detail read (Story 8.3, FR-M3-10). A policy that is not
     * this customer's is indistinguishable from one that does not exist.
     */
    @Transactional(readOnly = true)
    public PolicyView getById(UUID id, UUID customerId) {
        return policyRepository
                .findByIdAndCustomerId(id, customerId)
                .map(this::toView)
                .orElseThrow(() -> new PolicyNotFoundException(id));
    }

    /**
     * Owner-scoped list, newest first (Story 8.3, AD-10/AD-12) - the same
     * {@link PolicyView} the detail read returns, not a slimmed-down
     * summary, so one shape serves both screens.
     */
    @Transactional(readOnly = true)
    public List<PolicyView> listForCustomer(UUID customerId) {
        return policyRepository.findAllByCustomerIdOrderByIssuedAtDesc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Which of these quotes have become policies, as quote id to policy id
     * (Story 8.3). One query for a whole list, never one per row - a quote
     * list of any length costs the same single lookup.
     *
     * <p>Takes quote ids as plain values and returns plain values: no join
     * to {@code quotes}, no {@code Quote} type imported, nothing
     * dereferenced (AD-1, AD-4). {@code quote} calls this; {@code policy}
     * still knows nothing about it.
     */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findPolicyIdsByQuoteIds(UUID customerId, Collection<UUID> quoteIds) {
        if (quoteIds.isEmpty()) {
            return Map.of();
        }
        return policyRepository.findAllByCustomerIdAndQuoteIdIn(customerId, quoteIds).stream()
                .collect(Collectors.toMap(Policy::getQuoteId, Policy::getId));
    }

    /**
     * True only for the {@code quote_id} uniqueness failure - never for a
     * bad {@code customer_id} or a violated vehicle-identity check, which
     * must keep propagating as the errors they are.
     *
     * <p>Reads the constraint name structurally where Hibernate provides it
     * rather than pattern-matching prose, and falls back to the driver's
     * message only if some layer wraps the cause differently. The name
     * itself is one this project chose in {@code
     * V9__create_policies_table.sql}, so neither route depends on the
     * server's message locale.
     */
    private static boolean violatesQuoteIdUniqueness(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException hibernateCause) {
            return QUOTE_ID_UNIQUE_CONSTRAINT.equalsIgnoreCase(hibernateCause.getConstraintName());
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(QUOTE_ID_UNIQUE_CONSTRAINT);
    }

    /**
     * Derives the status here rather than storing it (AD-3), from the one
     * injected business-zone clock (AD-6) - which is why this is an
     * instance method where every other mapper in this codebase is static.
     */
    private PolicyView toView(Policy policy) {
        return new PolicyView(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getQuoteId(),
                policy.getIssuedAt(),
                policy.getCoverageStart(),
                policy.getCoverageEnd(),
                policy.getHolderName(),
                policy.getVehicleRegistration(),
                policy.getVehicleVin(),
                policy.getDriverAge(),
                policy.getRegionCode(),
                policy.getEngineCc(),
                policy.getZoneId(),
                policy.getZoneName(),
                policy.getBasePremium(),
                policy.getAgeSurcharge(),
                policy.getBonusMalusCode(),
                policy.getBonusMalusFactor(),
                policy.getOneTimePremium(),
                policy.getInstallments(),
                policy.getInstallmentFee(),
                policy.getTotalPremium(),
                policy.getInstallmentAmount(),
                policy.getCurrency(),
                policy.status(LocalDate.now(clock)));
    }
}
