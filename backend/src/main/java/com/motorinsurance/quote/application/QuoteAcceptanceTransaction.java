package com.motorinsurance.quote.application;

import com.motorinsurance.policy.application.IssuePolicyCommand;
import com.motorinsurance.policy.application.PolicyService;
import com.motorinsurance.policy.application.PolicyView;
import com.motorinsurance.quote.api.AcceptQuoteRequest;
import com.motorinsurance.quote.domain.Quote;
import com.motorinsurance.quote.domain.QuoteStatus;
import com.motorinsurance.quote.persistence.QuoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one transactional unit of work behind quote acceptance (BA 7.3,
 * Architecture Spine AD-1/AD-5, M3): validate ownership, validate the
 * offer, validate the acceptance input, mark the quote accepted, and issue
 * the policy - all inside one {@code @Transactional} method, so any failure
 * leaves neither an accepted quote nor a policy.
 *
 * <p>Split out from {@link QuoteAcceptanceService} rather than being a
 * method on it because the race path has to <em>leave</em> this transaction
 * to recover; see that class's javadoc for why, and why a self-invocation
 * would not work.
 *
 * <p>{@code quote} owns this sequence and calls exactly one
 * {@code policy.application} entry point with a fully-formed command
 * (AD-1). It never reaches into {@code policy}'s persistence, and
 * {@code policy} never reads back into {@code quotes}.
 */
@Service
public class QuoteAcceptanceTransaction {

    private final QuoteRepository quoteRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public QuoteAcceptanceTransaction(QuoteRepository quoteRepository, PolicyService policyService, Clock clock) {
        this.quoteRepository = quoteRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    @Transactional
    public AcceptanceOutcome acceptAndIssue(UUID quoteId, UUID customerId, AcceptQuoteRequest request) {
        // Ownership is in the query (AD-10): someone else's quote comes back
        // empty here and becomes the same 404 an unknown id does.
        Quote quote = quoteRepository
                .findByIdAndCustomerId(quoteId, customerId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));

        // The uncontended replay path (AD-5): already accepted means a
        // policy already exists, so no insert is attempted and the caller
        // gets that same policy back as a success, never a 409.
        if (quote.getAcceptedAt() != null) {
            return AcceptanceOutcome.existing(existingPolicy(quoteId, customerId));
        }

        LocalDate today = LocalDate.now(clock);
        if (quote.status(today) == QuoteStatus.EXPIRED) {
            throw new QuoteExpiredException(quoteId);
        }
        if (request.coverageStart().isBefore(today)) {
            throw new CoverageStartInPastException(request.coverageStart(), today);
        }

        String registration = normalized(request.vehicleRegistration());
        String vin = normalized(request.vehicleVin());
        // Exactly one, so the two nulls and the two non-nulls both fail.
        if ((registration == null) == (vin == null)) {
            throw new VehicleIdentifierRequiredException();
        }

        // One clock read for the whole acceptance: the quote's accepted_at
        // and the policy's issued_at are the same instant by construction,
        // and no midnight boundary can fall between the "today" checked
        // above and the year the policy number carries (AD-6).
        Instant acceptedAt = Instant.now(clock);
        quote.accept(acceptedAt);
        return AcceptanceOutcome.issued(
                policyService.issue(issuanceCommand(quote, request, acceptedAt, registration, vin)));
    }

    /**
     * Reads the policy an already-accepted quote produced. An accepted quote
     * without one is unreachable by construction - both happen in this one
     * transaction - so its absence is a broken invariant worth a logged 500,
     * not a reason to quietly issue a second policy.
     */
    private PolicyView existingPolicy(UUID quoteId, UUID customerId) {
        return policyService
                .findByQuoteId(quoteId, customerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Quote " + quoteId + " is marked accepted but has no policy"));
    }

    /**
     * Every value {@code policy} needs, already resolved (AD-1) - the
     * breakdown is copied from what the quote actually persisted, never
     * recalculated (NFR-1), which is what makes the policy's figures
     * immune to a later tariff change (AD-4).
     */
    private static IssuePolicyCommand issuanceCommand(
            Quote quote, AcceptQuoteRequest request, Instant issuedAt, String registration, String vin) {
        return new IssuePolicyCommand(
                quote.getId(),
                quote.getCustomerId(),
                issuedAt,
                request.coverageStart(),
                request.holderName().trim(),
                registration,
                vin,
                quote.getDriverAge(),
                quote.getRegionCode(),
                quote.getEngineCc(),
                quote.getZoneId(),
                quote.getZoneName(),
                quote.getBasePremium(),
                quote.getAgeSurcharge(),
                quote.getBonusMalusCode(),
                quote.getBonusMalusFactor(),
                quote.getOneTimePremium(),
                quote.getInstallments(),
                quote.getInstallmentFee(),
                quote.getTotalPremium(),
                quote.getInstallmentAmount(),
                quote.getCurrency());
    }

    /**
     * Blank counts as absent - a form submitting the identifier it did not
     * use as {@code ""} means "no VIN", not "an empty VIN". Uppercased for
     * the same reason {@code regionCode} is: the stored contract should
     * carry the canonical form, not whichever case was typed.
     */
    private static String normalized(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
