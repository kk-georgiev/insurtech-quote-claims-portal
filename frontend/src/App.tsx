import { useEffect, useState, type FormEvent } from 'react';
import {
  createQuote,
  type BonusMalusLevel,
  type Quote,
  type RegionRisk,
} from './api/quoteApi';
import { getSystemInfo, type SystemInfo } from './api/systemApi';

type ConnectionState = 'checking' | 'connected' | 'offline';
type SubmitState = 'idle' | 'submitting' | 'success' | 'error';

type QuoteForm = {
  driverAge: string;
  drivingExperienceYears: string;
  region: RegionRisk;
  vehiclePowerKw: string;
  bonusMalusLevel: BonusMalusLevel;
};

const initialForm: QuoteForm = {
  driverAge: '35',
  drivingExperienceYears: '12',
  region: 'SOFIA',
  vehiclePowerKw: '100',
  bonusMalusLevel: 'NEUTRAL',
};

const factorLabels: Array<[keyof Quote['breakdown'], string]> = [
  ['ageFactor', 'Driver age'],
  ['experienceFactor', 'Driving experience'],
  ['regionFactor', 'Region'],
  ['powerFactor', 'Vehicle power'],
  ['bonusMalusFactor', 'Bonus–malus'],
];

function App() {
  const [connection, setConnection] = useState<ConnectionState>('checking');
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [form, setForm] = useState<QuoteForm>(initialForm);
  const [submitState, setSubmitState] = useState<SubmitState>('idle');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    getSystemInfo(controller.signal)
      .then((data) => {
        setSystemInfo(data);
        setConnection('connected');
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setConnection('offline');
      });

    return () => controller.abort();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitState('submitting');
    setError(null);

    try {
      const result = await createQuote({
        driverAge: Number(form.driverAge),
        drivingExperienceYears: Number(form.drivingExperienceYears),
        region: form.region,
        vehiclePowerKw: Number(form.vehiclePowerKw),
        bonusMalusLevel: form.bonusMalusLevel,
      });
      setQuote(result);
      setSubmitState('success');
    } catch (requestError: unknown) {
      setQuote(null);
      setError(requestError instanceof Error ? requestError.message : 'Unable to calculate quote');
      setSubmitState('error');
    }
  }

  const formatMoney = (amount: number, currency: string) =>
    new Intl.NumberFormat('en-IE', { style: 'currency', currency }).format(amount);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar__inner">
          <a className="brand" href="#top" aria-label="Motor Insurance Portal home">
            <span className="brand__mark">MI</span>
            <span>Motor Insurance</span>
          </a>
          <div className="delivery-steps" aria-label="Delivery progress">
            <span className="delivery-step delivery-step--done">Foundation</span>
            <span className="delivery-step delivery-step--current">Quote</span>
            <span className="delivery-step">Policy</span>
            <span className="delivery-step">Claim</span>
          </div>
          <span className={`connection connection--${connection}`}>
            {connection === 'checking' && 'Checking API'}
            {connection === 'connected' && 'Backend connected'}
            {connection === 'offline' && 'Backend offline'}
          </span>
        </div>
      </header>

      <main id="top" className="page">
        <section className="intro">
          <div>
            <p className="eyebrow">QUOTE ENGINE · FIRST VERTICAL SLICE</p>
            <h1>See how your motor premium is calculated.</h1>
            <p className="lead">
              Enter a few driver and vehicle details. We return a saved quote with a
              transparent factor-by-factor breakdown.
            </p>
          </div>
          <div className="intro__proof">
            <strong>{systemInfo?.project ?? 'Motor Insurance Portal'}</strong>
            <span>Java 21 · React · PostgreSQL</span>
          </div>
        </section>

        <section className="quote-grid">
          <form className="quote-form" onSubmit={handleSubmit}>
            <div className="section-heading">
              <div>
                <p className="eyebrow">YOUR DETAILS</p>
                <h2>Calculate a quote</h2>
              </div>
              <span className="step-badge">1 of 1</span>
            </div>

            <div className="form-grid">
              <label>
                <span>Driver age</span>
                <input
                  type="number"
                  min="18"
                  max="100"
                  required
                  value={form.driverAge}
                  onChange={(event) => setForm({ ...form, driverAge: event.target.value })}
                />
                <small>Between 18 and 100 years</small>
              </label>

              <label>
                <span>Driving experience</span>
                <input
                  type="number"
                  min="0"
                  max="82"
                  required
                  value={form.drivingExperienceYears}
                  onChange={(event) => setForm({ ...form, drivingExperienceYears: event.target.value })}
                />
                <small>Completed years with a licence</small>
              </label>

              <label>
                <span>Registration region</span>
                <select
                  value={form.region}
                  onChange={(event) => setForm({ ...form, region: event.target.value as RegionRisk })}
                >
                  <option value="SOFIA">Sofia</option>
                  <option value="LARGE_CITY">Other large city</option>
                  <option value="OTHER">Other region</option>
                </select>
                <small>Used as a demonstration risk factor</small>
              </label>

              <label>
                <span>Vehicle power</span>
                <div className="input-with-unit">
                  <input
                    type="number"
                    min="20"
                    max="500"
                    required
                    value={form.vehiclePowerKw}
                    onChange={(event) => setForm({ ...form, vehiclePowerKw: event.target.value })}
                  />
                  <span>kW</span>
                </div>
                <small>Between 20 and 500 kW</small>
              </label>

              <label className="form-grid__wide">
                <span>Bonus–malus level</span>
                <select
                  value={form.bonusMalusLevel}
                  onChange={(event) =>
                    setForm({ ...form, bonusMalusLevel: event.target.value as BonusMalusLevel })
                  }
                >
                  <option value="BONUS_20">Bonus 20% — excellent history</option>
                  <option value="BONUS_10">Bonus 10% — good history</option>
                  <option value="NEUTRAL">Neutral — standard rate</option>
                  <option value="MALUS_25">Malus 25% — elevated risk</option>
                  <option value="MALUS_50">Malus 50% — high risk</option>
                </select>
              </label>
            </div>

            {error && <div className="error-message" role="alert">{error}</div>}

            <button className="primary-button" type="submit" disabled={submitState === 'submitting'}>
              {submitState === 'submitting' ? 'Calculating…' : 'Calculate premium'}
            </button>
            <p className="form-note">
              This uses tariff <strong>2026.1-demo</strong>. Coefficients are explicit
              placeholders awaiting mentor approval.
            </p>
          </form>

          <aside className={`result-card ${quote ? 'result-card--ready' : ''}`} aria-live="polite">
            {!quote ? (
              <div className="result-placeholder">
                <span className="result-placeholder__icon">%</span>
                <p className="eyebrow">PREMIUM BREAKDOWN</p>
                <h2>Your result will appear here</h2>
                <p>Each factor stays visible, so the quote is explainable and testable.</p>
                <ul>
                  <li>Age and experience factors</li>
                  <li>Region and vehicle power</li>
                  <li>Bonus–malus adjustment</li>
                  <li>Saved quote reference</li>
                </ul>
              </div>
            ) : (
              <div className="quote-result">
                <div className="quote-result__header">
                  <div>
                    <p className="eyebrow">YOUR ESTIMATED PREMIUM</p>
                    <div className="premium">{formatMoney(quote.premium, quote.currency)}</div>
                    <span className="premium-caption">for the demonstration coverage period</span>
                  </div>
                  <span className="status-badge">{quote.status}</span>
                </div>

                <div className="base-row">
                  <span>Base premium</span>
                  <strong>{formatMoney(quote.breakdown.basePremium, quote.currency)}</strong>
                </div>

                <div className="factor-list">
                  {factorLabels.map(([key, label]) => (
                    <div className="factor-row" key={key}>
                      <span>{label}</span>
                      <strong className={quote.breakdown[key] < 1 ? 'factor--bonus' : ''}>
                        × {Number(quote.breakdown[key]).toFixed(3)}
                      </strong>
                    </div>
                  ))}
                </div>

                <div className="quote-meta">
                  <div>
                    <span>Quote reference</span>
                    <code>{quote.id}</code>
                  </div>
                  <div>
                    <span>Valid until</span>
                    <strong>{new Date(quote.validUntil).toLocaleDateString('en-GB')}</strong>
                  </div>
                </div>
              </div>
            )}
          </aside>
        </section>

        <section className="next-step">
          <div>
            <p className="eyebrow">WHAT THIS PROVES</p>
            <h2>One complete business slice, ready to extend.</h2>
          </div>
          <p>
            The API validates inputs, calculates a versioned price, saves an immutable
            quote snapshot and returns the same breakdown to React. Accepting a quote
            and issuing a policy are intentionally the next slice.
          </p>
        </section>
      </main>
    </div>
  );
}

export default App;
