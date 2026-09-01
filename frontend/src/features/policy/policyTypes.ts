/**
 * Mirrors the backend's `policy.application.PolicyView` field for field
 * (READ-ONLY — Story 8.1 owns the shape). This is what `POST
 * /api/v1/quotes/{id}/accept` returns, and what Story 8.3's policy list and
 * detail screens will read.
 *
 * Lives under `features/policy/` rather than beside the quote screens
 * because the policy screens are its next consumer; Story 8.2 only needs
 * the type to render its success block.
 *
 * Money arrives as a number and renders exactly as the API sent it — never
 * re-derived, re-rounded, or re-formatted client-side. `vehicleRegistration`
 * and `vehicleVin` are mutually exclusive: exactly one is non-null, decided
 * and enforced by the backend.
 *
 * No `status` field: deriving a policy's status from its coverage dates is
 * Story 8.3's job (FR-M3-09), together with the screens that display it.
 */
export interface PolicyResponse {
  id: string;
  policyNumber: string;
  quoteId: string;
  issuedAt: string;
  coverageStart: string;
  coverageEnd: string;
  holderName: string;
  vehicleRegistration: string | null;
  vehicleVin: string | null;
  driverAge: number;
  regionCode: string;
  engineCc: number;
  zoneId: number;
  zoneName: string;
  basePremium: number;
  ageSurcharge: number;
  bonusMalusClass: string;
  bonusMalusFactor: number;
  oneTimePremium: number;
  installments: number;
  installmentFee: number;
  totalPremium: number;
  installmentAmount: number;
  currency: string;
}
