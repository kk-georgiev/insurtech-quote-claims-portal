import { useEffect, useState } from 'react';
import { getSystemInfo, type SystemInfo } from './api/systemApi';

type ConnectionState = 'checking' | 'connected' | 'offline';

const stages = [
  { name: 'Foundation', description: 'Structure, tooling and shared decisions', state: 'current' },
  { name: 'Quote', description: 'Driver + vehicle → transparent premium', state: 'next' },
  { name: 'Policy', description: 'Accepted quote → immutable policy snapshot', state: 'planned' },
  { name: 'Claim', description: 'FNOL → adjuster workflow and notifications', state: 'planned' },
];

const openDecisions = [
  'JWT or server-side sessions for authentication',
  'Exact permissions of agent and administrator roles',
  'Who submits FNOL in the third-party liability scenario',
  'Local or object storage for claim attachments',
];

function App() {
  const [connection, setConnection] = useState<ConnectionState>('checking');
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);

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

  return (
    <main>
      <header className="hero">
        <div className="nav">
          <span className="brand-mark">MI</span>
          <span className="brand-name">Motor Insurance Portal</span>
          <span className={`connection connection--${connection}`}>
            {connection === 'checking' && 'Checking backend'}
            {connection === 'connected' && 'Backend connected'}
            {connection === 'offline' && 'Backend offline'}
          </span>
        </div>

        <div className="hero__content">
          <p className="eyebrow">MENTOR CHECKPOINT · FOUNDATION</p>
          <h1>One clear foundation for the full insurance journey.</h1>
          <p className="lead">
            A modular starting point for quote, policy and claims workflows—small
            enough to discuss, structured enough to build on.
          </p>
          <div className="stack-list" aria-label="Technology stack">
            {(systemInfo?.stack ?? ['Java 21', 'Spring Boot', 'React', 'PostgreSQL']).map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </div>
      </header>

      <section className="content-grid">
        <article className="panel panel--wide">
          <div className="panel__heading">
            <div>
              <p className="eyebrow">DELIVERY PATH</p>
              <h2>Build one vertical slice at a time</h2>
            </div>
            <span className="status-pill">Foundation ready</span>
          </div>
          <div className="stages">
            {stages.map((stage, index) => (
              <div className={`stage stage--${stage.state}`} key={stage.name}>
                <span className="stage__number">0{index + 1}</span>
                <div>
                  <h3>{stage.name}</h3>
                  <p>{stage.description}</p>
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <p className="eyebrow">OPEN DECISIONS</p>
          <h2>Questions before code</h2>
          <ul className="decision-list">
            {openDecisions.map((decision) => (
              <li key={decision}>{decision}</li>
            ))}
          </ul>
        </article>

        <article className="panel panel--accent">
          <p className="eyebrow">CURRENT SCOPE</p>
          <h2>Intentionally not an MVP yet</h2>
          <p>
            No authentication, pricing rules or claim state machine have been
            implemented before mentor validation. The foundation proves the toolchain
            and leaves the business choices visible and reversible.
          </p>
        </article>
      </section>
    </main>
  );
}

export default App;
