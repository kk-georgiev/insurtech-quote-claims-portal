import { useEffect, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import { useFormSubmission } from '../../hooks/useFormSubmission';
import type { Translate } from '../../i18n/errorMessages';
import type { PolicyResponse } from '../policy/policyTypes';
import type { ClaimResponse } from './claimTypes';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'not-found' | 'error' | 'ready';

/**
 * The complete allowlist a client's browser can screen against before a
 * request is even made, mirroring `shared.storage.ImageType`'s MIME
 * strings (backend/src/main/java/com/motorinsurance/shared/storage/
 * ImageType.java) - **not** a substitute for it. The backend sniffs actual
 * file content; this only reads the browser-reported `File.type`, which a
 * renamed file can lie about. UX convenience only (spec Boundaries).
 */
const ACCEPTED_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ACCEPT_ATTRIBUTE = 'image/jpeg,image/png,image/webp';

/**
 * Mirrors `storage.attachment.max-file-size-bytes` (application.yml: 5 MiB)
 * - the backend's own configured value, re-validated independently server
 * side. A literal here, same as `AcceptQuoteForm`'s `MAX_COVERAGE_START_
 * DAYS_AHEAD`: a project-scoped UX nicety, not the authority.
 */
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
/** Mirrors `storage.attachment.max-count`. */
const MAX_FILE_COUNT = 10;

/** The fields this form renders an inline error next to. */
const KNOWN_FIELDS = new Set(['incidentDate', 'description', 'location', 'attachments']);

interface RejectedFile {
  name: string;
  reason: string;
}

interface FileScreeningResult {
  accepted: File[];
  rejected: RejectedFile[];
}

function isoDate(date: Date): string {
  // Local calendar date, not `toISOString()`, which would shift the day for
  // anyone east of UTC late in the evening - same helper as
  // `AcceptQuoteForm.tsx`'s own `isoDate`, duplicated rather than extracted
  // (spec: "computed client-side same as AcceptQuoteForm").
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function formatFileSize(bytes: number): string {
  const kb = bytes / 1024;
  return kb < 1024 ? `${Math.max(1, Math.round(kb))} KB` : `${(kb / 1024).toFixed(1)} MB`;
}

/**
 * Client-side file screening at selection time (spec Boundaries): type via
 * `accept`, size <= 5 MiB, count <= 10 - mirroring `storage.attachment.*`.
 * A rejected file is excluded from submission and listed with its own name
 * and reason, reusing the same `errors.codes.ATTACHMENT_*` copy the backend
 * rejection renders, rather than duplicating the sentence. **UX convenience
 * only** - the backend remains the sole authority and re-validates type
 * (content-sniffed), size and count independently.
 */
function screenFiles(files: FileList, t: Translate): FileScreeningResult {
  const accepted: File[] = [];
  const rejected: RejectedFile[] = [];

  for (const file of Array.from(files)) {
    if (!ACCEPTED_MIME_TYPES.has(file.type)) {
      rejected.push({ name: file.name, reason: t('errors.codes.ATTACHMENT_UNSUPPORTED_TYPE') });
      continue;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      rejected.push({ name: file.name, reason: t('errors.codes.ATTACHMENT_TOO_LARGE') });
      continue;
    }
    if (accepted.length >= MAX_FILE_COUNT) {
      rejected.push({ name: file.name, reason: t('errors.codes.ATTACHMENT_TOO_MANY') });
      continue;
    }
    accepted.push(file);
  }

  return { accepted, rejected };
}

/**
 * The FNOL ("First Notice of Loss") screen (Story 10.3, epic-10-context.md)
 * - a client's first screen to file a claim against one of their own
 * policies. Reached from `PolicyDetail`'s "File a claim" link, at its own
 * route (`/policies/:policyId/claims/new`) rather than embedded in
 * `PolicyDetail`/`QuoteDetail`'s pattern, because the AC names "which
 * policy" as the form's own first section and a routed screen survives a
 * refresh/bookmark where nav-state would not.
 *
 * Does its own owner-scoped `GET /api/v1/policies/{policyId}` load,
 * mirroring `PolicyDetail`'s loading/not-found/error/ready `Phase` pattern
 * exactly - which also gives owner-scoping 404 for free, identical to a
 * policy that does not exist (AD-10). Even if a stale/forged `policyId`
 * reached submit anyway, `ClaimSubmissionService.submit` performs the same
 * owner-scoped check server side and throws the same 404 (spec Boundaries).
 *
 * Reading order (epic-10-context.md, UX & Interaction Patterns): which
 * policy -> what happened -> when -> where -> photos -> submit, in one
 * screen section, never a modal. `AcceptQuoteForm` is the pattern mirrored
 * for the form mechanics: `useFormSubmission`, a native date input with
 * client min/max, `Card`+`FormField`+`Input`+`Alert`+`Button`/`Spinner`
 * composition - here with an inline success confirmation instead of a
 * navigate, since `/claims/:id` does not exist until Story 10.4.
 */
export function FnolForm() {
  const { policyId } = useParams<{ policyId: string }>();
  const { t } = useTranslation();
  const [phase, setPhase] = useState<Phase>('loading');
  const [policy, setPolicy] = useState<PolicyResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const cancelledRef = useCancelledRef();

  useEffect(() => {
    if (!policyId) return;
    setPhase('loading');
    apiFetch<PolicyResponse>(`/api/v1/policies/${policyId}`, { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setPolicy(response);
        setPhase('ready');
      })
      .catch((error: unknown) => {
        if (cancelledRef.current) return;
        if (error instanceof ApiRequestError && error.status === 404) {
          setPhase('not-found');
        } else {
          setPhase('error');
        }
      });
  }, [policyId, reloadToken]);

  const [incidentDate, setIncidentDate] = useState('');
  const [description, setDescription] = useState('');
  const [location, setLocation] = useState('');
  const [acceptedFiles, setAcceptedFiles] = useState<File[]>([]);
  const [rejectedFiles, setRejectedFiles] = useState<RejectedFile[]>([]);
  const [submittedClaim, setSubmittedClaim] = useState<ClaimResponse | null>(null);

  const { submitting, formError, fieldErrors, submit } = useFormSubmission('claim.form', t, {
    knownFields: KNOWN_FIELDS,
  });

  function handleFilesChange(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files;
    if (!files || files.length === 0) {
      setAcceptedFiles([]);
      setRejectedFiles([]);
      return;
    }
    const { accepted, rejected } = screenFiles(files, t);
    setAcceptedFiles(accepted);
    setRejectedFiles(rejected);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!policy) return;

    // The verified contract (ClaimController.java:57-68, SubmitClaimForm.
    // java:29-33): scalar FormData fields by exact name, `attachments` as a
    // sibling repeated field - not nested under a JSON-like key.
    const formData = new FormData();
    formData.append('policyId', policy.id);
    formData.append('incidentDate', incidentDate);
    formData.append('description', description);
    formData.append('location', location);
    for (const file of acceptedFiles) {
      formData.append('attachments', file);
    }

    await submit(async (isCancelled) => {
      const claim = await apiFetch<ClaimResponse>('/api/v1/claims', {
        method: 'POST',
        authenticated: true,
        body: formData,
      });
      if (isCancelled()) return;
      setSubmittedClaim(claim);
    });
  }

  if (phase === 'loading') {
    return (
      <div data-testid="fnol-loading" className="flex items-center gap-2 text-text-muted">
        <Spinner />
        <span>{t('policies.list.loading')}</span>
      </div>
    );
  }

  if (phase === 'not-found') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="fnol-not-found">
          {t('policies.detail.notFound')}
        </Alert>
        <Link to="/policies" className="inline-block text-sm text-accent underline">
          {t('policies.detail.backToList')}
        </Link>
      </div>
    );
  }

  if (phase === 'error') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="fnol-error">
          {t('policies.list.error')}
        </Alert>
        <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
          {t('policies.list.retry')}
        </Button>
      </div>
    );
  }

  // phase === 'ready' - policy is always set by this point; the check only
  // narrows the type for the render below.
  if (!policy) return null;

  if (submittedClaim) {
    // No navigate to /claims/:id (spec Never: doesn't exist until Story
    // 10.4) - an inline confirmation with the claim number and a link back
    // to the policy instead.
    return (
      <Card title={t('claims.form.success.heading')} titleAs="h2" data-testid="fnol-success">
        <p className="text-text" data-testid="fnol-success-claim-number">
          {t('claims.form.success.claimNumber', { claimNumber: submittedClaim.claimNumber })}
        </p>
        <Link
          to={`/policies/${policy.id}`}
          className="mt-4 inline-block text-sm text-accent underline"
        >
          {t('claims.form.success.backToPolicy')}
        </Link>
      </Card>
    );
  }

  const today = isoDate(new Date());
  // min=policy.coverageStart, max=min(today, policy.coverageEnd) - keeps the
  // native picker inside the policy's own coverage window *and* disallows a
  // future date in one control (spec Boundaries). ISO `yyyy-MM-dd` strings
  // compare lexicographically the same as their dates.
  const maxIncidentDate = policy.coverageEnd < today ? policy.coverageEnd : today;

  return (
    <div className="space-y-4" data-testid="fnol-form-screen">
      <Card title={t('claims.form.heading')} titleAs="h2" data-testid="fnol-form">
        <p className="mb-4 text-sm text-text-muted" data-testid="fnol-policy-label">
          {t('claims.form.policyLabel', { policyNumber: policy.policyNumber })}
        </p>
        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <FormField
            label={t('claims.form.description')}
            error={fieldErrors.description}
            errorId="fnol-description-error"
          >
            <Input
              id="fnol-description"
              name="description"
              type="text"
              required
              minLength={10}
              maxLength={2000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={submitting}
              invalid={Boolean(fieldErrors.description)}
            />
          </FormField>

          <FormField
            label={t('claims.form.incidentDate')}
            error={fieldErrors.incidentDate}
            errorId="fnol-incidentDate-error"
          >
            <Input
              id="fnol-incidentDate"
              name="incidentDate"
              // Native date input, no custom picker (epic-10-context.md). The
              // backend independently re-checks both bounds -
              // CLAIM_INCIDENT_DATE_IN_FUTURE and
              // CLAIM_INCIDENT_OUTSIDE_COVERAGE - this is a UX nicety, not a
              // substitute for either.
              type="date"
              required
              min={policy.coverageStart}
              max={maxIncidentDate}
              value={incidentDate}
              onChange={(event) => setIncidentDate(event.target.value)}
              disabled={submitting}
              invalid={Boolean(fieldErrors.incidentDate)}
            />
          </FormField>

          <FormField
            label={t('claims.form.location')}
            error={fieldErrors.location}
            errorId="fnol-location-error"
          >
            <Input
              id="fnol-location"
              name="location"
              type="text"
              required
              minLength={2}
              maxLength={200}
              value={location}
              onChange={(event) => setLocation(event.target.value)}
              disabled={submitting}
              invalid={Boolean(fieldErrors.location)}
            />
          </FormField>

          <FormField
            label={t('claims.form.attachments')}
            error={fieldErrors.attachments}
            errorId="fnol-attachments-error"
          >
            {/* Bare native multi-file input (spec Never: no drag-and-drop
                library, no upload-progress widget). Re-selecting replaces
                the whole choice, same as any native file input. */}
            <Input
              id="fnol-attachments"
              name="attachments"
              type="file"
              multiple
              accept={ACCEPT_ATTRIBUTE}
              onChange={handleFilesChange}
              disabled={submitting}
              invalid={Boolean(fieldErrors.attachments)}
            />
          </FormField>
          <p className="text-xs text-text-muted">{t('claims.form.attachmentsHint')}</p>

          {acceptedFiles.length > 0 && (
            <ul className="space-y-1 text-sm text-text" data-testid="fnol-accepted-files">
              {acceptedFiles.map((file, index) => (
                <li key={`${file.name}-${index}`}>
                  {file.name} ({formatFileSize(file.size)})
                </li>
              ))}
            </ul>
          )}

          {rejectedFiles.length > 0 && (
            <ul className="space-y-1 text-sm text-danger" data-testid="fnol-rejected-files">
              {rejectedFiles.map((file, index) => (
                <li key={`${file.name}-${index}`}>
                  {file.name}: {file.reason}
                </li>
              ))}
            </ul>
          )}

          {formError && (
            <Alert variant="danger" data-testid="fnol-form-error">
              {formError}
            </Alert>
          )}

          {/* One primary button, labelled with its outcome; the spinner and
              disabled state carry in-flight progress alongside it. */}
          <Button type="submit" disabled={submitting}>
            {submitting ? (
              <>
                <Spinner className="mr-2" />
                {t('claims.form.submitting')}
              </>
            ) : (
              t('claims.form.submit')
            )}
          </Button>
        </form>
      </Card>
    </div>
  );
}
