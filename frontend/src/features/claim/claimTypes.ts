/**
 * Mirrors the backend's `claim.domain.ClaimStatus` (Story 10.2). The
 * vocabulary is fixed at five states (epic-10-context.md, UX & Interaction
 * Patterns) - never chosen or computed by this frontend, always read off a
 * `ClaimResponse`. This story renders no status badge at all (a fresh claim
 * is always `SUBMITTED` - `claimStatusPresentation` is Story 10.4's own
 * module), but the union is typed honestly here rather than narrowed to the
 * one value this screen happens to see.
 */
export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAID';

/**
 * Mirrors the backend's `claim.application.AttachmentView` (READ-ONLY,
 * claim/application). `sizeBytes` and `contentType` are the sniffed values
 * the backend stored, not anything this screen re-derives.
 */
export interface AttachmentResponse {
  id: string;
  displayFilename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

/**
 * Mirrors the backend's `claim.application.ClaimView.StatusHistoryEntry`
 * (Story 10.4, READ-ONLY, claim/application). This story's backend ever
 * produces exactly one entry - the claim's current status paired with its
 * `submittedAt` - never a richer, inferred timeline (see `ClaimView`'s own
 * Javadoc); this frontend type carries the shape honestly rather than
 * assuming a single-entry array.
 */
export interface StatusHistoryEntryResponse {
  status: ClaimStatus;
  occurredAt: string;
}

/**
 * Mirrors the backend's `claim.application.ClaimView` (READ-ONLY, claim/
 * application) - what `POST /api/v1/claims` returns on success (Story
 * 10.2), and what Story 10.4's `GET /api/v1/claims` (list) and
 * `GET /api/v1/claims/{id}` (detail) both return - the same shape, per
 * M4-AD-12. Lives under `features/claim/` as this story's own first
 * artifact in that module.
 */
export interface ClaimResponse {
  id: string;
  claimNumber: string;
  policyId: string;
  policyNumber: string;
  incidentDate: string;
  description: string;
  location: string;
  status: ClaimStatus;
  submittedAt: string;
  attachments: AttachmentResponse[];
  statusHistory: StatusHistoryEntryResponse[];
}
