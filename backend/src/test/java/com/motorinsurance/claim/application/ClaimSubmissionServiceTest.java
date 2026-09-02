package com.motorinsurance.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motorinsurance.claim.persistence.AttachmentRepository;
import com.motorinsurance.claim.persistence.ClaimRepository;
import com.motorinsurance.policy.application.PolicyService;
import com.motorinsurance.policy.application.PolicyView;
import com.motorinsurance.policy.domain.PolicyStatus;
import com.motorinsurance.shared.storage.AttachmentValidator;
import com.motorinsurance.shared.storage.AttachmentValidator.Candidate;
import com.motorinsurance.shared.storage.ImageType;
import com.motorinsurance.shared.storage.Storage;
import com.motorinsurance.shared.storage.StoredFile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the one branch the full-stack {@code ClaimControllerTest} cannot
 * reliably reach through HTTP alone: a DB failure <em>after</em> a photo has
 * already been stored (Story 10.2, M4-AD-3's "best-effort delete, but
 * correctness never depends on it succeeding"). The collaborators are
 * mocked, matching {@code auth.application.RegistrationServiceTest}'s style
 * - forcing a real unique-constraint violation deterministically through a
 * shared Postgres sequence would be fragile, where mocking
 * {@link ClaimRepository#saveAndFlush} is not.
 */
@ExtendWith(MockitoExtension.class)
class ClaimSubmissionServiceTest {

    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), SOFIA_ZONE);

    @Mock
    private PolicyService policyService;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentValidator attachmentValidator;

    @Mock
    private Storage storage;

    private ClaimSubmissionService service;

    @BeforeEach
    void setUp() {
        // Built here, not as a field initializer: Mockito's @Mock fields are
        // populated by MockitoExtension's callback, which runs after
        // instance-field initializers - an inline "new ClaimSubmissionService(...)"
        // at field-declaration time would still see every mock as null.
        service = new ClaimSubmissionService(
                policyService, claimRepository, attachmentRepository, attachmentValidator, storage, FIXED_CLOCK);
    }

    @Test
    void submit_persistenceFailsAfterAFileWasStored_bestEffortDeletesItAndRethrows() {
        UUID customerId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        PolicyView policy = policy(policyId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        Candidate candidate = new Candidate(new byte[] {1, 2, 3}, "photo.jpg");
        AttachmentValidator.ValidatedAttachment validated =
                new AttachmentValidator.ValidatedAttachment(candidate.content(), ImageType.JPEG, "photo.jpg");
        StoredFile stored = new StoredFile("generated-key", "image/jpeg", 3, "deadbeef", "photo.jpg");
        RuntimeException dbFailure = new RuntimeException("simulated unique-constraint violation");

        when(policyService.getById(policyId, customerId)).thenReturn(policy);
        when(attachmentValidator.validate(List.of(candidate))).thenReturn(List.of(validated));
        when(storage.store(validated.content(), "image/jpeg", "photo.jpg")).thenReturn(stored);
        when(claimRepository.saveAndFlush(any())).thenThrow(dbFailure);

        SubmitClaimCommand command =
                new SubmitClaimCommand(policyId, LocalDate.of(2026, 6, 10), "A minor collision", "Sofia", List.of(candidate));

        assertThatThrownBy(() -> service.submit(customerId, command)).isSameAs(dbFailure);

        // Correctness doesn't depend on this succeeding, but it must be
        // attempted - the byte just written is otherwise orphaned.
        verify(storage).delete("generated-key");
        verify(attachmentRepository, never()).saveAll(any());
    }

    @Test
    void submit_noStoredFilesYetWhenPersistenceFails_deletesNothing() {
        UUID customerId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        PolicyView policy = policy(policyId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        RuntimeException dbFailure = new RuntimeException("simulated failure");

        when(policyService.getById(policyId, customerId)).thenReturn(policy);
        when(attachmentValidator.validate(List.of())).thenReturn(List.of());
        when(claimRepository.saveAndFlush(any())).thenThrow(dbFailure);

        SubmitClaimCommand command =
                new SubmitClaimCommand(policyId, LocalDate.of(2026, 6, 10), "A minor collision", "Sofia", List.of());

        assertThatThrownBy(() -> service.submit(customerId, command)).isSameAs(dbFailure);

        verify(storage, never()).delete(anyString());
    }

    private static PolicyView policy(UUID id, LocalDate coverageStart, LocalDate coverageEnd) {
        return new PolicyView(
                id,
                "MI-2026-00000001",
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"),
                coverageStart,
                coverageEnd,
                "Ivan Petrov",
                "CA1234BM",
                null,
                30,
                "CA",
                1500,
                (short) 1,
                "Zone 1",
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                "NEUTRAL",
                new BigDecimal("1.000"),
                new BigDecimal("110.00"),
                2,
                new BigDecimal("5.00"),
                new BigDecimal("115.00"),
                new BigDecimal("57.50"),
                "EUR",
                PolicyStatus.ACTIVE);
    }
}
